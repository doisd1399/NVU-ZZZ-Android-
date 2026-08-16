import fs from "fs";
import path from "path";

const allMatches = [];
function scan(dir) {
  for (const f of fs.readdirSync(dir)) {
    const fn = path.join(dir, f);
    if (fs.statSync(fn).isDirectory()) {
      scan(fn);
    } else if (fn.endsWith(".tsx") || fn.endsWith(".ts")) {
      const p = fs.readFileSync(fn, "utf-8");
      const m = p.match(/dark:[a-zA-Z0-9#/[\]-]+/g);
      if (m) allMatches.push(...m);
    }
  }
}
scan("src");
const counts = {};
for (const m of allMatches) counts[m] = (counts[m] || 0) + 1;
const sorted = Object.entries(counts).sort((a, b) => a[1] - b[1]);
for (const [k, v] of sorted.slice(-30)) {
  console.log(`${v} ${k}`);
}
