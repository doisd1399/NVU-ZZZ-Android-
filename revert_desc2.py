import re

def revert_desc(filepath):
    with open(filepath, "r", encoding="utf-8") as f:
        content = f.read()

    # Find <span className="text-[8px] sm:text-[14px] text-white/80 font-medium leading-[1.2] sm:leading-[1.3] max-w-[140px] sm:max-w-[280px]">
    # And replace everything until </span>
    
    match = re.search(r'(<span className="text-\[8px\] sm:text-\[14px\] text-white/80 font-medium leading-\[1\.2\] sm:leading-\[1\.3\] max-w-\[140px\] sm:max-w-\[280px\]">)(.*?)(</span>)', content, re.DOTALL)
    if match:
        content = content[:match.start()] + match.group(1) + "\n                    {status.desc}\n                  " + match.group(3) + content[match.end():]
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Reverted desc in {filepath}")
    else:
        print(f"desc block not found in {filepath}")


revert_desc("src/components/CompanyPerformanceCard.tsx")
revert_desc("src/components/DriverPerformanceCard.tsx")
