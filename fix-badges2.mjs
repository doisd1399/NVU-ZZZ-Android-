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

      const colors = [
        "blue",
        "green",
        "emerald",
        "orange",
        "red",
        "rose",
        "indigo",
        "purple",
        "amber",
        "yellow",
        "cyan",
        "sky",
      ];

      for (const color of colors) {
        c = c.replace(
          new RegExp(`bg-${color}-50(?!0\\/?)(?!.*dark:bg-)`, "g"),
          `bg-${color}-50 dark:bg-${color}-500/10`,
        );
        c = c.replace(
          new RegExp(`text-${color}-600(?!.*dark:text-)`, "g"),
          `text-${color}-600 dark:text-${color}-400`,
        );
        c = c.replace(
          new RegExp(`text-${color}-700(?!.*dark:text-)`, "g"),
          `text-${color}-700 dark:text-${color}-400`,
        );
        c = c.replace(
          new RegExp(`text-${color}-800(?!.*dark:text-)`, "g"),
          `text-${color}-800 dark:text-${color}-400`,
        );
      }

      if (c !== original) {
        fs.writeFileSync(fn, c, "utf-8");
      }
    }
  }
}
fixDir("src");
