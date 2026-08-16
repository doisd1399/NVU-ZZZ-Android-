import {
  resolveApprovedCompanyOwnerPhoto,
  resolvePersistedUserProfilePhoto,
} from "./src/lib/profilePhotoRecovery";

const ownerPhoto = "data:image/jpeg;base64,OWNER_PHOTO";

const preserved = resolvePersistedUserProfilePhoto(
  { name: "Documento principal", profilePhotoURL: "" },
  { name: "Documento duplicado", profilePhotoURL: ownerPhoto },
);
if (preserved !== ownerPhoto) {
  throw new Error("A unificação de login apagaria a foto de perfil existente.");
}

const recovered = resolveApprovedCompanyOwnerPhoto([
  {
    type: "company_registration",
    status: "pending",
    ownerPhotoUrl: "data:image/jpeg;base64,PENDING",
    createdAt: "2026-07-19T10:00:00.000Z",
  },
  {
    type: "company_registration",
    status: "approved",
    ownerPhotoUrl: ownerPhoto,
    createdAt: "2026-07-19T11:00:00.000Z",
  },
]);
if (recovered !== ownerPhoto) {
  throw new Error("A recuperação não encontrou a foto da inscrição aprovada.");
}

const rejectedOnly = resolveApprovedCompanyOwnerPhoto([
  {
    type: "company_registration",
    status: "rejected",
    ownerPhotoUrl: ownerPhoto,
  },
]);
if (rejectedOnly !== "") {
  throw new Error("A recuperação não pode usar inscrição rejeitada.");
}

console.log("profile-photo-approval-regression: 3/3 approved");
