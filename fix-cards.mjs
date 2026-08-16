import fs from "fs";
import path from "path";

function fixFile(fn) {
  let c = fs.readFileSync(fn, "utf-8");
  const original = c;

  // Replace the specific bg-[#f8fafc] in Admin Dashboard and Operations
  c = c.replace(/bg-\[#f8fafc\]/g, "bg-gray-100 dark:bg-[#09090b]");

  if (c !== original) {
    fs.writeFileSync(fn, c, "utf-8");
  }
}

fixFile("src/pages/admin/Dashboard.tsx");
fixFile("src/pages/admin/Operations.tsx");

function fixDriverDashboard() {
  const fn = "src/pages/driver/Dashboard.tsx";
  let c = fs.readFileSync(fn, "utf-8");
  const original = c;

  // The controls inside the active job
  c = c.replace(
    /px-4 py-3 bg-gray-50 dark:bg-\[#1A1F26\]/g,
    "px-4 py-3 bg-gray-100 dark:bg-[#09090b]",
  );
  // The vehicle / trailer tag
  c = c.replace(
    /bg-gray-50 dark:bg-\[#1A1F26\] border/g,
    "bg-gray-100 dark:bg-[#09090b] border",
  );

  if (c !== original) {
    fs.writeFileSync(fn, c, "utf-8");
  }
}
fixDriverDashboard();
