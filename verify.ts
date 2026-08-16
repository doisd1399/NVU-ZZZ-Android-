import { getFilteredTrips } from './src/lib/metricsEngine';
import fs from 'fs';

// Simulação de timezone BRT (UTC-3)
// No node local (se estiver em UTC), o new Date("2026-06-01") gera 2026-06-01T00:00:00Z
// E new Date(2026, 5, 1) gera 2026-06-01T00:00:00.000 (Local).
// Como estou rodando num ambiente onde posso não estar em BRT, farei a conta matematicamente.

// mock de trips
const trips = [];

// Gerando 1 viagem por dia ao meio dia (local)
for (let d = 31; d <= 31; d++) { // maio
  trips.push({
    id: `trip-05-${d}`,
    normalizedDataJob: new Date(2026, 4, d, 12, 0, 0).getTime(), // 31/05 12:00
    normalizedValor: 100,
    empresaId: 'dc',
    simulator: 'sim1',
    motoristaId: 'mot1'
  });
}
for (let d = 1; d <= 30; d++) { // junho
  trips.push({
    id: `trip-06-${d}`,
    normalizedDataJob: new Date(2026, 5, d, 12, 0, 0).getTime(), // 01/06 a 30/06 12:00
    normalizedValor: 100,
    empresaId: 'dc',
    simulator: 'sim1',
    motoristaId: 'mot1'
  });
}

// Histórico
const histStart = new Date(2026, 5, 1, 0, 0, 0); // 01/06
const histEnd = new Date(2026, 5, 30, 23, 59, 59, 999); // 30/06

const histTrips = trips.filter(t => t.normalizedDataJob >= histStart.getTime() && t.normalizedDataJob <= histEnd.getTime());

// Ranking
// "2026-06-01" parsed into new Date() -> if we are UTC-3, this is 2026-05-31T21:00:00.000 Local
const rankStartParsed = new Date(2026, 4, 31, 21, 0, 0);
const rankStart = new Date(rankStartParsed.getFullYear(), rankStartParsed.getMonth(), rankStartParsed.getDate(), 0, 0, 0, 0); // 31/05/2026 00:00

const rankEndParsed = new Date(2026, 5, 29, 21, 0, 0); // "2026-06-30" is 29/06 21:00
const rankEnd = new Date(rankEndParsed.getFullYear(), rankEndParsed.getMonth(), rankEndParsed.getDate(), 23, 59, 59, 999); // 29/06/2026 23:59:59

const rankTrips = trips.filter(t => t.normalizedDataJob >= rankStart.getTime() && t.normalizedDataJob <= rankEnd.getTime());

let report = `Data | Qtd Histórico | Qtd Ranking | Diferença | Valor Histórico | Valor Ranking\n`;
report += `--- | --- | --- | --- | --- | ---\n`;

const dates = ['31/05/2026'];
for(let i=1; i<=30; i++) dates.push(`${i.toString().padStart(2, '0')}/06/2026`);

const getDateStr = (t) => {
  const d = new Date(t);
  return `${d.getDate().toString().padStart(2, '0')}/${(d.getMonth()+1).toString().padStart(2, '0')}/${d.getFullYear()}`;
}

let histTotal = 0;
let rankTotal = 0;

for (const dStr of dates) {
  const h = histTrips.filter(t => getDateStr(t.normalizedDataJob) === dStr);
  const r = rankTrips.filter(t => getDateStr(t.normalizedDataJob) === dStr);
  const diff = h.length - r.length;
  histTotal += h.length;
  rankTotal += r.length;
  if(h.length > 0 || r.length > 0) {
    report += `${dStr} | ${h.length} | ${r.length} | ${diff} | R$ ${(h.length*100).toFixed(2)} | R$ ${(r.length*100).toFixed(2)}\n`;
  }
}

report += `\nTotal Histórico: ${histTotal}\nTotal Ranking: ${rankTotal}\nDiferença: ${histTotal - rankTotal}\n`;
console.log(report);
fs.writeFileSync('proof.txt', report);
