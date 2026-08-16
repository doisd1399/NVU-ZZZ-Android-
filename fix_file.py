import re

def fix(filepath):
    with open(filepath, "r", encoding="utf-8") as f:
        content = f.read()

    # Find the start of getStatus
    start = content.find("const getStatus = (score: number) => {")
    if start != -1:
        # We need to find the matching closing brace.
        # But we also have leftover code like `if (score >= 100)` etc.
        # Actually, let's just find the next `const status = getStatus(score);`
        end = content.find("const status = getStatus(score);", start)
        if end != -1:
            new_status = """const getStatus = (score: number) => {
    if (score >= 90)
      return {
        label: "Excelente",
        color: "text-emerald-600 dark:text-emerald-400",
        bg: "bg-emerald-500 dark:bg-emerald-400",
        index: 4,
        desc: (
          <>
            Ritmo de operação intenso e<br />
            altamente competitivo.
          </>
        ),
      };
    if (score >= 70)
      return {
        label: "Bom",
        color: "text-teal-600 dark:text-teal-400",
        bg: "bg-teal-500 dark:bg-teal-400",
        index: 3,
        desc: (
          <>
            Operação consistente com<br />
            bom volume de viagens.
          </>
        ),
      };
    if (score >= 50)
      return {
        label: "Regular",
        color: "text-yellow-600 dark:text-yellow-400",
        bg: "bg-yellow-500 dark:bg-yellow-400",
        index: 2,
        desc: (
          <>
            Ritmo moderado, há espaço<br />
            para acelerar.
          </>
        ),
      };
    return {
      label: "Abaixo do Esperado",
      color: "text-slate-500 dark:text-slate-400",
      bg: "bg-slate-400 dark:bg-slate-600",
      index: 1,
      desc: (
        <>
          Baixa atividade no<br />
          período selecionado.
        </>
      ),
    };
  };

  """
            content = content[:start] + new_status + content[end:]
            with open(filepath, "w", encoding="utf-8") as f:
                f.write(content)
            print(f"Fixed {filepath}")
        else:
            print(f"End not found in {filepath}")

fix("src/components/CompanyPerformanceCard.tsx")
fix("src/components/DriverPerformanceCard.tsx")
