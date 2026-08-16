import { finalValueCompatibilityIssue, parsePositiveNumber } from "../functions/src/gtoMoney";

function require(condition: unknown, message: string): void {
  if (!condition) throw new Error(message);
}

require(parsePositiveNumber("R$ 5.300,00") === 5300, "5.300,00 must parse as 5300");
require(parsePositiveNumber("R$ 5.300") === 5300, "5.300 must parse as 5300");
require(parsePositiveNumber("R$ 5300,00") === 5300, "5300,00 must parse as 5300");
require(parsePositiveNumber("R$ 5300.50") === 5300.5, "5300.50 must preserve cents");
require(parsePositiveNumber("R$ 5.300,50") === 5300.5, "5.300,50 must preserve cents");
require(finalValueCompatibilityIssue(5300, 530000) !== null, "100x corruption must be rejected");
require(finalValueCompatibilityIssue(5300, 5300) === null, "normal payout must be accepted");
require(finalValueCompatibilityIssue(5300, 10600) === null, "2x value is not rejected by broad corruption guard");
console.log("GtoMoney backend helper: PASS");
