import { Camera, CircleDot, Sparkles, X } from "lucide-react";
import { useEffect, useState } from "react";
import { createPortal } from "react-dom";

export type GtoWorkMode = "print" | "automatic";

interface GtoWorkModeDialogProps {
  open: boolean;
  onClose: () => void;
  onSelect: (mode: GtoWorkMode) => void | Promise<void>;
}

export function GtoWorkModeDialog({
  open,
  onClose,
  onSelect,
}: GtoWorkModeDialogProps) {
  const [automaticStep, setAutomaticStep] = useState(false);

  useEffect(() => {
    if (!open) setAutomaticStep(false);
  }, [open]);

  if (!open || typeof document === "undefined") return null;

  return createPortal(
    <div
      className="fixed inset-0 z-[140] flex items-end sm:items-center justify-center bg-black/55 backdrop-blur-[2px] p-3 sm:p-5"
      role="dialog"
      aria-modal="true"
      aria-labelledby="gto-work-mode-title"
      onMouseDown={(event) => {
        if (event.currentTarget === event.target) onClose();
      }}
    >
      <div className="w-full max-w-md rounded-2xl border border-slate-200 dark:border-white/10 bg-white dark:bg-[#15171c] shadow-2xl overflow-hidden">
        <div className="flex items-start justify-between gap-3 px-4 pt-4 pb-3 border-b border-slate-100 dark:border-white/5">
          <div>
            <h2
              id="gto-work-mode-title"
              className="text-[16px] font-bold text-slate-900 dark:text-white"
            >
              Iniciar trabalho GTO
            </h2>
            <p className="mt-1 text-[12px] leading-relaxed text-slate-500 dark:text-slate-400">
              {automaticStep
                ? "Reative a bolha NVU antes de abrir o simulador."
                : "Escolha como deseja registrar esta operação."}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Fechar"
            className="h-8 w-8 shrink-0 rounded-lg flex items-center justify-center text-slate-500 hover:bg-slate-100 dark:hover:bg-white/5"
          >
            <X size={17} />
          </button>
        </div>

        <div className="p-3 space-y-2.5">
          {!automaticStep ? (
            <>
          <button
            type="button"
            onClick={() => void onSelect("print")}
            className="w-full rounded-xl border border-slate-200 dark:border-white/10 bg-slate-50 dark:bg-[#1d2027] hover:bg-slate-100 dark:hover:bg-[#242832] active:scale-[0.995] transition-all p-3.5 text-left"
          >
            <div className="flex items-start gap-3">
              <div className="h-10 w-10 rounded-xl bg-blue-600/10 text-blue-600 dark:text-blue-400 flex items-center justify-center shrink-0">
                <Camera size={20} />
              </div>
              <div className="min-w-0">
                <div className="font-bold text-[14px] text-slate-900 dark:text-white">
                  Modo print
                </div>
                <p className="mt-0.5 text-[11px] leading-relaxed text-slate-500 dark:text-slate-400">
                  Abre Lançar Viagem. Preencha origem e destino, envie o print e a NVU lê os ganhos e verifica anúncio/valor dobrado.
                </p>
              </div>
            </div>
          </button>

          <button
            type="button"
            onClick={() => setAutomaticStep(true)}
            className="w-full rounded-xl border border-cyan-200/80 dark:border-cyan-400/20 bg-cyan-50/70 dark:bg-cyan-400/5 hover:bg-cyan-50 dark:hover:bg-cyan-400/10 active:scale-[0.995] transition-all p-3.5 text-left"
          >
            <div className="flex items-start gap-3">
              <div className="h-10 w-10 rounded-xl bg-cyan-600 text-white flex items-center justify-center shrink-0">
                <Sparkles size={19} />
              </div>
              <div className="min-w-0">
                <div className="font-bold text-[14px] text-slate-900 dark:text-white">
                  Modo automático
                </div>
                <p className="mt-0.5 text-[11px] leading-relaxed text-slate-500 dark:text-slate-400">
                  Abre o GTO com a bolha NVU. O frete, a conclusão e o envio da viagem são acompanhados automaticamente.
                </p>
              </div>
            </div>
          </button>
            </>
          ) : (
            <>
              <div className="rounded-xl border border-cyan-200/80 dark:border-cyan-400/20 bg-cyan-50/70 dark:bg-cyan-400/5 p-3.5">
                <div className="flex items-start gap-3">
                  <div className="h-10 w-10 rounded-xl bg-cyan-600 text-white flex items-center justify-center shrink-0">
                    <CircleDot size={20} />
                  </div>
                  <div className="min-w-0">
                    <div className="font-bold text-[14px] text-slate-900 dark:text-white">
                      Preparar modo automático
                    </div>
                    <p className="mt-0.5 text-[11px] leading-relaxed text-slate-500 dark:text-slate-400">
                      A NVU vai reconstruir e rearmar o botão flutuante sem apagar uma viagem em andamento. Depois, o GTO será aberto e a bolha reaparecerá assim que o simulador estiver em primeiro plano.
                    </p>
                  </div>
                </div>
              </div>
              <button
                type="button"
                onClick={() => void onSelect("automatic")}
                className="w-full rounded-xl bg-cyan-600 hover:bg-cyan-700 active:scale-[0.995] transition-all px-4 py-3.5 text-white text-[14px] font-bold flex items-center justify-center gap-2"
              >
                <CircleDot size={18} />
                Ativar botão flutuante
              </button>
              <button
                type="button"
                onClick={() => setAutomaticStep(false)}
                className="w-full rounded-xl border border-slate-200 dark:border-white/10 px-4 py-3 text-[13px] font-semibold text-slate-600 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-white/5"
              >
                Voltar
              </button>
            </>
          )}
        </div>
      </div>
    </div>,
    document.body,
  );
}
