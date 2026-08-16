import re

def revert_desc(filepath):
    with open(filepath, "r", encoding="utf-8") as f:
        content = f.read()

    # We need to find the block:
    #                     <>
    #                       {currentTotalDrivers > 1 ? (
    #                         <>
    #                           Desempenho superior a {Math.max(0, Math.round(((currentTotalDrivers - (currentCompanyPos >= 0 ? currentCompanyPos + 1 : 1)) / currentTotalDrivers) * 100))}%
    #                           <br />
    #                           das empresas neste período.
    #                         </>
    #                       ) : (
    #                         <>
    #                           Dados em análise
    #                           <br />
    #                           neste período.
    #                         </>
    #                       )}
    #                     </>

    # Let's use a simpler string replace since it's hard to match exact whitespace.
    # We can match from "                    <>\n                      {currentTotalDrivers > 1" up to "                      )}\n                    </>"
    
    match = re.search(r'                    <>\n\s*\{currentTotalDrivers > 1 \? \([\s\S]*?\}\)\n\s*</>', content)
    if match:
        content = content[:match.start()] + "                    {status.desc}" + content[match.end():]
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Reverted desc in {filepath}")
    else:
        print(f"desc block not found in {filepath}")


revert_desc("src/components/CompanyPerformanceCard.tsx")
revert_desc("src/components/DriverPerformanceCard.tsx")
