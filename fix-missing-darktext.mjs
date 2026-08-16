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

      // Match text-gray-900 that doesn't have dark:text- immediately following or preceding it in the same className string
      let regex = /className="([^"]*text-gray-900[^"]*)"/g;
      c = c.replace(regex, (match, classNames) => {
        if (!classNames.includes("dark:text-")) {
          return `className="${classNames.replace("text-gray-900", "text-gray-900 dark:text-[#fafafa]")}"`;
        }
        return match;
      });

      regex = /className="([^"]*text-slate-900[^"]*)"/g;
      c = c.replace(regex, (match, classNames) => {
        if (!classNames.includes("dark:text-")) {
          return `className="${classNames.replace("text-slate-900", "text-slate-900 dark:text-[#fafafa]")}"`;
        }
        return match;
      });

      regex = /className="([^"]*text-gray-800[^"]*)"/g;
      c = c.replace(regex, (match, classNames) => {
        if (!classNames.includes("dark:text-")) {
          return `className="${classNames.replace("text-gray-800", "text-gray-800 dark:text-[#f4f4f5]")}"`;
        }
        return match;
      });

      regex = /className="([^"]*text-gray-700[^"]*)"/g;
      c = c.replace(regex, (match, classNames) => {
        if (!classNames.includes("dark:text-")) {
          return `className="${classNames.replace("text-gray-700", "text-gray-700 dark:text-[#d4d4d8]")}"`;
        }
        return match;
      });

      regex = /className="([^"]*text-gray-600[^"]*)"/g;
      c = c.replace(regex, (match, classNames) => {
        if (!classNames.includes("dark:text-")) {
          return `className="${classNames.replace("text-gray-600", "text-gray-600 dark:text-[#d4d4d8]")}"`;
        }
        return match;
      });

      regex = /className="([^"]*text-gray-500[^"]*)"/g;
      c = c.replace(regex, (match, classNames) => {
        if (!classNames.includes("dark:text-")) {
          return `className="${classNames.replace("text-gray-500", "text-gray-500 dark:text-[#a1a1aa]")}"`;
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
