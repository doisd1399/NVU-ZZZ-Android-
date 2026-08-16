import fs from "fs";
import path from "path";

function fixFile(fn) {
  let c = fs.readFileSync(fn, "utf-8");
  const original = c;

  // 1. Swap the colors for Active Job cards.
  // The outer wrapper (active job): currently we set it to bg-white dark:bg-[#09090b]
  // We want it to be cinza in dark mode: dark:bg-[#1A1F26].
  // And its border should match "Resumo Operacional": border-gray-200 dark:border-[#2A2F3A] hover:border-gray-300 dark:hover:border-gray-700
  // Note: currently it uses: border-slate-100 shadow-sm dark:shadow-none hover:border-slate-200 hover:shadow-md dark:shadow-none
  c = c.replace(
    /border-slate-100 shadow-sm dark:shadow-none hover:border-slate-200/g,
    "border-gray-200 dark:border-[#2A2F3A] shadow-sm dark:shadow-none hover:border-gray-300 dark:hover:border-gray-600",
  );

  // Change wrapper background: dark:bg-[#09090b] -> dark:bg-[#1A1F26]
  c = c.replace(/bg-white dark:bg-\[#09090b\]/g, "bg-white dark:bg-[#1A1F26]");

  // 2. The inner "entregas realizadas" card
  // Currently: border-slate-100 dark:border-[#2A2F3A]/50 bg-gray-100 dark:bg-[#1A1F26]
  // In light mode: gray (bg-gray-100), dark mode: black (dark:bg-[#09090b])
  c = c.replace(
    /border-slate-100 dark:border-\[#2A2F3A\]\/50 bg-gray-100 dark:bg-\[#1A1F26\]/g,
    "border-gray-200 dark:border-[#2A2F3A] bg-gray-100 dark:bg-[#09090b]",
  );
  // if some file has it slightly different:
  c = c.replace(
    /border-slate-100 dark:border-\[#2A2F3A\]\/50 bg-gray-100 dark:bg-\[#09090b\]/g,
    "border-gray-200 dark:border-[#2A2F3A] bg-gray-100 dark:bg-[#09090b]",
  );

  // The inner ON/OFF indicator card bg was bg-white dark:bg-[#1A1F26]. If we made the outer card 1A1F26, the ON/OFF card bg can be black or just transparent.
  // Actually let's just reverse the inner circle background from 1A1F26 to 09090b since the background is now 1A1F26 that way the circle fits!
  // Let's use 09090b for the inner inner stuff.
  c = c.replace(
    /bg-gray-50 dark:bg-\[#1A1F26\] border border-gray-200 dark:border-\[#2A2F3A\]/g,
    "bg-gray-50 dark:bg-[#09090b] border border-gray-200 dark:border-[#2A2F3A]",
  );
  c = c.replace(
    /bg-white dark:bg-\[#1A1F26\] border border-gray-200 dark:border-\[#2A2F3A\] rounded-full/g,
    "bg-white dark:bg-[#09090b] border border-gray-200 dark:border-[#2A2F3A] rounded-full",
  );

  // Let's also make sure the menu lateral is black.
  // "menu lateral na cor preta tbm." -> The user asked this in the first request but I didn't see that line clearly.
  // Wait, I did fix DriverLayout and AdminLayout to be dark:bg-[#09090b] in the side menu! Let's double check.

  if (c !== original) {
    fs.writeFileSync(fn, c, "utf-8");
  }
}

fixFile("src/pages/admin/Dashboard.tsx");
fixFile("src/pages/admin/Operations.tsx");
