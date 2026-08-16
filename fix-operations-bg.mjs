import fs from "fs";
import path from "path";

function fixFile(fn) {
  let c = fs.readFileSync(fn, "utf-8");
  const original = c;

  // Outer card for jobs list: change to black in dark mode
  c = c.replace(
    /className=\{cn\("relative flex flex-col border rounded-\[24px\] transition-all overflow-hidden bg-white dark:bg-\[#1A1F26\]"/g,
    'className={cn("relative flex flex-col border rounded-[24px] transition-all overflow-hidden bg-white dark:bg-[#09090b]"',
  );

  // Inner "Entregas Realizadas" card: change from dark:bg-[#09090b] to dark:bg-[#1A1F26] so it contrasts against the black parent
  c = c.replace(
    /border-slate-100 dark:border-\[#2A2F3A\]\/50 bg-gray-100 dark:bg-\[#09090b\] rounded-2xl/g,
    "border-slate-100 dark:border-[#2A2F3A]/50 bg-gray-100 dark:bg-[#1A1F26] rounded-2xl",
  );

  // Also fix the ON/OFF indicator's bg and inner circle container
  c = c.replace(
    /bg-gray-50 dark:bg-\[#1A1F26\] border border-gray-200 dark:border-\[#2A2F3A\] rounded-lg/g,
    "bg-gray-50 dark:bg-[#1A1F26] border border-gray-200 dark:border-[#2A2F3A] rounded-lg",
  ); // Keep #1A1F26 or #09090b here? Inner card, let's make it #1A1F26.

  // Specifically target the ON/OFF inner circle that was set to #1A1F26
  c = c.replace(
    /bg-white dark:bg-\[#1A1F26\] border border-gray-200 dark:border-\[#2A2F3A\] rounded-full/g,
    "bg-white dark:bg-[#1A1F26] border border-gray-200 dark:border-[#2A2F3A] rounded-full",
  );

  if (c !== original) {
    fs.writeFileSync(fn, c, "utf-8");
  }
}

fixFile("src/pages/admin/Dashboard.tsx");
fixFile("src/pages/admin/Operations.tsx");
