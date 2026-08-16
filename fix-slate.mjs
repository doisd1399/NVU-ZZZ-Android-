import fs from "fs";
import path from "path";

function fixSlateInRecruitment(dir) {
  for (const f of fs.readdirSync(dir)) {
    const fn = path.join(dir, f);
    if (fs.statSync(fn).isDirectory()) {
      fixSlateInRecruitment(fn);
    } else if (fn.endsWith(".tsx") || fn.endsWith(".ts")) {
      let c = fs.readFileSync(fn, "utf-8");
      const original = c;

      // bg-white -> dark:bg-[#09090b] or #18181b depending
      // border-slate-200 -> dark:border-[#27272a]
      c = c.replace(
        /border-slate-100(?!.*dark:)/g,
        "border-slate-100 dark:border-[#27272a]/50",
      );
      c = c.replace(
        /border-slate-200(?!.*dark:)/g,
        "border-slate-200 dark:border-[#27272a]",
      );
      c = c.replace(
        /border-slate-300(?!.*dark:)/g,
        "border-slate-300 dark:border-[#3f3f46]",
      );
      c = c.replace(
        /focus:border-slate-900(?!.*dark:)/g,
        "focus:border-slate-900 dark:focus:border-[#e4e4e7]",
      );
      c = c.replace(
        /focus:ring-slate-900\/20(?!.*dark:)/g,
        "focus:ring-slate-900/20 dark:focus:ring-[#e4e4e7]/20",
      );

      c = c.replace(
        /text-slate-900(?!.*dark:)/g,
        "text-slate-900 dark:text-[#fafafa]",
      );
      c = c.replace(
        /text-slate-800(?!.*dark:)/g,
        "text-slate-800 dark:text-[#f4f4f5]",
      );
      c = c.replace(
        /text-slate-700(?!.*dark:)/g,
        "text-slate-700 dark:text-[#e4e4e7]",
      );
      c = c.replace(
        /text-slate-600(?!.*dark:)/g,
        "text-slate-600 dark:text-[#a1a1aa]",
      );
      c = c.replace(
        /text-slate-500(?!.*dark:)/g,
        "text-slate-500 dark:text-[#71717a]",
      );
      c = c.replace(
        /text-slate-400(?!.*dark:)/g,
        "text-slate-400 dark:text-[#71717a]",
      );

      c = c.replace(
        /bg-slate-100(?!.*dark:)/g,
        "bg-slate-100 dark:bg-[#27272a]/50",
      );
      c = c.replace(/bg-slate-50(?!.*dark:)/g, "bg-slate-50 dark:bg-[#18181b]");

      if (c !== original) {
        fs.writeFileSync(fn, c, "utf-8");
      }
    }
  }
}
fixSlateInRecruitment("src");
console.log("Fixed slate colors!");
