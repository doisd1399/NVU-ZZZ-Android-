import fs from "fs";
let content = fs.readFileSync("src/pages/driver/Dashboard.tsx", "utf-8");

// Vehicle and Trailer cards: make background same as Card bg
content = content.replace(
  /className="text-sm sm:text-base font-semibold text-gray-900 dark:text-\[\#fafafa\] bg-\[\#fafafa\] dark:bg-\[\#18181b\] border border-gray-100 dark:border-\[\#2A2F3A\] hover:bg-gray-100 dark:hover:bg-\[\#27272a\] transition-colors px-3 py-1\.5 rounded-lg text-center flex-1 sm:flex-none sm:min-w-\[120px\] truncate cursor-pointer"/g,
  'className="text-sm sm:text-base font-semibold text-gray-900 dark:text-[#fafafa] bg-white dark:bg-[#1A1F26] border border-gray-100 dark:border-[#2A2F3A] hover:bg-gray-50 dark:hover:bg-[#27272a] shadow-sm dark:shadow-none transition-colors px-3 py-1.5 rounded-lg text-center flex-1 sm:flex-none sm:min-w-[120px] truncate cursor-pointer"',
);

// Aguardando Início control card:
content = content.replace(
  '<div className="flex items-center justify-center gap-2 px-3 py-2 text-gray-500 dark:text-[#a1a1aa] rounded-lg font-medium w-full sm:w-auto text-xs sm:text-sm bg-[#fafafa] dark:bg-[#18181b] border border-gray-100 dark:border-[#2A2F3A]">',
  '<div className="flex items-center justify-center gap-2 px-3 py-2 text-gray-500 dark:text-[#a1a1aa] rounded-lg font-medium w-full sm:w-auto text-xs sm:text-sm bg-white dark:bg-[#1A1F26] border border-gray-100 dark:border-[#2A2F3A] shadow-sm dark:shadow-none">',
);

fs.writeFileSync("src/pages/driver/Dashboard.tsx", content, "utf-8");
