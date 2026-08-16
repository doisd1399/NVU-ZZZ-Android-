import fs from 'fs';

let content = fs.readFileSync('src/pages/RegisterCompany.tsx', 'utf8');

const selectHtml = `<select
                      required
                      value={formData.simulatorId}
                      onChange={(e) => {
                        const selected = activeSimulators.find((s:any) => s.id === e.target.value);
                        setFormData({
                          ...formData,
                          simulatorId: e.target.value,
                          simulatorName: selected?.name || "",
                        });
                      }}
                      className="w-full bg-white dark:bg-[#09090b] border border-slate-200 dark:border-[#2A2F3A] rounded-xl px-4 h-12 text-[15px] outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 dark:text-[#fafafa] transition-all shadow-sm appearance-none cursor-pointer"
                    >
                      <option value="" disabled>
                        Selecione...
                      </option>
                      {activeSimulators.map((sim:any) => (
                        <option key={sim.id} value={sim.id}>
                          {sim.name}
                        </option>
                      ))}
                    </select>`;

const newSelectHtml = `{activeSimulators.length === 0 ? (
                      <div className="w-full bg-slate-50 dark:bg-[#09090b]/50 border border-slate-200 dark:border-[#2A2F3A] rounded-xl px-4 h-12 flex items-center text-[15px] text-slate-500 dark:text-[#a1a1aa] shadow-none">
                        Nenhum simulador disponível no sistema.
                      </div>
                    ) : (
                      <select
                        required
                        value={formData.simulatorId}
                        onChange={(e) => {
                          const selected = activeSimulators.find((s:any) => s.id === e.target.value);
                          setFormData({
                            ...formData,
                            simulatorId: e.target.value,
                            simulatorName: selected?.name || "",
                          });
                        }}
                        className="w-full bg-white dark:bg-[#09090b] border border-slate-200 dark:border-[#2A2F3A] rounded-xl px-4 h-12 text-[15px] outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 dark:text-[#fafafa] transition-all shadow-sm appearance-none cursor-pointer"
                      >
                        <option value="" disabled>
                          Selecione...
                        </option>
                        {activeSimulators.map((sim:any) => (
                          <option key={sim.id} value={sim.id}>
                            {sim.name}
                          </option>
                        ))}
                      </select>
                    )}`;

content = content.replace(selectHtml, newSelectHtml);

const buttonHtml = `<Button
                    disabled={submitting}
                    type="submit"
                    className="w-full sm:w-2/3 bg-blue-600 hover:bg-blue-700 active:bg-blue-800 text-white rounded-xl h-12 text-[15px] font-bold shadow-sm transition-all"
                  >
                    {submitting ? "Enviando..." : "Enviar Solicitação"}
                  </Button>`;

const newButtonHtml = `<Button
                    disabled={submitting || !formData.simulatorId}
                    type="submit"
                    className="w-full sm:w-2/3 bg-blue-600 hover:bg-blue-700 active:bg-blue-800 text-white rounded-xl h-12 text-[15px] font-bold shadow-sm transition-all disabled:opacity-50"
                  >
                    {submitting ? "Enviando..." : "Enviar Solicitação"}
                  </Button>`;

content = content.replace(buttonHtml, newButtonHtml);
fs.writeFileSync('src/pages/RegisterCompany.tsx', content);
