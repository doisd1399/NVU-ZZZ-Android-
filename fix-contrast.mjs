import fs from "fs";
import path from "path";

function fixDir(dir) {
  for (const f of fs.readdirSync(dir)) {
    const fn = path.join(dir, f);
    if (fs.statSync(fn).isDirectory()) {
      fixDir(fn);
    } else if (fn.endsWith(".tsx") || fn.endsWith(".ts")) {
      let c = fs.readFileSync(fn, "utf-8");
      const original = c;

      // Global text color improvements for dark mode across the board
      c = c.replace(
        /text-gray-900 dark:text-\[#d4d4d8\]/g,
        "text-gray-900 dark:text-[#fafafa]",
      );
      c = c.replace(
        /text-slate-900 dark:text-\[#d4d4d8\]/g,
        "text-slate-900 dark:text-[#fafafa]",
      );

      // Secondary text
      c = c.replace(
        /text-gray-500 dark:text-\[#d4d4d8\]/g,
        "text-gray-500 dark:text-[#a1a1aa]",
      );
      c = c.replace(
        /text-slate-500 dark:text-\[#d4d4d8\]/g,
        "text-slate-500 dark:text-[#a1a1aa]",
      );

      // Missing dark text variants for blue text
      c = c.replace(
        /text-blue-600 bg-blue-50/g,
        "text-blue-600 dark:text-blue-300 bg-blue-50",
      );

      // Progress bars
      c = c.replace(
        /h-full bg-gray-900 rounded-full/g,
        "h-full bg-gray-900 dark:bg-blue-500 rounded-full",
      );
      c = c.replace(
        /h-full bg-slate-900 rounded-full/g,
        "h-full bg-slate-900 dark:bg-blue-500 rounded-full",
      );

      // The Marcar Entrega button
      c = c.replace(
        /bg-green-50 dark:bg-green-500\/100 hover:bg-green-600 text-white/g,
        "bg-green-600 dark:bg-green-600 hover:bg-green-700 text-white shadow-sm dark:shadow-none border-none",
      );

      // Desfazer ultima dev underline text
      c = c.replace(
        /text-gray-400 hover:text-gray-600 dark:hover:text-\[#a1a1aa\] underline/g,
        "text-gray-500 hover:text-gray-700 dark:text-[#a1a1aa] dark:hover:text-[#fafafa] underline",
      );

      // Revogar aprovacao button (we need to find it first, let's just do a generic replace if it exists)
      c = c.replace(
        /bg-blue-500 hover:bg-blue-600 text-white shadow font-medium flex items-center justify-center gap-1.5/g,
        "bg-red-50 dark:bg-red-500/10 hover:bg-red-100 dark:hover:bg-red-500/20 text-red-600 dark:text-red-400 rounded-xl font-bold h-10 px-4 transition-all w-full sm:w-auto",
      );

      if (c !== original) {
        fs.writeFileSync(fn, c, "utf-8");
      }
    }
  }
}
fixDir("src");
console.log("Fixed contrast issues!");
