import fs from 'fs';

let content = fs.readFileSync('src/context/AppContext.tsx', 'utf8');
content = content.replace(
  /...companyUsers.map\(\(user\) => user.profilePhotoURL \|\| user.photoURL \|\| user.photoUrl \|\| user.avatar \|\| user.profileImage \|\| user.imageUrl \|\| user.photo\),/g,
  "...companyUsers.map((user: any) => user.profilePhotoURL || user.photoURL || user.photoUrl || user.avatar || user.profileImage || user.imageUrl || user.photo),"
);
content = content.replace(
  /...activeJobDrivers.map\(\(user\) => user.profilePhotoURL \|\| user.photoURL \|\| user.photoUrl \|\| user.avatar \|\| user.profileImage \|\| user.imageUrl \|\| user.photo\),/g,
  "...activeJobDrivers.map((user: any) => user.profilePhotoURL || user.photoURL || user.photoUrl || user.avatar || user.profileImage || user.imageUrl || user.photo),"
);

fs.writeFileSync('src/context/AppContext.tsx', content);
