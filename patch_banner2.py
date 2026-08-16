import re

with open("src/components/CompanyPerformanceCard.tsx", "r", encoding="utf-8") as f:
    content = f.read()

start_marker = "        {/* Banner */}"
end_marker = "        {/* KPIs Grid */}"

start_idx = content.find(start_marker)
end_idx = content.find(end_marker)

if start_idx == -1 or end_idx == -1:
    print("Could not find markers")
    exit(1)

banner_content = content[start_idx:end_idx]

# Fix my mistake
banner_content = banner_content.replace('className="text-[20px] sm:text-[26px] font-bold tracking-tight ${bannerTextColor} leading-none mb-1.5"', 'className={cn("text-[20px] sm:text-[26px] font-bold tracking-tight leading-none mb-1.5", bannerTextColor)}')
banner_content = banner_content.replace('className="text-[32px] sm:text-[40px] font-bold ${bannerTextColor} leading-none"', 'className={cn("text-[32px] sm:text-[40px] font-bold leading-none", bannerTextColor)}')
banner_content = banner_content.replace('className="text-[11px] sm:text-[13px] font-semibold ${bannerTextColor}"', 'className={cn("text-[11px] sm:text-[13px] font-semibold", bannerTextColor)}')

# Wait, check for any other literal ${bannerTextColor} strings
banner_content = banner_content.replace('className="`${bannerTextColor}', 'className={`${bannerTextColor}')

# And for text-teal-400 replacement that didn't work properly
banner_content = banner_content.replace('className="text-[11px] sm:text-[13px] font-semibold text-teal-400"', 'className={cn("text-[11px] sm:text-[13px] font-semibold", bannerTextColor)}')

new_content = content[:start_idx] + banner_content + content[end_idx:]

with open("src/components/CompanyPerformanceCard.tsx", "w", encoding="utf-8") as f:
    f.write(new_content)

print("Patched successfully")
