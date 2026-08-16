import fs from "fs";
import path from "path";

function fixFile(fn) {
  let c = fs.readFileSync(fn, "utf-8");
  const original = c;

  // Header and sidebar backgrounds
  c = c.replace(/bg-white dark:bg-\[#1A1F26\]/g, "bg-white dark:bg-[#09090b]");
  c = c.replace(
    /bg-gray-50 dark:bg-\[#1A1F26\]/g,
    "bg-gray-50 dark:bg-[#09090b]",
  );
  c = c.replace(
    /hover:bg-gray-50 dark:bg-\[#1A1F26\] dark:hover:bg-\[#3f3f46\]/g,
    "hover:bg-gray-50 dark:hover:bg-[#18181b]",
  );

  if (c !== original) {
    fs.writeFileSync(fn, c, "utf-8");
  }
}

fixFile("src/layouts/DriverLayout.tsx");
fixFile("src/layouts/AdminLayout.tsx");
