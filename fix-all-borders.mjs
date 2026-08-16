import fs from "fs";
import path from "path";

function fixAllBorders(dir) {
  for (const f of fs.readdirSync(dir)) {
    const fn = path.join(dir, f);
    if (fs.statSync(fn).isDirectory()) {
      fixAllBorders(fn);
    } else if (fn.endsWith(".tsx") || fn.endsWith(".ts")) {
      let c = fs.readFileSync(fn, "utf-8");
      const original = c;

      // Let's ensure all border-gray-* have a dark counterpart
      c = c.replace(
        /border-gray-50(?! dark:)/g,
        "border-gray-50 dark:border-[#27272a]/50",
      );
      c = c.replace(
        /border-gray-100(?! dark:)/g,
        "border-gray-100 dark:border-[#27272a]",
      );
      c = c.replace(
        /border-gray-200(?! dark:)/g,
        "border-gray-200 dark:border-[#3f3f46]",
      );
      c = c.replace(
        /border-gray-300(?! dark:)/g,
        "border-gray-300 dark:border-[#52525b]",
      );

      // Ensure text-gray has dark counterpart
      c = c.replace(
        /text-gray-900(?! dark:)/g,
        "text-gray-900 dark:text-[#f4f4f5]",
      );
      c = c.replace(
        /text-gray-800(?! dark:)/g,
        "text-gray-800 dark:text-[#e4e4e7]",
      );
      c = c.replace(
        /text-gray-700(?! dark:)/g,
        "text-gray-700 dark:text-[#d4d4d8]",
      );
      c = c.replace(
        /text-gray-600(?! dark:)/g,
        "text-gray-600 dark:text-[#a1a1aa]",
      );
      c = c.replace(
        /text-gray-500(?! dark:)/g,
        "text-gray-500 dark:text-[#a1a1aa]",
      );

      // bg-white
      c = c.replace(/bg-white(?! dark:)/g, "bg-white dark:bg-[#0a0a0b]");

      if (c !== original) {
        fs.writeFileSync(fn, c, "utf-8");
      }
    }
  }
}
fixAllBorders("src");
console.log("Fixed missing dark classes across the board!");
