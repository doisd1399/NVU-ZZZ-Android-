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

      // Cards should be #18181b in dark mode so they sit above #09090b
      c = c.replace(
        /Card className="(.*)bg-white dark:bg-\[#09090b\](.*)"/g,
        'Card className="$1bg-white dark:bg-[#18181b]$2"',
      );
      c = c.replace(
        /className="(.*)bg-white dark:bg-\[#09090b\](.*)shadow(.*)"/g,
        'className="$1bg-white dark:bg-[#18181b]$2shadow$3"',
      );

      // Update any component with rounded & bg-white to be elevated if it has shadow
      c = c.replace(
        /bg-white dark:bg-\[#09090b\] rounded-(xl|2xl|3xl|lg) border/g,
        "bg-white dark:bg-[#18181b] rounded-$1 border",
      );

      if (c !== original) {
        fs.writeFileSync(fn, c, "utf-8");
      }
    }
  }
}
fixDir("src");
console.log("Fixed depth!");
