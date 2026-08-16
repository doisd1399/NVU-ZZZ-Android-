import re

def fix_driver():
    filepath = "src/components/DriverPerformanceCard.tsx"
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

    # Fix the missing % in XP display logic if any, but driver already binds XP and uses level.
    # Wait, the xp is already bound to {Math.floor(currentLevelXp)} in the JSX for Driver:
    # "{Math.floor(currentLevelXp)} / 1.000 XP"
    # But wait, wait! It should be currentLevelXp % 1000 so it doesn't show 15000 / 1.000 XP!
    content = content.replace('{Math.floor(currentLevelXp)} / 1.000 XP', '{Math.floor(currentLevelXp) % 1000} / 1.000 XP')

    with open(filepath, "w", encoding="utf-8") as f:
        f.write(content)
    print("Fixed Driver")

fix_driver()
