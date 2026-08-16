import fs from "fs";
import path from "path";

function fixInputs(dir) {
  for (const f of fs.readdirSync(dir)) {
    const fn = path.join(dir, f);
    if (fs.statSync(fn).isDirectory()) {
      fixInputs(fn);
    } else if (fn.endsWith(".tsx") || fn.endsWith(".ts")) {
      let c = fs.readFileSync(fn, "utf-8");
      const original = c;

      // Let's ensure text inputs look good in dark mode
      c = c.replace(
        /bg-gray-50 dark:bg-slate-800/g,
        "bg-gray-50 dark:bg-[#121213]",
      );
      c = c.replace(
        /bg-gray-50 dark:bg-\[#141415\]/g,
        "bg-gray-50 dark:bg-[#121213]",
      );
      c = c.replace(
        /focus:bg-white dark:focus:bg-slate-900/g,
        "focus:bg-white dark:focus:bg-[#0a0a0b]",
      );

      // Placeholder
      c = c.replace(
        /placeholder:text-slate-400(?! dark:)/g,
        "placeholder:text-slate-400 dark:placeholder:text-[#71717a]",
      );
      c = c.replace(
        /placeholder:text-gray-400(?! dark:)/g,
        "placeholder:text-gray-400 dark:placeholder:text-[#71717a]",
      );

      if (c !== original) {
        fs.writeFileSync(fn, c, "utf-8");
      }
    }
  }
}
fixInputs("src");
console.log("Fixed inputs!");
