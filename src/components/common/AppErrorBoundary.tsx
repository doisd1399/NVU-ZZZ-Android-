import React from "react";

type AppErrorBoundaryState = {
  hasError: boolean;
};

export class AppErrorBoundary extends React.Component<
  React.PropsWithChildren,
  AppErrorBoundaryState
> {
  state: AppErrorBoundaryState = { hasError: false };

  static getDerivedStateFromError(): AppErrorBoundaryState {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    console.error("[NVU] Falha na renderização inicial:", error, info);
  }

  private reload = () => {
    window.location.reload();
  };

  render() {
    if (!this.state.hasError) return this.props.children;

    return (
      <main className="min-h-screen bg-slate-50 px-6 dark:bg-[#09090b] flex items-center justify-center">
        <section
          className="w-full max-w-sm rounded-2xl border border-slate-200 bg-white p-6 text-center shadow-sm dark:border-white/10 dark:bg-[#151921]"
          role="alert"
        >
          <div className="mb-3 text-lg font-bold tracking-[0.22em] text-slate-900 dark:text-white">
            NVU
          </div>
          <h1 className="text-base font-semibold text-slate-900 dark:text-white">
            Não foi possível abrir esta tela
          </h1>
          <p className="mt-2 text-sm leading-relaxed text-slate-500 dark:text-slate-400">
            Recarregue o aplicativo para restaurar a sessão.
          </p>
          <button
            type="button"
            onClick={this.reload}
            className="mt-5 h-11 w-full rounded-xl bg-slate-900 px-4 text-sm font-semibold text-white transition-transform active:scale-[0.98] dark:bg-white dark:text-slate-900"
          >
            Recarregar
          </button>
        </section>
      </main>
    );
  }
}
