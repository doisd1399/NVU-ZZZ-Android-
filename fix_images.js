const fs = require("fs");
const glob = require("glob");

const files = glob.sync("src/**/*.tsx");

files.forEach((file) => {
  let content = fs.readFileSync(file, "utf8");
  let changed = false;

  content = content.replace(
    /<img(.*?)src=\{([^}]+)\}(.*?)>/g,
    (match, p1, p2, p3) => {
      if (match.includes("referrerPolicy")) return match;
      changed = true;
      return `<img${p1}src={${p2}}${p3} referrerPolicy="no-referrer">`;
    },
  );

  // Also catch simple string src if any, but we mainly care about dynamic src
  if (changed) {
    fs.writeFileSync(file, content);
    console.log("Fixed", file);
  }
});
