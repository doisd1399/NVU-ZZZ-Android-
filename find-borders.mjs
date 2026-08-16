import fs from "fs";
import path from "path";

function findBorders() {
  const matches = {};
  function traverse(dir) {
    for (const f of fs.readdirSync(dir)) {
      const fn = path.join(dir, f);
      if (fs.statSync(fn).isDirectory()) {
        traverse(fn);
      } else if (fn.endsWith(".tsx") || fn.endsWith(".ts")) {
        let c = fs.readFileSync(fn, "utf-8");
        const found = c.match(/border-gray-[0-9]+/g);
        if (found) {
          for (const m of found) matches[m] = (matches[m] || 0) + 1;
        }

        // Also look for dark:border
        const d_found = c.match(/dark:border-[#a-zA-Z0-9]+/g);
        if (d_found) {
          for (const m of d_found) matches[m] = (matches[m] || 0) + 1;
        }
      }
    }
  }
  traverse("src");
  console.log(matches);
}
findBorders();
