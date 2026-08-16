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

      c = c.replace(/bg-gray-900 dark:bg-\[#00D1FF\]/g, "bg-[#00D1FF]");

      if (c !== original) {
        fs.writeFileSync(fn, c, "utf-8");
      }
    }
  }
}
fixDir("src");
