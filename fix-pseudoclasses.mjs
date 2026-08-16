import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const directoryPath = path.join(__dirname, "src");

const replacements = [
  {
    regex: /hover:bg-white dark:bg-slate-900/g,
    replacement: "hover:bg-white dark:hover:bg-slate-900",
  },
  {
    regex: /hover:text-gray-900 dark:text-gray-100/g,
    replacement: "hover:text-gray-900 dark:hover:text-gray-100",
  },
  {
    regex: /hover:text-gray-800 dark:text-gray-200/g,
    replacement: "hover:text-gray-800 dark:hover:text-gray-200",
  },
  {
    regex: /hover:text-gray-700 dark:text-gray-300/g,
    replacement: "hover:text-gray-700 dark:hover:text-gray-300",
  },
  {
    regex: /hover:text-gray-600 dark:text-gray-400/g,
    replacement: "hover:text-gray-600 dark:hover:text-gray-400",
  },
  {
    regex: /hover:text-gray-500 dark:text-gray-400/g,
    replacement: "hover:text-gray-500 dark:hover:text-gray-400",
  },
  {
    regex: /hover:bg-gray-50 dark:bg-slate-800/g,
    replacement: "hover:bg-gray-50 dark:hover:bg-slate-800",
  },
  {
    regex: /hover:bg-gray-100 dark:bg-slate-800\/50/g,
    replacement: "hover:bg-gray-100 dark:hover:bg-slate-800/50",
  },

  {
    regex: /focus:bg-white dark:bg-slate-900/g,
    replacement: "focus:bg-white dark:focus:bg-slate-900",
  },
  {
    regex: /focus:bg-gray-50 dark:bg-slate-800/g,
    replacement: "focus:bg-gray-50 dark:focus:bg-slate-800",
  },
  {
    regex: /focus:border-gray-200 dark:border-slate-700/g,
    replacement: "focus:border-gray-200 dark:focus:border-slate-700",
  },
  {
    regex: /focus:border-gray-300 dark:border-slate-600/g,
    replacement: "focus:border-gray-300 dark:focus:border-slate-600",
  },
];

function processDirectory(dir) {
  const files = fs.readdirSync(dir);
  for (const file of files) {
    const fullPath = path.join(dir, file);
    if (fs.statSync(fullPath).isDirectory()) {
      processDirectory(fullPath);
    } else if (fullPath.endsWith(".tsx") || fullPath.endsWith(".ts")) {
      let content = fs.readFileSync(fullPath, "utf8");
      let modified = false;

      replacements.forEach(({ regex, replacement }) => {
        if (regex.test(content)) {
          content = content.replace(regex, replacement);
          modified = true;
        }
      });

      if (modified) {
        fs.writeFileSync(fullPath, content, "utf8");
        console.log(`Fixes applied to ${fullPath}`);
      }
    }
  }
}

processDirectory(directoryPath);
console.log("Hover/Focus fixes complete!");
