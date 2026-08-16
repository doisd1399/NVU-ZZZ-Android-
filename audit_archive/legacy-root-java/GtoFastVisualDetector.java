package com.nvu.operacional;

import android.graphics.Rect;
import android.media.Image;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Lightweight, OCR-free visual detector for the fixed GTO freight list.
 *
 * It reads only a thin strip that contains the orange "Aceitar" buttons and a
 * coarse signature of the jobs panel. The class never consumes or injects touch
 * events. Its purpose is to preserve the very short pressed-button frame that can
 * be lost by ImageReader.acquireLatestImage().
 */
final class GtoFastVisualDetector {
    static final class Frame {
        final long capturedAtMs;
        final long imageTimestampNs;
        final List<Rect> buttons;
        final List<int[]> buttonSignatures;
        final float[] orangeRatios;
        final int[] panelSignature;
        final int screenHeight;

        Frame(long capturedAtMs,
              long imageTimestampNs,
              List<Rect> buttons,
              List<int[]> buttonSignatures,
              float[] orangeRatios,
              int[] panelSignature,
              int screenHeight) {
            this.capturedAtMs = capturedAtMs;
            this.imageTimestampNs = imageTimestampNs;
            this.buttons = buttons;
            this.buttonSignatures = buttonSignatures;
            this.orangeRatios = orangeRatios;
            this.panelSignature = panelSignature;
            this.screenHeight = screenHeight;
        }

        boolean hasFreightList() {
            // R3.7: detectButtons() already returns only a plausible stack. Keep the
            // frame-level validation on the exact same scale envelope so a valid list
            // is not rejected only because a device renders the GTO UI slightly smaller
            // or larger (common across 16:9, 18:9, 20:9 and older Android devices).
            if (buttons == null || buttons.isEmpty() || buttons.size() > 6 || screenHeight <= 0) return false;
            // GTO 0.1.x changed the scale of the freight cards on some devices. The
            // previous 3.8% lower bound made a perfectly valid 3-4% Aceitar button
            // disappear before the selection state machine ever saw the page.
            int minHeight = Math.max(10, Math.round(screenHeight * 0.014f));
            int maxHeight = Math.max(minHeight + 1, Math.round(screenHeight * 0.160f));
            for (Rect rect : buttons) {
                if (rect.height() < minHeight || rect.height() > maxHeight) return false;
            }
            if (buttons.size() == 1) {
                return buttons.get(0).centerY() <= Math.round(screenHeight * 0.32f);
            }
            int minGap = Math.round(screenHeight * 0.060f);
            int maxGap = Math.round(screenHeight * 0.320f);
            for (int i = 1; i < buttons.size(); i++) {
                int gap = buttons.get(i).centerY() - buttons.get(i - 1).centerY();
                if (gap < minGap || gap > maxGap) return false;
            }
            return true;
        }
    }

    static final class PressCandidate {
        final int row;
        final float score;
        final float margin;

        PressCandidate(int row, float score, float margin) {
            this.row = row;
            this.score = score;
            this.margin = margin;
        }
    }

    Frame analyze(Image image, int width, int height, long nowMs) {
        if (image == null || width <= 0 || height <= 0) return empty(nowMs, image);
        Image.Plane[] planes = image.getPlanes();
        if (planes == null || planes.length == 0) return empty(nowMs, image);
        Image.Plane plane = planes[0];
        ByteBuffer buffer = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        if (buffer == null || pixelStride < 3 || rowStride <= 0) return empty(nowMs, image);

        List<Rect> buttons = detectButtons(buffer, pixelStride, rowStride, width, height);
        List<int[]> signatures = new ArrayList<>(buttons.size());
        float[] orange = new float[buttons.size()];
        for (int i = 0; i < buttons.size(); i++) {
            Rect rect = buttons.get(i);
            signatures.add(signature(buffer, pixelStride, rowStride, width, height, rect));
            orange[i] = orangeRatio(buffer, pixelStride, rowStride, width, height, rect);
        }
        int[] panel = panelSignature(buffer, pixelStride, rowStride, width, height, buttons);
        return new Frame(nowMs, image.getTimestamp(), buttons, signatures, orange, panel, height);
    }

    PressCandidate detectPressedRow(Frame previous, Frame current, int screenHeight) {
        if (previous == null || current == null || !previous.hasFreightList() || !current.hasFreightList()) return null;
        if (previous.buttons.size() != current.buttons.size()) return null;
        int count = current.buttons.size();
        int yTolerance = Math.max(10, Math.round(screenHeight * 0.022f));
        for (int i = 0; i < count; i++) {
            if (Math.abs(previous.buttons.get(i).centerY() - current.buttons.get(i).centerY()) > yTolerance) return null;
        }

        float best = -1f;
        float second = -1f;
        int bestRow = -1;
        float[] scores = new float[count];
        for (int i = 0; i < count; i++) {
            float sig = signatureDistance(previous.buttonSignatures.get(i), current.buttonSignatures.get(i));
            float color = Math.abs(previous.orangeRatios[i] - current.orangeRatios[i]);
            // The pressed state changes both shading and the orange fill. Signature is
            // weighted more heavily because it also catches ripple/highlight effects.
            float score = sig * 0.78f + color * 0.22f;
            scores[i] = score;
            if (score > best) {
                second = best;
                best = score;
                bestRow = i;
            } else if (score > second) {
                second = score;
            }
        }
        if (bestRow < 0) return null;
        if (second < 0f) second = 0f;

        float othersMean = 0f;
        int others = 0;
        for (int i = 0; i < count; i++) {
            if (i == bestRow) continue;
            othersMean += scores[i];
            others++;
        }
        if (others > 0) othersMean /= others;
        float margin = best - Math.max(second, othersMean);

        // Deliberately conservative. A page transition changes several rows at once;
        // an actual tap changes one button first. One strong frame is enough because
        // the candidate still has to be followed by the freight list disappearing.
        boolean absolute = best >= 0.027f;
        boolean isolated = margin >= 0.011f && best >= Math.max(0.027f, othersMean * 2.10f + 0.008f);
        if (!absolute || !isolated) return null;
        return new PressCandidate(bestRow, best, margin);
    }



    /**
     * Relaxed press detector used only inside a short ACTION_OUTSIDE pulse window.
     * The timestamp pulse proves that the user just touched the GTO while the freight
     * list was visible, so we can lower visual thresholds and let the subsequent list
     * disappearance be the second confirmation.
     */
    /**
     * Last-resort detector for the edge case where the user taps before a clean
     * pre-touch baseline was processed. It only accepts one button whose orange fill
     * is a clear outlier versus the other buttons in the same frame.
     */
    PressCandidate detectPressedRowFromSingleFrame(Frame current) {
        if (current == null || !current.hasFreightList() || current.orangeRatios == null
            || current.orangeRatios.length < 2) return null;
        List<Float> sorted = new ArrayList<>();
        for (float value : current.orangeRatios) sorted.add(value);
        Collections.sort(sorted);
        float median = sorted.get(sorted.size() / 2);
        int bestRow = -1;
        float bestDrop = 0f;
        float secondDrop = 0f;
        for (int i = 0; i < current.orangeRatios.length; i++) {
            float drop = Math.max(0f, median - current.orangeRatios[i]);
            if (drop > bestDrop) {
                secondDrop = bestDrop;
                bestDrop = drop;
                bestRow = i;
            } else if (drop > secondDrop) {
                secondDrop = drop;
            }
        }
        float margin = bestDrop - secondDrop;
        if (bestRow < 0 || median < 0.18f) return null;
        if (bestDrop < 0.075f || margin < 0.035f) return null;
        return new PressCandidate(bestRow, bestDrop, margin);
    }

    PressCandidate detectPressedRowAfterTouch(Frame baseline, Frame current, int screenHeight) {
        if (baseline == null || current == null || !baseline.hasFreightList()) return null;
        // A real button press may darken one Aceitar enough that the orange detector
        // temporarily returns N-1 buttons. That frame is intentionally not a valid
        // freight-list geometry because of the vertical gap, but it is exactly the
        // strongest selection signal. Evaluate the missing-row case before requiring
        // current.hasFreightList().
        if (baseline.buttons.size() != current.buttons.size()) {
            return detectTemporarilyMissingPressedRowAfterTouch(baseline, current, screenHeight);
        }
        if (!current.hasFreightList()) return null;
        int count = current.buttons.size();
        int yTolerance = Math.max(12, Math.round(screenHeight * 0.028f));
        for (int i = 0; i < count; i++) {
            if (Math.abs(baseline.buttons.get(i).centerY() - current.buttons.get(i).centerY()) > yTolerance) return null;
        }

        float best = -1f;
        float second = -1f;
        int bestRow = -1;
        float othersMean = 0f;
        float[] scores = new float[count];
        for (int i = 0; i < count; i++) {
            float sig = signatureDistance(baseline.buttonSignatures.get(i), current.buttonSignatures.get(i));
            float colorDrop = Math.max(0f, baseline.orangeRatios[i] - current.orangeRatios[i]);
            float colorAny = Math.abs(baseline.orangeRatios[i] - current.orangeRatios[i]);
            float score = sig * 0.70f + Math.max(colorDrop, colorAny * 0.72f) * 0.30f;
            scores[i] = score;
            if (score > best) {
                second = best;
                best = score;
                bestRow = i;
            } else if (score > second) {
                second = score;
            }
        }
        if (bestRow < 0) return null;
        if (second < 0f) second = 0f;
        int others = 0;
        for (int i = 0; i < count; i++) {
            if (i == bestRow) continue;
            othersMean += scores[i];
            others++;
        }
        if (others > 0) othersMean /= others;
        float margin = best - Math.max(second, othersMean);

        // Touch timestamp + list exit provide two independent confirmations, therefore
        // this stage should be sensitive instead of conservative.
        boolean absolute = best >= 0.009f;
        boolean isolated = margin >= 0.0028f
            && best >= Math.max(0.009f, othersMean * 1.30f + 0.0018f);
        if (!absolute || !isolated) return null;
        return new PressCandidate(bestRow, best, margin);
    }

    private PressCandidate detectTemporarilyMissingPressedRowAfterTouch(Frame baseline, Frame current, int screenHeight) {
        if (baseline == null || current == null || !baseline.hasFreightList()) return null;
        if (current.buttons == null || baseline.buttons.size() < 2) return null;
        if (current.buttons.size() != baseline.buttons.size() - 1) return null;
        // A pressed Aceitar changes the button strip but not the cargo/text panel.
        // Page navigation can also change the button count, so reject it here before
        // interpreting the missing vertical slot as a pressed row.
        if (signatureDistance(baseline.panelSignature, current.panelSignature) > 0.026f) return null;

        int tolerance = Math.max(14, Math.round(screenHeight * 0.032f));
        boolean[] matched = new boolean[baseline.buttons.size()];
        for (Rect now : current.buttons) {
            int best = -1;
            int bestDistance = Integer.MAX_VALUE;
            for (int i = 0; i < baseline.buttons.size(); i++) {
                if (matched[i]) continue;
                int distance = Math.abs(baseline.buttons.get(i).centerY() - now.centerY());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = i;
                }
            }
            if (best < 0 || bestDistance > tolerance) return null;
            matched[best] = true;
        }
        int missing = -1;
        for (int i = 0; i < matched.length; i++) {
            if (!matched[i]) {
                if (missing >= 0) return null;
                missing = i;
            }
        }
        // Once a touch pulse is armed, one button temporarily disappearing while all
        // others remain geometrically aligned is strong evidence by itself.
        return missing < 0 ? null : new PressCandidate(missing, 0.20f, 0.20f);
    }

    PressCandidate detectTemporarilyMissingPressedRow(Frame previous, Frame current, int screenHeight) {
        if (previous == null || current == null || !previous.hasFreightList()) return null;
        if (current.buttons == null || previous.buttons.size() < 2) return null;
        if (current.buttons.size() != previous.buttons.size() - 1) return null;
        // A page arrow changes the cargo/text panel as well. A pressed button becoming
        // temporarily too dark for the orange detector leaves the rest of the page stable.
        if (signatureDistance(previous.panelSignature, current.panelSignature) > 0.040f) return null;

        int tolerance = Math.max(10, Math.round(screenHeight * 0.024f));
        boolean[] matchedPrevious = new boolean[previous.buttons.size()];
        for (Rect now : current.buttons) {
            int best = -1;
            int bestDistance = Integer.MAX_VALUE;
            for (int i = 0; i < previous.buttons.size(); i++) {
                if (matchedPrevious[i]) continue;
                int distance = Math.abs(previous.buttons.get(i).centerY() - now.centerY());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = i;
                }
            }
            if (best < 0 || bestDistance > tolerance) return null;
            matchedPrevious[best] = true;
        }
        int missing = -1;
        for (int i = 0; i < matchedPrevious.length; i++) {
            if (!matchedPrevious[i]) {
                if (missing >= 0) return null;
                missing = i;
            }
        }
        return missing < 0 ? null : new PressCandidate(missing, 0.12f, 0.12f);
    }

    boolean samePage(Frame a, Frame b) {
        if (a == null || b == null || !a.hasFreightList() || !b.hasFreightList()) return false;
        if (a.buttons.size() != b.buttons.size()) return false;
        // Real GTO pages from the validation captures differ by ~0.04-0.08 even when
        // the button count is identical. The old 0.080 threshold could therefore keep
        // the previous page snapshot and OCR the wrong freight after fast navigation.
        // The signature excludes the Aceitar strip, so a button press itself remains
        // essentially unchanged and safely below this stricter boundary.
        return signatureDistance(a.panelSignature, b.panelSignature) < 0.024f;
    }

    float pageDistance(Frame a, Frame b) {
        if (a == null || b == null) return 1f;
        return signatureDistance(a.panelSignature, b.panelSignature);
    }

    private Frame empty(long nowMs, Image image) {
        return new Frame(nowMs, image == null ? 0L : image.getTimestamp(),
            Collections.emptyList(), Collections.emptyList(), new float[0], new int[0], 0);
    }

    private List<Rect> detectButtons(ByteBuffer buffer, int pixelStride, int rowStride, int width, int height) {
        // R3.4: the GTO HUD is not positioned at exactly the same X coordinate on every
        // aspect ratio / OEM rendering path. The old detector sampled only 91.0%-99.4%
        // of the capture width, which worked on the reference devices but could completely
        // miss the Aceitar column when the game scaled its jobs panel a few percent left.
        // Probe several narrow right-side bands and keep the strongest plausible vertical
        // button stack. This stays OCR-free and adds only a small bounded amount of work.
        final float[][] bands = new float[][] {
            {0.910f, 0.998f},
            {0.885f, 0.985f},
            {0.855f, 0.965f},
            {0.825f, 0.945f},
            {0.790f, 0.925f},
            {0.750f, 0.900f},
            {0.710f, 0.875f},
            {0.670f, 0.850f}
        };

        List<Rect> best = Collections.emptyList();
        float bestScore = -1f;
        for (float[] band : bands) {
            int left = clamp(Math.round(width * band[0]), 0, width - 2);
            int right = clamp(Math.round(width * band[1]), left + 1, width);
            List<Rect> candidate = detectButtonsInBand(buffer, pixelStride, rowStride, width, height, left, right);
            if (!isPlausibleButtonStack(candidate, height)) continue;
            float score = buttonBandScore(buffer, pixelStride, rowStride, width, height, candidate);
            // Prefer more complete stacks first, then the band with the strongest orange
            // coverage. A small right-side bias preserves the reference layout when tied.
            score += candidate.size() * 2.0f + band[0] * 0.05f;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        if (!best.isEmpty()) {
            List<Rect> refined = new ArrayList<>();
            for (Rect rect : best) {
                refined.add(refineButtonHorizontalBounds(buffer, pixelStride, rowStride, width, height, rect));
            }
            best = refined;
        }
        return best;
    }

    private Rect refineButtonHorizontalBounds(
        ByteBuffer buffer,
        int pixelStride,
        int rowStride,
        int width,
        int height,
        Rect coarse
    ) {
        if (coarse == null || coarse.width() <= 2 || coarse.height() <= 2) return coarse == null ? new Rect() : new Rect(coarse);
        int stepX = Math.max(1, width / 1600);
        int stepY = Math.max(1, height / 900);
        int first = -1;
        int last = -1;
        int gap = 0;
        int allowedGap = Math.max(2, Math.round(width * 0.0035f));
        for (int x = coarse.left; x < coarse.right; x += stepX) {
            int orange = 0;
            int total = 0;
            for (int y = coarse.top; y < coarse.bottom; y += stepY) {
                int rgb = readRgb(buffer, pixelStride, rowStride, x, y);
                total++;
                if (isOrange(rgb)) orange++;
            }
            boolean active = total > 0 && orange / (float) total >= 0.16f;
            if (active) {
                if (first < 0) first = x;
                last = x;
                gap = 0;
            } else if (first >= 0) {
                gap += stepX;
                if (gap > allowedGap) break;
            }
        }
        if (first < 0 || last < first) return new Rect(coarse);
        int minWidth = Math.max(8, Math.round(width * 0.018f));
        if (last - first < minWidth) return new Rect(coarse);
        int pad = Math.max(2, Math.round(width * 0.003f));
        return new Rect(
            clamp(first - pad, 0, width - 2),
            coarse.top,
            clamp(last + pad, first + 1, width),
            coarse.bottom
        );
    }

    private List<Rect> detectButtonsInBand(
        ByteBuffer buffer,
        int pixelStride,
        int rowStride,
        int width,
        int height,
        int left,
        int right
    ) {
        int maxY = clamp(Math.round(height * 0.900f), 1, height);
        int stepY = Math.max(1, height / 720);
        int stepX = Math.max(3, width / 900);
        int allowedGap = Math.max(4, Math.round(height * 0.010f));
        int minHeight = Math.max(10, Math.round(height * 0.012f));

        List<Rect> result = new ArrayList<>();
        int runStart = -1;
        int lastActive = -1;
        for (int y = 0; y < maxY; y += stepY) {
            int orange = 0;
            int total = 0;
            for (int x = left; x < right; x += stepX) {
                int rgb = readRgb(buffer, pixelStride, rowStride, x, y);
                total++;
                if (isOrange(rgb)) orange++;
            }
            boolean active = total > 0 && orange / (float) total >= 0.085f;
            if (active) {
                if (runStart < 0) runStart = y;
                lastActive = y;
            } else if (runStart >= 0 && lastActive >= 0 && y - lastActive > allowedGap) {
                addButtonRun(result, left, right, height, runStart, lastActive, minHeight);
                runStart = -1;
                lastActive = -1;
            }
        }
        if (runStart >= 0) addButtonRun(result, left, right, height, runStart, lastActive, minHeight);

        result.sort(Comparator.comparingInt(Rect::centerY));
        List<Rect> merged = new ArrayList<>();
        int mergeGap = Math.max(9, Math.round(height * 0.016f));
        for (Rect rect : result) {
            if (merged.isEmpty()) {
                merged.add(new Rect(rect));
                continue;
            }
            Rect last = merged.get(merged.size() - 1);
            if (rect.top - last.bottom <= mergeGap) last.union(rect);
            else merged.add(new Rect(rect));
        }
        if (merged.size() > 6) {
            List<Integer> heights = new ArrayList<>();
            for (Rect rect : merged) heights.add(rect.height());
            Collections.sort(heights);
            final int median = heights.get(heights.size() / 2);
            merged.sort((a, b) -> Integer.compare(Math.abs(a.height() - median), Math.abs(b.height() - median)));
            merged = new ArrayList<>(merged.subList(0, 6));
            merged.sort(Comparator.comparingInt(Rect::centerY));
        }
        return merged;
    }

    private void addButtonRun(List<Rect> result, int left, int right, int height, int runStart, int lastActive, int minHeight) {
        if (lastActive < runStart) return;
        int runHeight = lastActive - runStart;
        int maxButtonHeight = Math.max(minHeight + 1, Math.round(height * 0.155f));
        if (runHeight >= minHeight && runHeight <= maxButtonHeight) {
            int pad = Math.max(3, Math.round(height * 0.004f));
            result.add(new Rect(left, Math.max(0, runStart - pad), right, Math.min(height, lastActive + pad)));
        }
    }

    private boolean isPlausibleButtonStack(List<Rect> buttons, int screenHeight) {
        if (buttons == null || buttons.isEmpty() || buttons.size() > 6 || screenHeight <= 0) return false;
        int minHeight = Math.max(10, Math.round(screenHeight * 0.014f));
        int maxHeight = Math.max(minHeight + 1, Math.round(screenHeight * 0.160f));
        for (Rect rect : buttons) {
            if (rect.height() < minHeight || rect.height() > maxHeight) return false;
        }
        if (buttons.size() == 1) return buttons.get(0).centerY() <= Math.round(screenHeight * 0.32f);
        int minGap = Math.round(screenHeight * 0.060f);
        int maxGap = Math.round(screenHeight * 0.320f);
        for (int i = 1; i < buttons.size(); i++) {
            int gap = buttons.get(i).centerY() - buttons.get(i - 1).centerY();
            if (gap < minGap || gap > maxGap) return false;
        }
        return true;
    }

    private float buttonBandScore(ByteBuffer buffer, int pixelStride, int rowStride, int width, int height, List<Rect> buttons) {
        if (buttons == null || buttons.isEmpty()) return -1f;
        float total = 0f;
        for (Rect rect : buttons) total += orangeRatio(buffer, pixelStride, rowStride, width, height, rect);
        return total / buttons.size();
    }

    private int[] signature(ByteBuffer buffer, int pixelStride, int rowStride, int width, int height, Rect rect) {
        final int cols = 6;
        final int rows = 4;
        int[] out = new int[cols * rows * 3];
        int index = 0;
        int left = clamp(rect.left, 0, width - 1);
        int right = clamp(rect.right, left + 1, width);
        int top = clamp(rect.top, 0, height - 1);
        int bottom = clamp(rect.bottom, top + 1, height);
        for (int gy = 0; gy < rows; gy++) {
            int cy = clamp(top + Math.round((gy + 0.5f) * (bottom - top) / rows), top, bottom - 1);
            for (int gx = 0; gx < cols; gx++) {
                int cx = clamp(left + Math.round((gx + 0.5f) * (right - left) / cols), left, right - 1);
                int rr = 0, gg = 0, bb = 0, samples = 0;
                for (int oy = -1; oy <= 1; oy++) {
                    for (int ox = -1; ox <= 1; ox++) {
                        int rgb = readRgb(buffer, pixelStride, rowStride,
                            clamp(cx + ox, left, right - 1), clamp(cy + oy, top, bottom - 1));
                        rr += (rgb >> 16) & 0xff;
                        gg += (rgb >> 8) & 0xff;
                        bb += rgb & 0xff;
                        samples++;
                    }
                }
                out[index++] = rr / samples;
                out[index++] = gg / samples;
                out[index++] = bb / samples;
            }
        }
        return out;
    }

    private float orangeRatio(ByteBuffer buffer, int pixelStride, int rowStride, int width, int height, Rect rect) {
        int left = clamp(rect.left, 0, width - 1);
        int right = clamp(rect.right, left + 1, width);
        int top = clamp(rect.top, 0, height - 1);
        int bottom = clamp(rect.bottom, top + 1, height);
        int stepX = Math.max(3, (right - left) / 24);
        int stepY = Math.max(2, (bottom - top) / 16);
        int orange = 0, total = 0;
        for (int y = top; y < bottom; y += stepY) {
            for (int x = left; x < right; x += stepX) {
                total++;
                if (isOrange(readRgb(buffer, pixelStride, rowStride, x, y))) orange++;
            }
        }
        return total == 0 ? 0f : orange / (float) total;
    }

    private int[] panelSignature(
        ByteBuffer buffer,
        int pixelStride,
        int rowStride,
        int width,
        int height,
        List<Rect> buttons
    ) {
        // Calibrate the page-signature ROI from the detected Aceitar column. This keeps
        // page-change detection tied to the actual GTO jobs panel on 16:9, 18:9, 20:9
        // and wider captures instead of assuming a fixed 68%-90% horizontal interval.
        final int cols = 8;
        final int rows = 8;
        int buttonLeft = Math.round(width * 0.910f);
        if (buttons != null && !buttons.isEmpty()) {
            buttonLeft = buttons.get(0).left;
            for (Rect rect : buttons) buttonLeft = Math.min(buttonLeft, rect.left);
        }
        int left = clamp(buttonLeft - Math.round(width * 0.285f), Math.round(width * 0.42f), width - 2);
        int right = clamp(buttonLeft - Math.round(width * 0.010f), left + 1, width);
        int top = clamp(Math.round(height * 0.030f), 0, height - 1);
        int bottom = clamp(Math.round(height * 0.875f), top + 1, height);
        int[] out = new int[cols * rows * 3];
        int index = 0;
        for (int gy = 0; gy < rows; gy++) {
            int y = clamp(top + Math.round((gy + 0.5f) * (bottom - top) / rows), top, bottom - 1);
            for (int gx = 0; gx < cols; gx++) {
                int x = clamp(left + Math.round((gx + 0.5f) * (right - left) / cols), left, right - 1);
                int rgb = readRgb(buffer, pixelStride, rowStride, x, y);
                out[index++] = (rgb >> 16) & 0xff;
                out[index++] = (rgb >> 8) & 0xff;
                out[index++] = rgb & 0xff;
            }
        }
        return out;
    }

    private int readRgb(ByteBuffer buffer, int pixelStride, int rowStride, int x, int y) {
        int offset = y * rowStride + x * pixelStride;
        if (offset < 0 || offset + 2 >= buffer.limit()) return 0;
        int r = buffer.get(offset) & 0xff;
        int g = buffer.get(offset + 1) & 0xff;
        int b = buffer.get(offset + 2) & 0xff;
        return (r << 16) | (g << 8) | b;
    }

    private boolean isOrange(int rgb) {
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        int chroma = max - min;
        boolean referenceOrange = r >= 130 && g >= 65 && g <= 235 && b <= 175
            && r >= g + 12 && g >= b + 8;
        boolean scaledOrange = r >= 92 && g >= 45 && chroma >= 34
            && r >= g * 1.08f && g >= b * 1.06f;
        // Recent GTO builds can render the same action button with a darker,
        // less saturated orange. Keep this fallback narrow enough to avoid treating
        // yellow/white UI as an Aceitar button.
        boolean darkOrange = r >= 82 && g >= 42 && b <= 105
            && r >= g * 1.16f && g >= b * 1.05f;
        return referenceOrange || scaledOrange || darkOrange;
    }

    static float signatureDistance(int[] a, int[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) return 1f;
        long sum = 0L;
        for (int i = 0; i < a.length; i++) sum += Math.abs(a[i] - b[i]);
        return sum / (255f * a.length);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
