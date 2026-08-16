import fs from "fs";
import path from "path";

function replaceInFiles(dir) {
  for (const f of fs.readdirSync(dir)) {
    const fn = path.join(dir, f);
    if (fs.statSync(fn).isDirectory()) {
      replaceInFiles(fn);
    } else if (fn.endsWith(".tsx") || fn.endsWith(".ts")) {
      let c = fs.readFileSync(fn, "utf-8");
      const original = c;

      // Global background -> darker `#0a0a0b`
      c = c.replace(/dark:bg-slate-900/g, "dark:bg-[#0a0a0b]");
      // Cards and panels -> slightly lighter `#141415`
      c = c.replace(/dark:bg-slate-800\/50/g, "dark:bg-[#121213]");
      c = c.replace(/dark:bg-slate-800/g, "dark:bg-[#141415]");

      // Borders -> `#27272a` (zinc-800)
      c = c.replace(/dark:border-slate-800\/50/g, "dark:border-[#27272a]/50");
      c = c.replace(/dark:border-slate-800/g, "dark:border-[#27272a]");
      c = c.replace(/dark:border-slate-700/g, "dark:border-[#3f3f46]");
      c = c.replace(/dark:border-slate-600/g, "dark:border-[#52525b]");

      // Texts
      c = c.replace(/dark:text-gray-400/g, "dark:text-[#a1a1aa]"); // zinc-400
      c = c.replace(/dark:text-gray-500/g, "dark:text-[#71717a]"); // zinc-500
      c = c.replace(/dark:text-gray-300/g, "dark:text-[#d4d4d8]"); // zinc-300
      c = c.replace(/dark:text-gray-200/g, "dark:text-[#e4e4e7]"); // zinc-200
      c = c.replace(/dark:text-gray-100/g, "dark:text-[#f4f4f5]"); // zinc-100

      c = c.replace(/dark:hover:text-gray-400/g, "dark:hover:text-[#a1a1aa]");
      c = c.replace(/dark:hover:text-gray-100/g, "dark:hover:text-[#f4f4f5]");
      c = c.replace(/dark:hover:text-gray-200/g, "dark:hover:text-[#e4e4e7]");
      c = c.replace(/dark:hover:text-gray-300/g, "dark:hover:text-[#d4d4d8]");

      // Hover backgrounds
      c = c.replace(
        /dark:hover:bg-slate-800\/50/g,
        "dark:hover:bg-[#1e1e20]/50",
      );
      c = c.replace(/dark:hover:bg-slate-800/g, "dark:hover:bg-[#1e1e20]");
      c = c.replace(/dark:hover:bg-slate-700/g, "dark:hover:bg-[#27272a]");
      c = c.replace(/dark:focus:bg-slate-900/g, "dark:focus:bg-[#0a0a0b]");
      c = c.replace(/dark:focus:bg-slate-800/g, "dark:focus:bg-[#141415]");

      // Colored backgrounds (badges, alerts)
      c = c.replace(
        /dark:bg-blue-900\/20/g,
        "dark:bg-blue-500/10 dark:border-blue-500/20",
      );
      c = c.replace(
        /dark:bg-green-900\/20/g,
        "dark:bg-green-500/10 dark:border-green-500/20",
      );
      c = c.replace(
        /dark:bg-red-900\/20/g,
        "dark:bg-red-500/10 dark:border-red-500/20",
      );
      c = c.replace(
        /dark:bg-yellow-900\/20/g,
        "dark:bg-amber-500/10 dark:border-amber-500/20",
      );

      c = c.replace(/dark:border-blue-900\/40/g, "dark:border-blue-500/20");
      c = c.replace(/dark:border-green-900\/40/g, "dark:border-green-500/20");
      c = c.replace(/dark:border-red-900\/40/g, "dark:border-red-500/20");
      c = c.replace(/dark:border-yellow-900\/40/g, "dark:border-amber-500/20");

      // Colored text
      c = c.replace(/dark:text-blue-[0-9]+/g, "dark:text-blue-400");
      c = c.replace(/dark:text-green-[0-9]+/g, "dark:text-green-400");
      c = c.replace(/dark:text-red-[0-9]+/g, "dark:text-red-400");
      c = c.replace(/dark:text-yellow-[0-9]+/g, "dark:text-amber-400");

      // Buttons that might be explicitly styled
      // Let's make primary buttons pop more if they have dark classes

      if (c !== original) {
        fs.writeFileSync(fn, c, "utf-8");
      }
    }
  }
}
replaceInFiles("src");
console.log("Premium dark mode applied!");
