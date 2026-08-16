package com.nvu.operacional;

/** Low-risk UX policy for removing the floating observer outside GTO. */
final class GtoBubbleDismissPolicy {
    private GtoBubbleDismissPolicy() {}

    static boolean shouldShowRemoveTarget(boolean gtoForeground, boolean dragging) {
        return dragging && !gtoForeground;
    }

    static boolean isDropInside(
        int bubbleX,
        int bubbleY,
        int bubbleWidth,
        int bubbleHeight,
        int targetX,
        int targetY,
        int targetWidth,
        int targetHeight
    ) {
        if (bubbleWidth <= 0 || bubbleHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) return false;
        int cx = bubbleX + bubbleWidth / 2;
        int cy = bubbleY + bubbleHeight / 2;
        return cx >= targetX && cx <= targetX + targetWidth
            && cy >= targetY && cy <= targetY + targetHeight;
    }
}
