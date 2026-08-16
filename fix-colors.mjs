import fs from "fs";
import path from "path";

function fixColors(dir) {
  for (const f of fs.readdirSync(dir)) {
    const fn = path.join(dir, f);
    if (fs.statSync(fn).isDirectory()) {
      fixColors(fn);
    } else if (fn.endsWith(".tsx") || fn.endsWith(".ts")) {
      let c = fs.readFileSync(fn, "utf-8");
      const original = c;

      // bg-gray-200 -> dark:bg-[#27272a]
      // bg-gray-100 -> dark:bg-[#1a1a1c]

      // Specifically for progress bars
      c = c.replace(/bg-gray-200\/70/g, "bg-gray-200/70 dark:bg-[#27272a]");
      c = c.replace(/bg-gray-200(?! dark:)/g, "bg-gray-200 dark:bg-[#3f3f46]");
      c = c.replace(/bg-gray-300(?! dark:)/g, "bg-gray-300 dark:bg-[#52525b]");

      // Check if some have duplicate dark:bg-slate-700
      c = c.replace(
        /dark:bg-\[#3f3f46\] dark:bg-slate-700/g,
        "dark:bg-slate-700",
      );

      // Let's remove aggressive shadows
      c = c.replace(/shadow-sm/g, "shadow-sm dark:shadow-none");
      c = c.replace(/shadow-md/g, "shadow-md dark:shadow-none");
      c = c.replace(/shadow-lg/g, "shadow-lg dark:shadow-none");
      c = c.replace(/shadow(?!-)/g, "shadow dark:shadow-none");
      c = c.replace(/shadow-inner/g, "shadow-inner dark:shadow-none");

      // Texts in dark mode usually should be softer
      c = c.replace(/dark:text-\[#f4f4f5\]/g, "dark:text-[#e4e4e7]"); // make white softer
      c = c.replace(/dark:text-\[#a1a1aa\]/g, "dark:text-[#a1a1aa]");

      // Buttons and toggles
      c = c.replace(/dark:bg-slate-700/g, "dark:bg-[#3f3f46]");

      if (c !== original) {
        fs.writeFileSync(fn, c, "utf-8");
      }
    }
  }
}
fixColors("src");
console.log("Fixed colors and shadows!");
