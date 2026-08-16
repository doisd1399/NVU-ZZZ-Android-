import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const directoryPath = path.join(__dirname, "src");

const replacements = [
  {
    regex: /\bbg-white\b(?! dark:)/g,
    replacement: "bg-white dark:bg-slate-900",
  },
  {
    regex: /\btext-gray-900\b(?! dark:)/g,
    replacement: "text-gray-900 dark:text-gray-100",
  },
  {
    regex: /\btext-gray-800\b(?! dark:)/g,
    replacement: "text-gray-800 dark:text-gray-200",
  },
  {
    regex: /\btext-gray-700\b(?! dark:)/g,
    replacement: "text-gray-700 dark:text-gray-300",
  },
  {
    regex: /\btext-gray-600\b(?! dark:)/g,
    replacement: "text-gray-600 dark:text-gray-400",
  },
  {
    regex: /\btext-gray-500\b(?! dark:)/g,
    replacement: "text-gray-500 dark:text-gray-400",
  },
  {
    regex: /\bbg-gray-50\b(?! dark:)/g,
    replacement: "bg-gray-50 dark:bg-slate-800",
  },
  {
    regex: /\bbg-gray-100\b(?! dark:)/g,
    replacement: "bg-gray-100 dark:bg-slate-800/50",
  },
  {
    regex: /\bborder-gray-100\b(?! dark:)/g,
    replacement: "border-gray-100 dark:border-slate-800",
  },
  {
    regex: /\bborder-gray-200\b(?! dark:)/g,
    replacement: "border-gray-200 dark:border-slate-700",
  },
  {
    regex: /\bborder-gray-300\b(?! dark:)/g,
    replacement: "border-gray-300 dark:border-slate-600",
  },
  {
    regex: /\bh-14 md:h-16 bg-white border-b border-gray-100\b/g,
    replacement:
      "h-14 md:h-16 bg-white dark:bg-slate-900 border-b border-gray-100 dark:border-slate-800",
  },
  {
    regex: /\bbg-blue-50\b(?! dark:)/g,
    replacement: "bg-blue-50 dark:bg-blue-900/20",
  },
  {
    regex: /\border-blue-100\b(?! dark:)/g,
    replacement: "border-blue-100 dark:border-blue-900/40",
  },
  {
    regex: /\bbg-green-50\b(?! dark:)/g,
    replacement: "bg-green-50 dark:bg-green-900/20",
  },
  {
    regex: /\border-green-200\b(?! dark:)/g,
    replacement: "border-green-200 dark:border-green-900/40",
  },
  {
    regex: /\bbg-red-50\b(?! dark:)/g,
    replacement: "bg-red-50 dark:bg-red-900/20",
  },
  {
    regex: /\border-red-200\b(?! dark:)/g,
    replacement: "border-red-200 dark:border-red-900/40",
  },
  {
    regex: /\bbg-yellow-50\b(?! dark:)/g,
    replacement: "bg-yellow-50 dark:bg-yellow-900/20",
  },
  {
    regex: /\border-yellow-200\b(?! dark:)/g,
    replacement: "border-yellow-200 dark:border-yellow-900/40",
  },
  {
    regex: /\btext-red-900\b(?! dark:)/g,
    replacement: "text-red-900 dark:text-red-100",
  },
  {
    regex: /\btext-green-800\b(?! dark:)/g,
    replacement: "text-green-800 dark:text-green-100",
  },
  {
    regex: /\btext-green-900\b(?! dark:)/g,
    replacement: "text-green-900 dark:text-green-100",
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
        console.log(`Updated ${fullPath}`);
      }
    }
  }
}

processDirectory(directoryPath);
console.log("Class mapping complete!");
