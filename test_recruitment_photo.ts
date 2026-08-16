import assert from "node:assert/strict";
import {
  getRecruitmentInitials,
  getRecruitmentPhotoStorageCandidates,
  resolveRecruitmentPhoto,
} from "./src/lib/recruitmentPhoto.ts";

const application = {
  id: "app-1",
  fullName: "Maria Silva",
  applicationPhotoURL: "",
  photoUrl: "https://storage.example/candidate.jpg",
};

assert.equal(
  resolveRecruitmentPhoto(application),
  "https://storage.example/candidate.jpg",
);
assert.equal(
  resolveRecruitmentPhoto(
    { applicationPhotoURL: "" },
    { profilePhotoURL: "https://storage.example/profile.jpg" },
  ),
  "https://storage.example/profile.jpg",
);
assert.equal(
  resolveRecruitmentPhoto(
    { applicationPhotoURL: "https://ui-profilePhotoURLs.com/api/?name=Maria" },
    { profilePhotoURL: "https://storage.example/profile.jpg" },
  ),
  "https://storage.example/profile.jpg",
);
assert.deepEqual(
  getRecruitmentPhotoStorageCandidates({
    applicationPhotoURL: "empresas/acme/recruitment_photos/photo.jpg",
  }),
  ["empresas/acme/recruitment_photos/photo.jpg"],
);
assert.equal(getRecruitmentInitials("Maria Silva"), "MS");
assert.equal(getRecruitmentInitials(""), "?");

console.log("Recruitment photo resolution tests passed.");
