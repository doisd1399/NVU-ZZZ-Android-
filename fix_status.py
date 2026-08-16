import re

def fix_status():
    filepath = "src/components/CompanyPerformanceCard.tsx"
    with open(filepath, "r", encoding="utf-8") as f:
        content = f.read()

    # Find where status.desc is rendered
    desc_html = """                    {status.desc}
                  </span>"""
    
    # We will replace {status.desc} with a dynamic text
    # First, let's create the dynamic text based on currentCompanyPos and currentTotalDrivers.
    dynamic_percentile = """                    <>
                      {currentTotalDrivers > 1 ? (
                        <>
                          Desempenho superior a {Math.max(0, Math.round(((currentTotalDrivers - (currentCompanyPos >= 0 ? currentCompanyPos + 1 : 1)) / currentTotalDrivers) * 100))}%
                          <br />
                          das empresas neste período.
                        </>
                      ) : (
                        <>
                          Dados em análise
                          <br />
                          neste período.
                        </>
                      )}
                    </>"""
    
    if desc_html in content:
        content = content.replace(desc_html, dynamic_percentile + "\n                  </span>")
        print("Replaced desc in Company")
    else:
        print("desc not found in Company")
        
    with open(filepath, "w", encoding="utf-8") as f:
        f.write(content)

fix_status()
