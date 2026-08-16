import re

with open("src/components/CompanyPerformanceCard.tsx", "r", encoding="utf-8") as f:
    content = f.read()

start_marker = "        {/* Banner */}"
end_marker = "        {/* KPIs Grid */}"

start_idx = content.find(start_marker)
end_idx = content.find(end_marker)

if start_idx == -1 or end_idx == -1:
    print("Could not find markers")
    exit(1)

new_banner = """        {/* Banner */}
        <div className="relative w-full bg-[#031c22] rounded-[20px] text-white mb-4 shadow-sm overflow-hidden">
          {/* Backgrounds container with overflow-hidden */}
          <div className="absolute inset-0 rounded-[20px] overflow-hidden pointer-events-none">
            {/* Glows and particles */}
            <div className="absolute top-[-30%] left-[-10%] w-[80%] h-[150%] bg-[radial-gradient(circle_at_center,_var(--tw-gradient-stops))] from-teal-400/10 via-transparent to-transparent pointer-events-none" />
            <div className="absolute bottom-[-50%] right-[-10%] w-[80%] h-[120%] bg-[radial-gradient(circle_at_center,_var(--tw-gradient-stops))] from-emerald-500/10 via-transparent to-transparent pointer-events-none" />

            {/* Fluid lines */}
            <svg
              className="absolute bottom-0 w-full h-auto opacity-40 pointer-events-none mix-blend-screen"
              viewBox="0 0 1440 200"
              fill="none"
              preserveAspectRatio="none"
            >
              <path
                d="M0,200 C320,50 420,150 720,80 C1020,10 1200,120 1440,60 L1440,200 L0,200 Z"
                fill="url(#fluid-grad)"
              />
              <path
                d="M0,200 C200,100 400,20 720,90 C1040,160 1240,40 1440,80 L1440,200 L0,200 Z"
                fill="url(#fluid-grad-2)"
                opacity="0.5"
              />
              <defs>
                <linearGradient id="fluid-grad" x1="0" y1="0" x2="1" y2="0">
                  <stop offset="0%" stopColor="#14B8A6" stopOpacity="0" />
                  <stop offset="50%" stopColor="#14B8A6" stopOpacity="0.4" />
                  <stop offset="100%" stopColor="#0ea5e9" stopOpacity="0" />
                </linearGradient>
                <linearGradient id="fluid-grad-2" x1="1" y1="0" x2="0" y2="0">
                  <stop offset="0%" stopColor="#10b981" stopOpacity="0" />
                  <stop offset="50%" stopColor="#10b981" stopOpacity="0.3" />
                  <stop offset="100%" stopColor="#06b6d4" stopOpacity="0" />
                </linearGradient>
              </defs>
            </svg>
          </div>

          {/* Banner Content */}
          <div className="flex flex-col w-full p-4 sm:p-5 md:p-6 relative z-10 gap-5 sm:gap-6">
            {/* Header: Title and Period Select */}
            <div className="flex flex-row justify-between items-center w-full gap-2">
              <h3 className="text-[12px] sm:text-[14px] font-bold text-white uppercase tracking-wider shrink-0">
                1. RITMO OPERACIONAL
              </h3>

              <div className="relative" ref={periodSelectorRef}>
                <div
                  onClick={() => setIsPeriodSelectorOpen(!isPeriodSelectorOpen)}
                  className="flex items-center gap-1.5 sm:gap-2 bg-white/5 hover:bg-white/10 transition-colors border border-white/10 rounded-full px-3 py-1.5 sm:px-4 sm:py-2 cursor-pointer backdrop-blur-md shrink-0"
                >
                  <svg
                    width="14"
                    height="14"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    className="text-white/80"
                  >
                    <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
                    <line x1="16" y1="2" x2="16" y2="6"></line>
                    <line x1="8" y1="2" x2="8" y2="6"></line>
                    <line x1="3" y1="10" x2="21" y2="10"></line>
                  </svg>
                  <span className="text-[11px] sm:text-[13px] font-medium text-white/90">
                    Período: {getPeriodLabel()}
                  </span>
                  <svg
                    width="14"
                    height="14"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    className={cn(
                      "text-white/60 ml-1 transition-transform",
                      isPeriodSelectorOpen && "rotate-180",
                    )}
                  >
                    <polyline points="6 9 12 15 18 9"></polyline>
                  </svg>
                </div>

                {isPeriodSelectorOpen && (
                  <div className="absolute right-0 top-full mt-2 w-64 sm:w-72 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl shadow-xl z-50 overflow-hidden">
                    <div className="p-1">
                      {[
                        "Semana atual",
                        "Mês atual",
                        "Hoje",
                        "Personalizado",
                      ].map((period) => (
                        <button
                          key={period}
                          onClick={() => handlePeriodSelect(period as any)}
                          className={cn(
                            "w-full text-left px-3 py-2 text-sm font-medium rounded-lg transition-colors",
                            selectedPeriod === period
                              ? "bg-teal-50 dark:bg-teal-900/20 text-teal-700 dark:text-teal-400"
                              : "text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800",
                          )}
                        >
                          {period}
                        </button>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            </div>

            {/* Middle Row: Status and Índice Final */}
            <div className="flex flex-row items-center justify-between">
              {/* Left: Circle + Status */}
              <div className="flex items-center gap-3 sm:gap-4 flex-1">
                <div className="relative w-[52px] h-[52px] sm:w-[64px] sm:h-[64px] rounded-full border border-teal-500/30 flex items-center justify-center bg-teal-500/10 shrink-0">
                  <ArrowUpRight size={28} className="text-teal-400" strokeWidth={3} />
                </div>
                <div className="flex flex-col">
                  <span className="text-[20px] sm:text-[26px] font-bold tracking-tight text-teal-400 leading-none mb-1.5">
                    {status.label}
                  </span>
                  <span className="text-[12px] sm:text-[14px] text-white/80 font-medium leading-[1.3] max-w-[200px] sm:max-w-[280px]">
                    {status.desc}
                  </span>
                </div>
              </div>

              {/* Separator */}
              <div className="hidden sm:block w-px h-12 bg-white/10 mx-4"></div>

              {/* Right: Índice Final */}
              <div className="flex flex-col items-end sm:items-start shrink-0">
                <span className="text-[10px] sm:text-[11px] text-white/60 font-medium tracking-wider uppercase mb-1">
                  ÍNDICE FINAL
                </span>
                <div className="flex items-baseline gap-1">
                  <span className="text-[32px] sm:text-[40px] font-bold text-teal-400 leading-none">
                    {score}
                  </span>
                  <span className="text-[14px] sm:text-[18px] text-white/60 font-medium">
                    /100
                  </span>
                </div>
              </div>
            </div>

            {/* Indicators Row */}
            <div className="flex flex-row items-center justify-between sm:justify-start gap-4 sm:gap-10 mt-1">
              <div className="flex items-center gap-2 sm:gap-3">
                <div className="w-8 h-8 sm:w-10 sm:h-10 rounded-full border border-teal-500/30 flex items-center justify-center bg-teal-500/10">
                   <CalendarCheck size={18} className="text-teal-400" strokeWidth={2} />
                </div>
                <div className="flex flex-col">
                  <span className="text-[9px] sm:text-[10px] text-white/60 font-medium tracking-wider uppercase leading-none mb-1">Constância</span>
                  <span className="text-[16px] sm:text-[18px] font-bold text-white leading-none">92%</span>
                </div>
              </div>
              <div className="hidden sm:block w-px h-8 bg-white/10"></div>
              <div className="flex items-center gap-2 sm:gap-3">
                <div className="w-8 h-8 sm:w-10 sm:h-10 rounded-full border border-teal-500/30 flex items-center justify-center bg-teal-500/10">
                   <TrendingUp size={18} className="text-teal-400" strokeWidth={2.5} />
                </div>
                <div className="flex flex-col">
                  <span className="text-[9px] sm:text-[10px] text-white/60 font-medium tracking-wider uppercase leading-none mb-1">Viagens</span>
                  <span className="text-[16px] sm:text-[18px] font-bold text-white leading-none">89%</span>
                </div>
              </div>
              <div className="hidden sm:block w-px h-8 bg-white/10"></div>
              <div className="flex items-center gap-2 sm:gap-3">
                <div className="w-8 h-8 sm:w-10 sm:h-10 rounded-full border border-teal-500/30 flex items-center justify-center bg-teal-500/10">
                   <DollarSign size={18} className="text-teal-400" strokeWidth={2.5} />
                </div>
                <div className="flex flex-col">
                  <span className="text-[9px] sm:text-[10px] text-white/60 font-medium tracking-wider uppercase leading-none mb-1">Ganhos</span>
                  <span className="text-[16px] sm:text-[18px] font-bold text-white leading-none">91%</span>
                </div>
              </div>
            </div>

            {/* Bottom Row: XP Progress */}
            <div className="flex flex-row items-center gap-3 sm:gap-4 mt-2 bg-[#021317] rounded-xl sm:rounded-2xl p-2 sm:p-3 border border-white/5">
              <div className="flex-1 flex flex-col gap-1.5 sm:gap-2">
                <div className="flex justify-between items-center px-1">
                   <span className="text-[12px] sm:text-[14px] font-medium text-white/90">800 / 1.000 XP</span>
                   <span className="text-[11px] sm:text-[13px] font-semibold text-teal-400">80%</span>
                </div>
                <div className="w-full h-2 sm:h-2.5 bg-[#0a2e38] rounded-full overflow-hidden">
                  <div className="h-full bg-teal-400 rounded-full" style={{ width: '80%' }}></div>
                </div>
              </div>
              <div className="hidden sm:block w-px h-8 bg-white/10"></div>
              <div className="flex items-center gap-2 shrink-0 pr-1 sm:pr-2">
                 <div className="w-8 h-8 sm:w-10 sm:h-10 rounded-full bg-teal-500/20 border border-teal-500/30 flex items-center justify-center">
                    <Star size={18} className="text-teal-400" fill="currentColor" />
                 </div>
                 <span className="text-[12px] sm:text-[14px] font-bold text-white uppercase tracking-wider">
                   Nível <span className="text-teal-400 text-[14px] sm:text-[16px] ml-0.5">12</span>
                 </span>
              </div>
            </div>
          </div>
        </div>
"""

new_content = content[:start_idx] + new_banner + content[end_idx:]

with open("src/components/CompanyPerformanceCard.tsx", "w", encoding="utf-8") as f:
    f.write(new_content)

print("Updated successfully")
