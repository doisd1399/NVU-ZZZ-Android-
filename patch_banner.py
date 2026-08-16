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

# Replace text-teal-400
banner_content = banner_content.replace('className="text-teal-400"', 'className={bannerTextColor}')
banner_content = banner_content.replace('text-teal-400 leading-none', '${bannerTextColor} leading-none')
banner_content = banner_content.replace('className="text-teal-400 text-[14px]', 'className={`${bannerTextColor} text-[14px]')
banner_content = banner_content.replace('className="text-[20px] sm:text-[26px] font-bold tracking-tight text-teal-400 leading-none mb-1.5"', 'className={`text-[20px] sm:text-[26px] font-bold tracking-tight ${bannerTextColor} leading-none mb-1.5`}')
banner_content = banner_content.replace('className="text-[32px] sm:text-[40px] font-bold text-teal-400 leading-none"', 'className={`text-[32px] sm:text-[40px] font-bold ${bannerTextColor} leading-none`}')
banner_content = banner_content.replace('bg-teal-400', '${bannerBgColor}')
# also the inline styles for bg-teal-400
banner_content = banner_content.replace('className="h-full bg-teal-400 rounded-full"', 'className={`h-full ${bannerBgColor} rounded-full`}')

new_content = content[:start_idx] + banner_content + content[end_idx:]

with open("src/components/CompanyPerformanceCard.tsx", "w", encoding="utf-8") as f:
    f.write(new_content)

print("Patched successfully")
