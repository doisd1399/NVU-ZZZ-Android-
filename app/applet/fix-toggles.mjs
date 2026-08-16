import fs from "fs";

function fixFile(fn) {
  let c = fs.readFileSync(fn, "utf-8");
  let original = c;

  c = c.replace(
    /peer-checked:bg-\[\#32D74B\](?!\s+dark:peer-checked:bg)/g,
    "peer-checked:bg-[#32D74B] dark:peer-checked:bg-[#32D74B]",
  );

  if (c !== original) {
    fs.writeFileSync(fn, c, "utf-8");
    console.log(`Fixed ${fn}`);
  }
}

fixFile("src/pages/driver/Dashboard.tsx");
fixFile("src/pages/driver/Profile.tsx");
