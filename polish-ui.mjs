import fs from "fs";
import path from "path";

function fixResidualBordersAndColors(content) {
  let c = content;

  // Clean up any double or conflicting dark utility classes
  c = c.replace(
    /dark:bg-\[#0a0a0b\] dark:bg-\[#09090b\]/g,
    "dark:bg-[#09090b]",
  );
  c = c.replace(/dark:bg-slate-900 dark:bg-\[#09090b\]/g, "dark:bg-[#09090b]");
  c = c.replace(
    /dark:bg-\[#121213\] dark:bg-\[#141415\]/g,
    "dark:bg-[#18181b]",
  );
  c = c.replace(/dark:bg-\[#141415\]/g, "dark:bg-[#18181b]");
  c = c.replace(/dark:bg-\[#121213\]/g, "dark:bg-[#18181b]");
  c = c.replace(/dark:bg-slate-800/g, "dark:bg-[#18181b]"); // In case we missed any
  c = c.replace(
    /dark:border-\[#3f3f46\] dark:border-\[#27272a\]/g,
    "dark:border-[#27272a]",
  ); // conflict

  // Refine card/modal borders
  c = c.replace(
    /border-gray-100 dark:border-\[#27272a\]/g,
    "border-gray-100 dark:border-[#27272a]/70",
  );

  // Make inputs/selects specifically inside dark mode look inset and smooth
  // Look for `bg-gray-50 dark:bg-[#09090b] border`
  c = c.replace(
    /bg-gray-50 dark:bg-\[#09090b\] border border-gray-200 dark:border-\[#3f3f46\]/g,
    "bg-gray-50 dark:bg-[#09090b] border border-gray-200 dark:border-[#27272a]/80 shadow-sm dark:shadow-inner",
  );

  // The dark mode inputs might look better slightly lighter than app bg (#09090b) -> let's use #18181b for container inputs
  c = c.replace(
    /bg-gray-50 dark:bg-\[#09090b\] rounded-xl px-4 py-3/g,
    "bg-gray-50 dark:bg-[#18181b] rounded-xl px-4 py-3",
  );

  // Progress bars background
  c = c.replace(
    /bg-gray-200\/70 dark:bg-\[#27272a\]/g,
    "bg-gray-100 dark:bg-white/10",
  );
  c = c.replace(
    /bg-gray-200 dark:bg-\[#27272a\]/g,
    "bg-gray-200 dark:bg-white/10",
  );
  c = c.replace(
    /bg-gray-200 dark:bg-\[#3f3f46\]/g,
    "bg-gray-200 dark:bg-white/10",
  );

  // Primary buttons (blue) to look sharper
  c = c.replace(
    /bg-blue-600 hover:bg-blue-700 dark:bg-blue-600 dark:hover:bg-blue-500/g,
    "bg-blue-600 hover:bg-blue-700 dark:bg-blue-600 dark:hover:bg-blue-500 shadow-sm",
  );

  // Fix weird badge borders where `border ` (with trailing space) was produced
  c = c.replace(
    /dark:bg-blue-500\/10 dark:border dark:border-blue-500\/20/g,
    "dark:bg-blue-500/10 border border-transparent dark:border-blue-500/20",
  );
  c = c.replace(
    /dark:bg-green-500\/10 dark:border dark:border-green-500\/20/g,
    "dark:bg-green-500/10 border border-transparent dark:border-green-500/20",
  );
  c = c.replace(
    /dark:bg-red-500\/10 dark:border dark:border-red-500\/20/g,
    "dark:bg-red-500/10 border border-transparent dark:border-red-500/20",
  );
  c = c.replace(
    /dark:bg-amber-500\/10 dark:border dark:border-amber-500\/20/g,
    "dark:bg-amber-500/10 border border-transparent dark:border-amber-500/20",
  );

  // Any raw colors needing alignment
  c = c.replace(/dark:text-\[#f4f4f5\]/g, "dark:text-[#fafafa]"); // standardize zinc-50
  c = c.replace(/dark:text-\[#e4e4e7\]/g, "dark:text-[#d4d4d8]"); // zinc-300 for secondary text
  c = c.replace(/dark:text-\[#a1a1aa\]/g, "dark:text-[#a1a1aa]"); // zinc-400 for tertiary text
  c = c.replace(/dark:text-\[#71717a\]/g, "dark:text-[#71717a]"); // zinc-500

  return c;
}

function processDirectory(dir) {
  const files = fs.readdirSync(dir);
  for (const file of files) {
    const fullPath = path.join(dir, file);
    if (fs.statSync(fullPath).isDirectory()) {
      processDirectory(fullPath);
    } else if (fullPath.endsWith(".tsx") || fullPath.endsWith(".ts")) {
      let content = fs.readFileSync(fullPath, "utf8");
      const original = content;
      content = fixResidualBordersAndColors(content);
      if (content !== original) {
        fs.writeFileSync(fullPath, content, "utf8");
      }
    }
  }
}

processDirectory(path.join(process.cwd(), "src"));
console.log("Polishing complete.");
