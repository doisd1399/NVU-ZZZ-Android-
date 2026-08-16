import fs from "fs";
import path from "path";

function fixComposites(dir) {
  for (const f of fs.readdirSync(dir)) {
    const fn = path.join(dir, f);
    if (fs.statSync(fn).isDirectory()) {
      fixComposites(fn);
    } else if (fn.endsWith(".tsx") || fn.endsWith(".ts")) {
      let c = fs.readFileSync(fn, "utf-8");
      const original = c;

      c = c.replace(
        /dark:border-green-500\/20\/30/g,
        "dark:border-green-500/20",
      );
      c = c.replace(/dark:border-blue-500\/20\/40/g, "dark:border-blue-500/20");
      c = c.replace(/dark:border-blue-500\/20\/80/g, "dark:border-blue-500/40");
      c = c.replace(/dark:bg-\[#0a0a0b\]\/20/g, "dark:bg-transparent");
      c = c.replace(/dark:bg-\[#0a0a0b\]\/70/g, "dark:bg-[#0a0a0b]/80");

      // I also want to make sure the focus/hover classes are good
      c = c.replace(/dark:hover:bg-\[#141415\]/g, "dark:hover:bg-[#1e1e20]");
      c = c.replace(/dark:hover:bg-\[#0a0a0b\]/g, "dark:hover:bg-[#141415]");

      // Let's make shadows less strong in dark mode, wait we just can remove shadows or add dark:shadow-none
      const regexShadow = /shadow-[a-z]+/g;

      if (c !== original) {
        fs.writeFileSync(fn, c, "utf-8");
      }
    }
  }
}
fixComposites("src");
console.log("Fixed composites!");
