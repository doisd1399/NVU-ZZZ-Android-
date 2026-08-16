import fs from 'fs';
let content = fs.readFileSync('src/context/AppContext.tsx', 'utf8');

content = content.replace(
  /...companyUsers.map\(\(user\) => user.profilePhotoURL\),/g,
  "...companyUsers.map((user) => user.profilePhotoURL || user.photoURL || user.photoUrl || user.avatar || user.profileImage || user.imageUrl || user.photo),"
).replace(
  /...activeJobDrivers.map\(\(user\) => user.profilePhotoURL\),/g,
  "...activeJobDrivers.map((user) => user.profilePhotoURL || user.photoURL || user.photoUrl || user.avatar || user.profileImage || user.imageUrl || user.photo),"
);

fs.writeFileSync('src/context/AppContext.tsx', content);
