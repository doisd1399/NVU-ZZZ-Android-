import re

def fix_company():
    filepath = "src/components/CompanyPerformanceCard.tsx"
    with open(filepath, "r", encoding="utf-8") as f:
        content = f.read()

    # 1. Fix max Dias/Viagens/Ganhos
    old_max = """  let maxDias = 1;
  let maxViagens = 1;
  let maxGanhos = 1;"""

    new_max = """  let maxDias = Math.max(1, Math.ceil(runDays * 0.5));
  let maxViagens = Math.max(1, runDays * 1);
  let maxGanhos = Math.max(1, runDays * 100);"""

    if old_max in content:
        content = content.replace(old_max, new_max)
    else:
        print("max logic not found in Company")

    # 2. Fix getStatus
    old_status = """  const getStatus = (score: number) => {
    if (score >= 120)
      return {
        label: "Extraordinário",
        color: "text-teal-600 dark:text-teal-400",
        bg: "bg-teal-500 dark:bg-teal-400",
        index: 5,
        desc: (
          <>
            Desempenho acima de 95%
            <br />
            das empresas neste período.
          </>
        ),
      };
    if (score >= 100)
      return {
        label: "Excelente",
        color: "text-emerald-600 dark:text-emerald-400",
        bg: "bg-emerald-500 dark:bg-emerald-400",
        index: 4,
        desc: (
          <>
            Desempenho acima de 82%
            <br />
            das empresas neste período.
          </>
        ),
      };
    if (score >= 80)
      return {
        label: "Bom",
        color: "text-pink-600 dark:text-pink-400",
        bg: "bg-pink-500 dark:bg-pink-400",
        index: 3,
        desc: (
          <>
            Desempenho acima de 60%
            <br />
            das empresas neste período.
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
            Desempenho acima de 35%
            <br />
            das empresas neste período.
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
          Baixa atividade no
          <br />
          período selecionado.
        </>
      ),
    };
  };"""
    
    # Check if there are other exact texts
    # The current content might have a replaced desc since we patched it earlier, wait!
    # I already patched `status.desc` in an earlier step! It might not match this exact string.
    
    # Let's use regex to replace the whole getStatus block
    match = re.search(r'const getStatus = \(score: number\) => \{.*?\};\n', content, re.DOTALL)
    if match:
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
  };\n"""
        content = content[:match.start()] + new_status + content[match.end():]
    else:
        print("getStatus block not found in Company")

    # 3. Fix XP Level
    old_xp = """  const currentLevelXp = Math.floor(totalViagensGeral * 10 + prevEarnings * 0.1 + currentEarnings * 0.1);"""
    new_xp = """  const totalEarningsAllTime = companyTrips.reduce((acc, t) => acc + t.normalizedValor, 0);
  const currentLevelXp = Math.floor(totalViagensGeral * 10 + totalEarningsAllTime * 0.1);"""
    if old_xp in content:
        content = content.replace(old_xp, new_xp)
    else:
        print("XP logic not found in Company")

    with open(filepath, "w", encoding="utf-8") as f:
        f.write(content)
    print("Patched Company")

def fix_driver():
    filepath = "src/components/DriverPerformanceCard.tsx"
    with open(filepath, "r", encoding="utf-8") as f:
        content = f.read()

    # 1. Fix max Dias/Viagens/Ganhos
    old_max = """  let maxDias = 1;
  let maxViagens = 1;
  let maxGanhos = 1;"""

    new_max = """  let maxDias = Math.max(1, Math.ceil(runDays * 0.5));
  let maxViagens = Math.max(1, runDays * 1);
  let maxGanhos = Math.max(1, runDays * 100);"""

    if old_max in content:
        content = content.replace(old_max, new_max)
    else:
        print("max logic not found in Driver")

    match = re.search(r'const getStatus = \(score: number\) => \{.*?\};\n', content, re.DOTALL)
    if match:
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
  };\n"""
        content = content[:match.start()] + new_status + content[match.end():]
    else:
        print("getStatus block not found in Driver")

    old_xp = """  const currentLevelXp = Math.floor(totalViagensGeral * 10 + prevEarnings * 0.1 + currentEarnings * 0.1);"""
    new_xp = """  const totalEarningsAllTime = userTrips.reduce((acc, t) => acc + t.normalizedValor, 0);
  const currentLevelXp = Math.floor(totalViagensGeral * 10 + totalEarningsAllTime * 0.1);"""
    if old_xp in content:
        content = content.replace(old_xp, new_xp)
    else:
        print("XP logic not found in Driver")

    with open(filepath, "w", encoding="utf-8") as f:
        f.write(content)
    print("Patched Driver")


fix_company()
fix_driver()
