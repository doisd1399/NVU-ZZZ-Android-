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

  content = content.replace(
    /<img([^>]*) \/ referrerPolicy="no-referrer">/g,
    '<img$1 referrerPolicy="no-referrer" />',
  );
  content = content.replace(
    /<img([^>]*)\/ referrerPolicy="no-referrer">/g,
    '<img$1 referrerPolicy="no-referrer" />',
  );

  if (content !== fs.readFileSync(file, "utf8")) {
    fs.writeFileSync(file, content);
    console.log("Cleaned", file);
  }
});
