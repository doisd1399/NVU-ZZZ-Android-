import fs from "fs";
import path from "path";

function optimizeClasses(content) {
  let c = content;

  // PREMIUM BACKGROUNDS (APP vs CARDS vs UI)
  // App Background
  c = c.replace(/bg-gray-50(?!.*dark:)/g, "bg-gray-50 dark:bg-[#09090b]"); // zinc-950
  c = c.replace(/dark:bg-\[#0a0a0b\]/g, "dark:bg-[#09090b]"); // standardize to zinc-950

  // Surfaces / Cards
  c = c.replace(/bg-white(?!.*dark:)/g, "bg-white dark:bg-[#18181b]"); // zinc-900
  c = c.replace(/dark:bg-\[#121213\]/g, "dark:bg-[#18181b]");
  c = c.replace(/dark:bg-\[#141415\]/g, "dark:bg-[#18181b]");

  // Secondary / Interactive Surfaces
  c = c.replace(/dark:bg-\[#1a1a1c\]/g, "dark:bg-[#27272a]"); // zinc-800
  c = c.replace(/dark:bg-\[#1e1e20\]/g, "dark:bg-[#27272a]");
  c = c.replace(/dark:bg-slate-800/g, "dark:bg-[#27272a]");

  // BORDERS
  c = c.replace(
    /border-gray-100(?!.*dark:)/g,
    "border-gray-100 dark:border-[#27272a]",
  );
  c = c.replace(
    /border-gray-200(?!.*dark:)/g,
    "border-gray-200 dark:border-[#3f3f46]",
  ); // zinc-700
  c = c.replace(
    /border-gray-300(?!.*dark:)/g,
    "border-gray-300 dark:border-[#52525b]",
  ); // zinc-600

  c = c.replace(/dark:border-\[#27272a\]\/50/g, "dark:border-[#27272a]");
  c = c.replace(/dark:border-\[#27272a\]\/60/g, "dark:border-[#27272a]");

  // TEXTS
  // Primary Text
  c = c.replace(
    /text-gray-900(?!.*dark:)/g,
    "text-gray-900 dark:text-[#fafafa]",
  ); // zinc-50
  c = c.replace(/dark:text-\[#f4f4f5\]/g, "dark:text-[#fafafa]");

  // Secondary Text
  c = c.replace(
    /text-gray-800(?!.*dark:)/g,
    "text-gray-800 dark:text-[#f4f4f5]",
  ); // zinc-100
  c = c.replace(
    /text-gray-700(?!.*dark:)/g,
    "text-gray-700 dark:text-[#e4e4e7]",
  ); // zinc-200
  c = c.replace(/dark:text-\[#d4d4d8\]/g, "dark:text-[#e4e4e7]");

  // Muted Text
  c = c.replace(
    /text-gray-600(?!.*dark:)/g,
    "text-gray-600 dark:text-[#a1a1aa]",
  ); // zinc-400
  c = c.replace(
    /text-gray-500(?!.*dark:)/g,
    "text-gray-500 dark:text-[#71717a]",
  ); // zinc-500

  // HOVER STATES
  c = c.replace(
    /hover:bg-gray-50(?!.*dark:)/g,
    "hover:bg-gray-50 dark:hover:bg-[#27272a]",
  );
  c = c.replace(
    /hover:bg-gray-100(?!.*dark:)/g,
    "hover:bg-gray-100 dark:hover:bg-[#3f3f46]",
  );
  c = c.replace(
    /hover:bg-gray-200(?!.*dark:)/g,
    "hover:bg-gray-200 dark:hover:bg-[#52525b]",
  );
  c = c.replace(/dark:hover:bg-\[#1e1e20\]/g, "dark:hover:bg-[#27272a]");
  c = c.replace(/dark:hover:bg-\[#27272a\]/g, "dark:hover:bg-[#3f3f46]");

  // HOVER TEXTS
  c = c.replace(
    /hover:text-gray-900(?!.*dark:)/g,
    "hover:text-gray-900 dark:hover:text-[#fafafa]",
  );
  c = c.replace(
    /hover:text-gray-700(?!.*dark:)/g,
    "hover:text-gray-700 dark:hover:text-[#e4e4e7]",
  );
  c = c.replace(/dark:hover:text-gray-100/g, "dark:hover:text-[#fafafa]");

  // BADGES & STATUSES (Blue, Green, Red, Yellow/Amber, Purple)
  const colors = [
    "blue",
    "green",
    "red",
    "amber",
    "purple",
    "indigo",
    "orange",
    "yellow",
  ];
  colors.forEach((color) => {
    // Backgrounds for badges
    c = c.replace(
      new RegExp(`bg-${color}-50(?!.*dark:)`, "g"),
      `bg-${color}-50 dark:bg-${color}-500/10`,
    );
    c = c.replace(
      new RegExp(`bg-${color}-100(?!.*dark:)`, "g"),
      `bg-${color}-100 dark:bg-${color}-500/20`,
    );

    // Soft transparent backgrounds handling
    c = c.replace(
      new RegExp(`dark:bg-${color}-900\\/20`, "g"),
      `dark:bg-${color}-500/10`,
    );
    c = c.replace(
      new RegExp(`dark:bg-${color}-500\\/10 dark:border([ a-zA-Z-0-9/]*)`, "g"),
      `dark:bg-${color}-500/10 dark:border$1`,
    );

    // Borders
    c = c.replace(
      new RegExp(`border-${color}-100(?!.*dark:)`, "g"),
      `border-${color}-100 dark:border-${color}-500/20`,
    );
    c = c.replace(
      new RegExp(`border-${color}-200(?!.*dark:)`, "g"),
      `border-${color}-200 dark:border-${color}-500/30`,
    );
    c = c.replace(
      new RegExp(`dark:border-${color}-900\\/40`, "g"),
      `dark:border-${color}-500/20`,
    );

    // Text colors
    c = c.replace(
      new RegExp(`text-${color}-900(?!.*dark:)`, "g"),
      `text-${color}-900 dark:text-${color}-300`,
    );
    c = c.replace(
      new RegExp(`text-${color}-800(?!.*dark:)`, "g"),
      `text-${color}-800 dark:text-${color}-400`,
    );
    c = c.replace(
      new RegExp(`text-${color}-700(?!.*dark:)`, "g"),
      `text-${color}-700 dark:text-${color}-400`,
    );
    c = c.replace(
      new RegExp(`text-${color}-600(?!.*dark:)`, "g"),
      `text-${color}-600 dark:text-${color}-400`,
    );

    // Some fixups
    c = c.replace(
      new RegExp(`dark:text-${color}-100`, "g"),
      `dark:text-${color}-300`,
    );
    c = c.replace(
      new RegExp(`dark:text-${color}-800`, "g"),
      `dark:text-${color}-400`,
    );
  });

  // SHADOWS
  c = c.replace(/shadow-sm dark:shadow-none/g, "shadow-sm dark:shadow-none");
  c = c.replace(/shadow-md dark:shadow-none/g, "shadow-md dark:shadow-none");
  c = c.replace(/shadow-lg dark:shadow-none/g, "shadow-lg dark:shadow-none");

  // If a shadow is present but has no dark:shadow counterpart, add it or leave it, but they should be soft.
  // Actually, light mode shadows: `shadow-sm`, `shadow-[0_2px_8px_-3px_rgba(0,0,0,0.05)]`

  // INPUTS & PROGRESS BARS
  c = c.replace(/dark:bg-\[#3f3f46\]/g, "dark:bg-[#27272a]"); // if it was mapped wrongly

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
      content = optimizeClasses(content);
      if (content !== original) {
        fs.writeFileSync(fullPath, content, "utf8");
      }
    }
  }
}

// Ensure global CSS gives proper body dark mode background
const cssPath = path.join("src", "index.css");
if (fs.existsSync(cssPath)) {
  let css = fs.readFileSync(cssPath, "utf8");
  if (
    css.includes(
      "@apply bg-gray-50 text-gray-900 dark:bg-slate-900 dark:text-gray-100;",
    )
  ) {
    css = css.replace(
      /@apply bg-gray-50 text-gray-900 dark:bg-slate-900 dark:text-gray-100;/g,
      "@apply bg-gray-50 text-gray-900 dark:bg-[#09090b] dark:text-[#fafafa];",
    );
    fs.writeFileSync(cssPath, css, "utf8");
  }
}

processDirectory(path.join(process.cwd(), "src"));
console.log("UI Overhaul Applied Successfully.");
