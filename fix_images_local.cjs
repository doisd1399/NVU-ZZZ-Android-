const fs = require("fs");
const path = require("path");

function walkDir(dir, callback) {
  fs.readdirSync(dir).forEach((f) => {
    let dirPath = path.join(dir, f);
    let isDirectory = fs.statSync(dirPath).isDirectory();
    isDirectory
      ? walkDir(dirPath, callback)
      : dirPath.endsWith(".tsx") && callback(path.join(dir, f));
  });
}

walkDir("src", (file) => {
  let content = fs.readFileSync(file, "utf8");
  let changed = false;

  content = content.replace(/<img([^>]*)>/g, (match, p1) => {
    if (match.includes("referrerPolicy")) return match;
    changed = true;
    return `<img${p1} referrerPolicy="no-referrer">`;
  });

  if (changed) {
    fs.writeFileSync(file, content);
    console.log("Fixed", file);
  }
});
