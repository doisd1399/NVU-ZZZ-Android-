import fs from "fs";
let content = fs.readFileSync("src/pages/driver/Dashboard.tsx", "utf-8");

// 1. Remove blur effect
content = content.replace(
  '<div className="absolute top-0 right-0 w-64 h-64 bg-green-50 dark:bg-green-500/10 dark:border-green-500/20 rounded-full blur-3xl -mr-20 -mt-20 opacity-50 pointer-events-none"></div>',
  "",
);

// 2. Change text
content = content.replace(
  '"Aguardando primeira entrega para iniciar"',
  '"Aguardando Início"',
);

// 3. compact controls for "Aguardando Início"
content = content.replace(
  '<div className="flex items-center justify-center gap-2 px-4 py-3 bg-gray-100 dark:bg-[#09090b] text-gray-500 dark:text-[#a1a1aa] rounded-xl font-medium w-full sm:w-auto h-12 text-sm sm:text-base">',
  '<div className="flex items-center justify-center gap-2 px-3 py-2 text-gray-500 dark:text-[#a1a1aa] rounded-lg font-medium w-full sm:w-auto text-xs sm:text-sm bg-[#fafafa] dark:bg-[#18181b] border border-gray-100 dark:border-[#2A2F3A]">',
);

// 4. Vehicle and Trailer gray background
content = content.replace(
  /className="text-sm sm:text-base font-semibold text-gray-900 dark:text-\[\#fafafa\] bg-gray-100 dark:bg-\[\#09090b\] border border-gray-200 dark:border-\[\#2A2F3A\] hover:bg-gray-100 dark:hover:bg-\[\#3f3f46\]\/50 transition-colors px-3 py-1\.5 rounded-lg text-center flex-1 sm:flex-none sm:min-w-\[120px\] truncate cursor-pointer"/g,
  'className="text-sm sm:text-base font-semibold text-gray-900 dark:text-[#fafafa] bg-[#fafafa] dark:bg-[#18181b] border border-gray-100 dark:border-[#2A2F3A] hover:bg-gray-100 dark:hover:bg-[#27272a] transition-colors px-3 py-1.5 rounded-lg text-center flex-1 sm:flex-none sm:min-w-[120px] truncate cursor-pointer"',
);

// active contract bg might be `#fafafa` or `#18181b` as "cinza".
fs.writeFileSync("src/pages/driver/Dashboard.tsx", content, "utf-8");
