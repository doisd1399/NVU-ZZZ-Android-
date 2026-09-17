import React from "react";
import { Gamepad2 } from "lucide-react";
import { useOperationalStore } from "../../context/AppContext";

export function SimulatorContextSelector() {
  const {
    simulators,
    simulatorsLoading,
    activeSimulatorId,
    availableCompanySimulatorIds,
    setActiveSimulatorId,
  } = useOperationalStore();

  const options = availableCompanySimulatorIds
    .map((id) => simulators.find((simulator) => simulator.id === id))
    .filter(Boolean) as Array<{ id: string; name: string }>;

  if (simulatorsLoading || options.length <= 1) return null;

  return (
    <label className="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-xs font-semibold text-slate-700 shadow-sm dark:border-[#2A2F3A] dark:bg-[#121214] dark:text-slate-200">
      <Gamepad2 size={14} className="text-blue-600 dark:text-blue-400" />
      <span className="hidden sm:inline">Simulador</span>
      <select
        aria-label="Simulador ativo"
        value={activeSimulatorId || ""}
        onChange={(event) => setActiveSimulatorId(event.target.value || null)}
        className="max-w-[130px] bg-transparent text-xs font-bold outline-none dark:bg-[#121214]"
      >
        {options.map((option) => (
          <option key={option.id} value={option.id}>
            {option.name}
          </option>
        ))}
      </select>
    </label>
  );
}

export default SimulatorContextSelector;
