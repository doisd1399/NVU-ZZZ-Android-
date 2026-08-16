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

      let regex = /className="([^"]*bg-gray-100[^"]*)"/g;
      c = c.replace(regex, (match, classNames) => {
        if (!classNames.includes("dark:bg-")) {
          return `className="${classNames.replace("bg-gray-100", "bg-gray-100 dark:bg-[#27272a]")}"`;
        }
        return match;
      });

      regex = /className="([^"]*bg-gray-50[^"]*)"/g;
      c = c.replace(regex, (match, classNames) => {
        if (!classNames.includes("dark:bg-")) {
          return `className="${classNames.replace("bg-gray-50", "bg-gray-50 dark:bg-[#18181b]")}"`;
        }
        return match;
      });

      regex = /className="([^"]*bg-slate-50[^"]*)"/g;
      c = c.replace(regex, (match, classNames) => {
        if (!classNames.includes("dark:bg-")) {
          return `className="${classNames.replace("bg-slate-50", "bg-slate-50 dark:bg-[#18181b]")}"`;
        }
        return match;
      });

      regex = /className="([^"]*border-gray-200[^"]*)"/g;
      c = c.replace(regex, (match, classNames) => {
        if (!classNames.includes("dark:border-")) {
          return `className="${classNames.replace("border-gray-200", "border-gray-200 dark:border-[#3f3f46]")}"`;
        }
        return match;
      });

      regex = /className="([^"]*border-gray-100[^"]*)"/g;
      c = c.replace(regex, (match, classNames) => {
        if (!classNames.includes("dark:border-")) {
          return `className="${classNames.replace("border-gray-100", "border-gray-100 dark:border-[#27272a]")}"`;
        }
        return match;
      });

      // Shadow fixes. We want shadow-sm dark:shadow-none generally
      regex = /className="([^"]*shadow-sm[^"]*)"/g;
      c = c.replace(regex, (match, classNames) => {
        if (!classNames.includes("dark:shadow-none")) {
          return `className="${classNames.replace("shadow-sm", "shadow-sm dark:shadow-none")}"`;
        }
        return match;
      });

      if (c !== original) {
        fs.writeFileSync(fn, c, "utf-8");
      }
    }
  }
}
fixDir("src");
