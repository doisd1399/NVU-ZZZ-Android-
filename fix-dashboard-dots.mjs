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

      c = c.replace(/bg-green-50 dark:bg-green-500\/100/g, "bg-green-500");
      c = c.replace(/bg-orange-50 dark:bg-orange-500\/100/g, "bg-orange-500");
      c = c.replace(/bg-red-50 dark:bg-red-500\/100/g, "bg-rose-500");
      c = c.replace(
        /<span className="font-bold text-sm">{/g,
        '<span className="font-bold text-sm text-gray-900 dark:text-[#fafafa]">{',
      );

      if (c !== original) {
        fs.writeFileSync(fn, c, "utf-8");
      }
    }
  }
}
fixDir("src");
