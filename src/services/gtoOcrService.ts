const LOCAL_TESSDATA_PATH = "/tessdata";
const VALUE_CROP = {
  x: 0.28,
  y: 0.35,
  width: 0.44,
  height: 0.23,
};

const RESULT_ANALYSIS_CROP = {
  x: 0.12,
  y: 0.10,
  width: 0.76,
  height: 0.80,
};

export interface GtoReceiptAnalysis {
  value: string | null;
  doubledByAd: boolean;
  evidence: string[];
  analysisOk: boolean;
  rawText: string;
}

interface DecodedImage {
  source: CanvasImageSource;
  width: number;
  height: number;
  cleanup: () => void;
}

const MONEY_TOKEN_PATTERN =
  /[0-9OoQIl|!SsBb][0-9OoQIl|!SsBb.,\s]{1,32}[0-9OoQIl|!SsBb]/g;

const replaceLikelyDigitConfusions = (value: string): string =>
  value
    .replace(/[OoQ]/g, "0")
    .replace(/[Il|!]/g, "1")
    .replace(/[Ss]/g, "5")
    .replace(/[Bb]/g, "8");

const formatDigitsAsBrl = (digits: string): string | null => {
  const normalizedDigits = digits.replace(/\D/g, "");
  if (normalizedDigits.length < 3) return null;

  const cents = normalizedDigits.slice(-2);
  const integerDigits = normalizedDigits.slice(0, -2).replace(/^0+(?=\d)/, "");
  if (!integerDigits) return null;

  const integerPart = Number(integerDigits);
  if (!Number.isFinite(integerPart) || integerPart <= 0) return null;

  return `${integerPart.toLocaleString("pt-BR")},${cents}`;
};

const normalizeMonetaryCandidate = (candidate: string): string | null => {
  const corrected = replaceLikelyDigitConfusions(candidate)
    .replace(/\s+/g, "")
    .replace(/[^0-9.,]/g, "");

  if (!corrected) return null;

  const lastComma = corrected.lastIndexOf(",");
  const lastDot = corrected.lastIndexOf(".");
  const decimalSeparatorIndex = Math.max(lastComma, lastDot);

  if (decimalSeparatorIndex < 0) return null;

  const decimalDigits = corrected
    .slice(decimalSeparatorIndex + 1)
    .replace(/\D/g, "");

  if (decimalDigits.length !== 2) return null;

  return formatDigitsAsBrl(corrected);
};

const extractValueFromText = (rawText: string): string | null => {
  const text = rawText.replace(/\s+/g, " ").trim();
  if (!text) return null;

  const contextualMatch = text.match(
    /(?:valor\s*(?:a\s*)?receber|receber)(.{0,100})/i,
  );

  if (contextualMatch?.[1]) {
    const amountContext = contextualMatch[1].replace(
      /^\s*[:=\-]?\s*(?:r\s*[$s5]?|rs)?\s*/i,
      "",
    );
    const contextualCandidates = amountContext.match(MONEY_TOKEN_PATTERN);

    for (const candidate of contextualCandidates || []) {
      const normalized = normalizeMonetaryCandidate(candidate);
      if (normalized) return normalized;
    }
  }

  const targetedPatterns = [
    /(?:valor\s*(?:a\s*)?receber|receber)[^0-9]{0,40}([0-9][0-9OoQIl|!SsBb\s.,]{2,}[0-9OoQIl|!SsBb])/i,
    /(?:r\s*[$s5]|rs)[^0-9]{0,12}([0-9][0-9OoQIl|!SsBb\s.,]{2,}[0-9OoQIl|!SsBb])/i,
  ];

  for (const pattern of targetedPatterns) {
    const match = text.match(pattern);
    const normalized = match?.[1]
      ? normalizeMonetaryCandidate(match[1])
      : null;
    if (normalized) return normalized;
  }

  const genericCandidates = text.match(MONEY_TOKEN_PATTERN);

  if (!genericCandidates) return null;

  for (const candidate of genericCandidates) {
    const normalized = normalizeMonetaryCandidate(candidate);
    if (normalized) return normalized;
  }

  return null;
};


const normalizeEvidenceText = (rawText: string): string =>
  rawText
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/\s+/g, " ")
    .trim();

const detectDoubledByAdEvidence = (
  rawText: string,
): { detected: boolean; evidence: string[] } => {
  const normalized = normalizeEvidenceText(rawText);
  if (!normalized) return { detected: false, evidence: [] };

  const evidence: string[] = [];
  const pushEvidence = (label: string) => {
    if (!evidence.includes(label)) evidence.push(label);
  };

  // Strong confirmation phrases. Intentionally do not treat the normal
  // "Dobrar valor / ADS" button as proof that the driver watched an ad.
  if (/\bvalor (?:foi |ja )?dobrad[oa]\b/.test(normalized)) {
    pushEvidence("valor-dobrado");
  }
  if (/\bganh(?:o|os) (?:foi |foram |ja )?dobrad[oa]s?\b/.test(normalized)) {
    pushEvidence("ganho-dobrado");
  }
  if (/\b(?:anuncio|video)\b.{0,45}\b(?:assistid|concluid|finalizad)[oa]s?\b/.test(normalized)
      || /\b(?:assistid|concluid|finalizad)[oa]s?\b.{0,45}\b(?:anuncio|video)\b/.test(normalized)) {
    pushEvidence("midia-assistida");
  }
  if (/\b(?:bonus|recompensa)\b.{0,45}\b(?:recebid|aplicad|concedid|creditad)[oa]s?\b/.test(normalized)
      || /\b(?:recebid|aplicad|concedid|creditad)[oa]s?\b.{0,45}\b(?:bonus|recompensa)\b/.test(normalized)) {
    pushEvidence("recompensa-recebida");
  }

  const doubledMarker =
    /\bdobrad[oa]s?\b/.test(normalized)
    || /\b(?:2x|x2|valor x ?2)\b/.test(normalized);
  const completedAdMarker =
    /\b(?:anuncio|video)\b.{0,45}\b(?:assistid|concluid|finalizad)[oa]s?\b/.test(normalized)
    || /\b(?:assistid|concluid|finalizad)[oa]s?\b.{0,45}\b(?:anuncio|video)\b/.test(normalized)
    || /\b(?:bonus|recompensa)\b.{0,45}\b(?:recebid|aplicad|concedid|creditad)[oa]s?\b/.test(normalized);

  if (doubledMarker && completedAdMarker) {
    pushEvidence("dobro-confirmado-com-anuncio");
  }

  return {
    detected: evidence.length > 0,
    evidence,
  };
};

const decodeImage = async (file: File): Promise<DecodedImage> => {
  if (typeof createImageBitmap === "function") {
    try {
      const bitmap = await createImageBitmap(file);
      return {
        source: bitmap,
        width: bitmap.width,
        height: bitmap.height,
        cleanup: () => bitmap.close(),
      };
    } catch (error) {
      console.warn("[NVU GTO OCR] createImageBitmap fallback", error);
    }
  }

  const objectUrl = URL.createObjectURL(file);
  const image = new Image();
  image.decoding = "async";
  image.src = objectUrl;

  await new Promise<void>((resolve, reject) => {
    image.onload = () => resolve();
    image.onerror = () => reject(new Error("Não foi possível abrir a imagem."));
  });

  return {
    source: image,
    width: image.naturalWidth,
    height: image.naturalHeight,
    cleanup: () => URL.revokeObjectURL(objectUrl),
  };
};

const percentile = (histogram: Uint32Array, total: number, ratio: number): number => {
  const target = Math.max(1, Math.floor(total * ratio));
  let accumulated = 0;

  for (let index = 0; index < histogram.length; index += 1) {
    accumulated += histogram[index];
    if (accumulated >= target) return index;
  }

  return 255;
};

/**
 * GTO uses a fixed landscape result modal. Cropping the center of the image
 * removes the game HUD and makes the value line large enough for local OCR.
 */
const buildValueBandCanvas = async (file: File): Promise<HTMLCanvasElement> => {
  const decoded = await decodeImage(file);

  try {
    const sourceX = Math.round(decoded.width * VALUE_CROP.x);
    const sourceY = Math.round(decoded.height * VALUE_CROP.y);
    const sourceWidth = Math.max(1, Math.round(decoded.width * VALUE_CROP.width));
    const sourceHeight = Math.max(1, Math.round(decoded.height * VALUE_CROP.height));

    const scale = Math.min(4, Math.max(2, 1800 / sourceWidth));
    const border = 32;
    const targetWidth = Math.round(sourceWidth * scale);
    const targetHeight = Math.round(sourceHeight * scale);

    const canvas = document.createElement("canvas");
    canvas.width = targetWidth + border * 2;
    canvas.height = targetHeight + border * 2;

    const context = canvas.getContext("2d", { willReadFrequently: true });
    if (!context) throw new Error("Canvas OCR indisponível.");

    context.fillStyle = "#ffffff";
    context.fillRect(0, 0, canvas.width, canvas.height);
    context.imageSmoothingEnabled = true;
    context.imageSmoothingQuality = "high";
    context.drawImage(
      decoded.source,
      sourceX,
      sourceY,
      sourceWidth,
      sourceHeight,
      border,
      border,
      targetWidth,
      targetHeight,
    );

    const imageData = context.getImageData(border, border, targetWidth, targetHeight);
    const histogram = new Uint32Array(256);

    for (let index = 0; index < imageData.data.length; index += 4) {
      const red = imageData.data[index];
      const green = imageData.data[index + 1];
      const blue = imageData.data[index + 2];
      const luminance = Math.round(red * 0.299 + green * 0.587 + blue * 0.114);
      histogram[luminance] += 1;
    }

    const totalPixels = targetWidth * targetHeight;
    const brightTextThreshold = Math.min(
      205,
      Math.max(135, percentile(histogram, totalPixels, 0.84)),
    );

    // White text becomes black and the dark modal becomes a clean white page.
    for (let index = 0; index < imageData.data.length; index += 4) {
      const red = imageData.data[index];
      const green = imageData.data[index + 1];
      const blue = imageData.data[index + 2];
      const luminance = Math.round(red * 0.299 + green * 0.587 + blue * 0.114);
      const output = luminance >= brightTextThreshold ? 0 : 255;

      imageData.data[index] = output;
      imageData.data[index + 1] = output;
      imageData.data[index + 2] = output;
      imageData.data[index + 3] = 255;
    }

    context.putImageData(imageData, border, border);
    return canvas;
  } finally {
    decoded.cleanup();
  }
};


const buildResultAnalysisCanvas = async (file: File): Promise<HTMLCanvasElement> => {
  const decoded = await decodeImage(file);

  try {
    const sourceX = Math.round(decoded.width * RESULT_ANALYSIS_CROP.x);
    const sourceY = Math.round(decoded.height * RESULT_ANALYSIS_CROP.y);
    const sourceWidth = Math.max(1, Math.round(decoded.width * RESULT_ANALYSIS_CROP.width));
    const sourceHeight = Math.max(1, Math.round(decoded.height * RESULT_ANALYSIS_CROP.height));

    // Keep the analysis reasonably small for low-end devices. The result
    // dialog text is large enough that ~1500 px width remains OCR-friendly.
    const scale = Math.min(2.4, Math.max(1, 1500 / sourceWidth));
    const border = 24;
    const targetWidth = Math.max(1, Math.round(sourceWidth * scale));
    const targetHeight = Math.max(1, Math.round(sourceHeight * scale));

    const canvas = document.createElement("canvas");
    canvas.width = targetWidth + border * 2;
    canvas.height = targetHeight + border * 2;

    const context = canvas.getContext("2d", { willReadFrequently: true });
    if (!context) throw new Error("Canvas OCR indisponível.");

    context.fillStyle = "#ffffff";
    context.fillRect(0, 0, canvas.width, canvas.height);
    context.imageSmoothingEnabled = true;
    context.imageSmoothingQuality = "high";
    context.drawImage(
      decoded.source,
      sourceX,
      sourceY,
      sourceWidth,
      sourceHeight,
      border,
      border,
      targetWidth,
      targetHeight,
    );

    const imageData = context.getImageData(border, border, targetWidth, targetHeight);
    const histogram = new Uint32Array(256);

    for (let index = 0; index < imageData.data.length; index += 4) {
      const red = imageData.data[index];
      const green = imageData.data[index + 1];
      const blue = imageData.data[index + 2];
      const luminance = Math.round(red * 0.299 + green * 0.587 + blue * 0.114);
      histogram[luminance] += 1;
    }

    const totalPixels = targetWidth * targetHeight;
    const threshold = Math.min(
      210,
      Math.max(125, percentile(histogram, totalPixels, 0.80)),
    );

    for (let index = 0; index < imageData.data.length; index += 4) {
      const red = imageData.data[index];
      const green = imageData.data[index + 1];
      const blue = imageData.data[index + 2];
      const luminance = Math.round(red * 0.299 + green * 0.587 + blue * 0.114);
      const output = luminance >= threshold ? 0 : 255;

      imageData.data[index] = output;
      imageData.data[index + 1] = output;
      imageData.data[index + 2] = output;
      imageData.data[index + 3] = 255;
    }

    context.putImageData(imageData, border, border);
    return canvas;
  } finally {
    decoded.cleanup();
  }
};

const ocrLogger = (message: { status: string; progress?: number }) => {
  if (message.status === "recognizing text") {
    console.log(
      "[NVU GTO OCR] progress",
      Math.round((message.progress || 0) * 100),
    );
  }
};

const recognizeLocally = async (image: HTMLCanvasElement): Promise<string> => {
  const tesseractModule = await import("tesseract.js");
  const Tesseract = tesseractModule.default || tesseractModule;

  try {
    const result = await Tesseract.recognize(image, "eng", {
      langPath: LOCAL_TESSDATA_PATH,
      logger: ocrLogger,
    });

    return result.data.text || "";
  } catch (localModelError) {
    // Free network fallback. It is only used when the bundled language file
    // cannot be loaded, keeping older deployments compatible.
    console.warn("[NVU GTO OCR] local model fallback", localModelError);
    const result = await Tesseract.recognize(image, "eng", {
      logger: ocrLogger,
    });
    return result.data.text || "";
  }
};

export async function analyzeGtoTripReceipt(
  file: File,
): Promise<GtoReceiptAnalysis> {
  try {
    console.log("[NVU GTO OCR] receipt analysis start", file.name, file.size);

    const resultCanvas = await buildResultAnalysisCanvas(file);
    const resultText = await recognizeLocally(resultCanvas);
    const normalizedResultText = resultText.replace(/\s+/g, " ").trim();
    const adEvidence = detectDoubledByAdEvidence(normalizedResultText);
    let value = extractValueFromText(normalizedResultText);

    // The wider result crop normally finds both value and confirmation text.
    // If it misses only the value, use the legacy narrow value crop as a
    // precision fallback instead of performing two OCR passes every time.
    let combinedText = normalizedResultText;
    if (!value) {
      const valueCanvas = await buildValueBandCanvas(file);
      const valueText = await recognizeLocally(valueCanvas);
      const normalizedValueText = valueText.replace(/\s+/g, " ").trim();
      value = extractValueFromText(normalizedValueText);
      combinedText = `${normalizedResultText} ${normalizedValueText}`.trim();
    }

    console.log("[NVU GTO OCR] receipt text", combinedText);
    console.log("[NVU GTO OCR] receipt value", value);
    console.log("[NVU GTO OCR] doubled by ad", adEvidence.detected, adEvidence.evidence);

    return {
      value,
      doubledByAd: adEvidence.detected,
      evidence: adEvidence.evidence,
      analysisOk: Boolean(combinedText),
      rawText: combinedText,
    };
  } catch (error) {
    console.error("[NVU GTO OCR] receipt analysis error", error);
    return {
      value: null,
      doubledByAd: false,
      evidence: [],
      analysisOk: false,
      rawText: "",
    };
  }
}

export async function extractGtoTripValue(file: File): Promise<string | null> {
  const analysis = await analyzeGtoTripReceipt(file);
  return analysis.value;
}

export const __gtoOcrTestUtils = {
  extractValueFromText,
  normalizeMonetaryCandidate,
  normalizeEvidenceText,
  detectDoubledByAdEvidence,
};
