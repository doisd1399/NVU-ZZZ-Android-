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
      ];

      for (const color of colors) {
        // match class names containing bg-{color}-50 and text-{color}-600 (or 700 or whatever)
        let regex = new RegExp(
          `className="([^"]*bg-${color}-50(?!0)[^"]*)"`,
          "g",
        );
        c = c.replace(regex, (match, classNames) => {
          if (
            !classNames.includes("dark:bg-") &&
            classNames.includes(`text-${color}-`)
          ) {
            return `className="${classNames.replace(`bg-${color}-50`, `bg-${color}-50 dark:bg-${color}-500/10`)}"`;
          }
          return match;
        });

        let textRegex = new RegExp(
          `className="([^"]*text-${color}-(600|700|800)[^"]*)"`,
          "g",
        );
        c = c.replace(textRegex, (match, classNames) => {
          if (
            !classNames.includes("dark:text-") &&
            classNames.includes(`bg-${color}-50`)
          ) {
            return `className="${classNames.replace(new RegExp(`text-${color}-(600|700|800)`), `text-${color}-$& dark:text-${color}-400`).replace(`text-${color}-text-${color}-`, `text-${color}-`)}"`;
          }
          return match;
        });
      }

      if (c !== original) {
        fs.writeFileSync(fn, c, "utf-8");
      }
    }
  }
}
fixDir("src");
