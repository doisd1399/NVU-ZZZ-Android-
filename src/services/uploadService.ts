import { getDownloadURL, ref, uploadBytesResumable } from "firebase/storage";
import imageCompression from "browser-image-compression";
import { auth, storage } from "../lib/firebase";
import {
  FILE_ACCESS_ERROR_CODE,
  isFileAccessError,
  normalizeFileAccessError,
} from "../lib/fileAccess";

export const AUTHENTICATED_STORAGE_RULE_MAX_BYTES = 2_000_000;
export const DEFAULT_UPLOAD_MAX_BYTES = 1_800_000;

export interface UploadOptions {
  file: File;
  companyId: string;
  userId: string;
  folder?: string;
  onProgress?: (progress: number) => void;
  compressionMaxSizeMB?: number;
  maxWidthOrHeight?: number;
  maxOutputBytes?: number;
  storageScope?: "default" | "trip-receipt";
}

export class UploadError extends Error {
  code?: string;

  constructor(message: string, code?: string) {
    super(message);
    this.name = "UploadError";
    this.code = code;
  }
}

const inferImageType = (file: File): string => {
  const declaredType = String(file.type || "").trim().toLowerCase();
  if (declaredType) return declaredType;

  const extension = file.name.split(".").pop()?.toLowerCase();
  if (extension === "jpg" || extension === "jpeg") return "image/jpeg";
  if (extension === "png") return "image/png";
  if (extension === "webp") return "image/webp";
  if (extension === "heic") return "image/heic";
  if (extension === "heif") return "image/heif";
  return "";
};

const supportedSourceTypes = new Set([
  "image/jpeg",
  "image/png",
  "image/webp",
  "image/heic",
  "image/heif",
]);

const normalizeFirebaseStorageError = (error: unknown): UploadError => {
  if (isFileAccessError(error)) {
    const fileError = normalizeFileAccessError(error);
    return new UploadError(fileError.message, FILE_ACCESS_ERROR_CODE);
  }

  const rawError = error as {
    code?: unknown;
    message?: unknown;
    serverResponse?: unknown;
  };
  const code = typeof rawError?.code === "string" ? rawError.code : "";

  const messages: Record<string, string> = {
    "storage/unauthenticated":
      "Sua sessão do Firebase não está ativa. Entre novamente e tente enviar a imagem.",
    "storage/unauthorized":
      "O Firebase Storage recusou o envio pelas regras publicadas. Publique o arquivo storage.rules deste projeto e tente novamente.",
    "storage/quota-exceeded":
      "A cota ou o faturamento do Firebase Storage impediu o envio.",
    "storage/retry-limit-exceeded":
      "O envio excedeu o tempo de tentativa. Verifique a conexão e tente novamente.",
    "storage/canceled": "O envio da imagem foi cancelado.",
    "storage/bucket-not-found":
      "O bucket do Firebase Storage não foi encontrado. Verifique a configuração do projeto.",
    "storage/project-not-found":
      "O projeto Firebase configurado no aplicativo não foi encontrado.",
  };

  const fallbackMessage =
    typeof rawError?.message === "string" && rawError.message.trim()
      ? rawError.message.trim()
      : "Falha ao enviar a imagem para o Firebase Storage.";
  const message = messages[code] || fallbackMessage;

  return new UploadError(
    code ? `${message} (${code})` : message,
    code || undefined,
  );
};

/**
 * Service to upload files to Firebase Storage with a standardized structure.
 */
export const uploadService = {
  /**
   * Uploads an image file to Firebase Storage.
   *
   * @param options Upload options including file and path parameters
   * @returns Promise resolving to the public download URL of the uploaded file
   */
  async uploadImage({
    file,
    companyId,
    userId,
    folder = "uploads",
    onProgress,
    compressionMaxSizeMB = 1,
    maxWidthOrHeight = 1920,
    maxOutputBytes = DEFAULT_UPLOAD_MAX_BYTES,
    storageScope = "default",
  }: UploadOptions): Promise<string> {
    const sourceType = inferImageType(file);
    if (!supportedSourceTypes.has(sourceType)) {
      throw new UploadError(
        "Formato de arquivo inválido. Use JPG, PNG, WEBP, HEIC ou HEIF.",
      );
    }

    const maxSourceSizeBytes = 10 * 1024 * 1024;
    if (file.size > maxSourceSizeBytes) {
      throw new UploadError(
        "Arquivo original muito grande. O limite máximo é de 10 MB.",
      );
    }

    if (
      !Number.isFinite(maxOutputBytes) ||
      maxOutputBytes <= 0 ||
      maxOutputBytes >= AUTHENTICATED_STORAGE_RULE_MAX_BYTES
    ) {
      throw new UploadError(
        "O limite interno do upload está incompatível com as regras do Firebase Storage.",
      );
    }

    // Wait for Firebase Auth to finish restoring the native/web session before
    // starting an authenticated Storage request.
    await auth.authStateReady();
    if (!auth.currentUser) {
      throw new UploadError(
        "Sua sessão do Firebase não está ativa. Entre novamente e tente enviar a imagem.",
        "storage/unauthenticated",
      );
    }

    let finalFile = file;
    try {
      const compressedBlob = await imageCompression(file, {
        maxSizeMB: compressionMaxSizeMB,
        maxWidthOrHeight,
        useWebWorker: true,
        fileType: "image/webp",
      });

      finalFile = new File(
        [compressedBlob],
        file.name.replace(/\.[^/.]+$/, ".webp"),
        { type: "image/webp" },
      );
    } catch (error) {
      if (isFileAccessError(error)) {
        const fileError = normalizeFileAccessError(error);
        throw new UploadError(fileError.message, FILE_ACCESS_ERROR_CODE);
      }

      // A small original file can still be uploaded safely. A large original
      // must never be sent after compression failure because Storage will deny
      // it and the user would only see a generic upload failure.
      if (file.size >= maxOutputBytes) {
        console.error("Image compression failed", error);
        throw new UploadError(
          "Não foi possível compactar a imagem para o tamanho permitido. Escolha outra imagem ou tente novamente.",
        );
      }
      console.warn("Image compression failed; using the small original file", error);
    }

    if (finalFile.size >= maxOutputBytes) {
      throw new UploadError(
        "Não foi possível reduzir a imagem para o tamanho permitido. Escolha outra imagem e tente novamente.",
      );
    }

    const timestamp = Date.now();
    const sanitizedName = finalFile.name.replace(/[^a-zA-Z0-9.\-_]/g, "");
    const cleanFileName = sanitizedName || `imagem-${timestamp}.webp`;
    const cleanCompanyId = String(companyId || "Geral").replace(
      /[^a-zA-Z0-9.\-_]/g,
      "",
    );
    const cleanUserId = String(userId || auth.currentUser.uid).replace(
      /[^a-zA-Z0-9.\-_]/g,
      "",
    );
    const cleanFolder = String(folder || "uploads").replace(
      /[^a-zA-Z0-9.\-_]/g,
      "",
    );
    const path =
      storageScope === "trip-receipt"
        ? `trip-receipts/${cleanCompanyId || "Geral"}/${cleanUserId || auth.currentUser.uid}/${timestamp}_${cleanFileName}`
        : `empresas/${cleanCompanyId || "Geral"}/${cleanFolder || "uploads"}/${cleanUserId || auth.currentUser.uid}/${timestamp}_${cleanFileName}`;
    const storageRef = ref(storage, path);

    const uploadTask = uploadBytesResumable(storageRef, finalFile, {
      contentType: finalFile.type || sourceType || "image/webp",
      cacheControl: "public,max-age=31536000,immutable",
      customMetadata: {
        originalFileName: file.name,
        uploadedBy: auth.currentUser.uid,
      },
    });

    return new Promise((resolve, reject) => {
      uploadTask.on(
        "state_changed",
        (snapshot) => {
          const progress =
            snapshot.totalBytes > 0
              ? (snapshot.bytesTransferred / snapshot.totalBytes) * 100
              : 0;
          onProgress?.(progress);
        },
        (error) => {
          console.error("Erro no upload para o Firebase Storage:", {
            code: (error as { code?: string })?.code,
            message: (error as { message?: string })?.message,
            path,
            bytes: finalFile.size,
            contentType: finalFile.type,
          });
          reject(normalizeFirebaseStorageError(error));
        },
        async () => {
          try {
            const downloadURL = await getDownloadURL(uploadTask.snapshot.ref);
            resolve(downloadURL);
          } catch (error) {
            console.error("Erro ao obter URL de download:", error);
            reject(normalizeFirebaseStorageError(error));
          }
        },
      );
    });
  },
};
