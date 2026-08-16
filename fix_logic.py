import re

def fix_company():
    filepath = "src/components/CompanyPerformanceCard.tsx"
    with open(filepath, "r", encoding="utf-8") as f:
        content = f.read()

    # 1. Update metric calculations
    metrics_calc = """  const mediaDiaria = viagensRealizadas / runDays;

  const diasComViagem = new Set(currentTrips.map(t => new Date(t.metricDate).toDateString())).size;
  const constanciaScore = Math.min(100, Math.round((diasComViagem / runDays) * 100)) || 0;
  const expectedTrips = runDays * 2;
  const viagensScore = expectedTrips > 0 ? Math.min(100, Math.round((viagensRealizadas / expectedTrips) * 100)) : 0;
  const expectedEarnings = runDays * 200;
  const ganhosScore = expectedEarnings > 0 ? Math.min(100, Math.round((currentEarnings / expectedEarnings) * 100)) : 0;

  const scoreRaw = Math.round((constanciaScore + viagensScore + ganhosScore) / 3);
  const score = Math.round(scoreRaw);
  const displayScore = Math.min(100, score);"""
    
    content = re.sub(
        r'  const mediaDiaria = viagensRealizadas / runDays;\n\n  const scoreRaw = 15 > 0 \? \(viagensRealizadas / 15\) \* 100 : 0;\n  const score = Math\.round\(scoreRaw\);',
        metrics_calc,
        content
    )

    # 2. Update JSX bindings in banner
    # Replace ÍNDICE FINAL score binding
    content = content.replace('{score}', '{displayScore}')
    
    # Replace indicators
    content = content.replace('>92%<', '>{constanciaScore}%<')
    content = content.replace('>89%<', '>{viagensScore}%<')
    content = content.replace('>91%<', '>{ganhosScore}%<')

    # Replace XP and Level for company
    # calculate total XP based on trips and earnings
    xp_calc = """  const status = getStatus(score);
  const bannerTextColor = status.color.split(" ").find(c => c.startsWith("dark:"))?.replace("dark:", "") || status.color;
  const bannerBgColor = bannerTextColor.replace("text-", "bg-");

  const currentLevelXp = Math.floor(totalViagensGeral * 10 + prevEarnings * 0.1 + currentEarnings * 0.1);
  const currentLevel = Math.floor(currentLevelXp / 1000) + 1;
  const xpProgress = Math.min(100, ((currentLevelXp % 1000) / 1000) * 100);"""
    
    content = re.sub(
        r'  const status = getStatus\(score\);\n  const bannerTextColor = [^\n]+\n  const bannerBgColor = [^\n]+',
        xp_calc,
        content
    )

    content = content.replace('800 / 1.000 XP', '{currentLevelXp % 1000} / 1.000 XP')
    content = content.replace('>80%<', '>{Math.round(xpProgress)}%<')
    content = content.replace('style={{"width": \'80%\'}}', 'style={{width: `${Math.max(2, xpProgress)}%`}}')
    content = content.replace('>12<', '>{currentLevel}<')

    with open(filepath, "w", encoding="utf-8") as f:
        f.write(content)
    print("Fixed Company")

fix_company()
