import React, {
  lazy,
  Suspense,
  useEffect,
  useRef,
  useState,
} from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { cn } from "../../lib/utils";
import { useOperationalStore, useSessionStore } from "../../context/AppContext";
import { useCompanyStore } from "../../context/CompanyContext";
import {
  ChevronLeft,
  Bell,
  ChevronDown,
  LayoutGrid,
  LayoutDashboard,
  FileText,
  Truck,
  Container,
  Users,
  Building2,
  ShieldCheck,
  Gamepad2,
  Check,
  RefreshCw,
  Activity,
  Settings,
  Pencil,
  Eye,
} from "lucide-react";
import { toast } from "sonner";
import { StableImage } from "../../components/common/StableImage";
import { prepareAndCommitNavigation } from "../../lib/navigationTransition";
import { preloadRoute } from "../../lib/routePreload";
import { resolveSimulatorDisplayLabel } from "../../lib/simulatorOptions";
import OperationsTab from "./fleet/OperationsTab";

const loadOperationsTab = () => import("./fleet/OperationsTab");
const loadCompanyTab = () => import("./fleet/CompanyTab");
const loadVehiclesTab = () => import("./fleet/VehiclesTab");
const loadTrailersTab = () => import("./fleet/TrailersTab");
const loadDriversTab = () => import("./fleet/DriversTab");
const loadContractsTab = () => import("./fleet/ContractsTab");
const loadRecruitmentTab = () => import("./fleet/RecruitmentTab");
const loadTripHistory = () => import("../driver/TripHistory");

const CompanyTab = lazy(loadCompanyTab);
const VehiclesTab = lazy(loadVehiclesTab);
const TrailersTab = lazy(loadTrailersTab);
const DriversTab = lazy(loadDriversTab);
const ContractsTab = lazy(loadContractsTab);
const RecruitmentTab = lazy(loadRecruitmentTab);
const TripHistory = lazy(loadTripHistory);

type Tab = "operations" | "company" | "fleet" | "hr" | "history" | "reports";

type FleetTab = "drivers" | "contracts" | "vehicles" | "trailers";

const tabOptions = [
  { id: "operations", label: "Painel Operacional", icon: LayoutDashboard },
  { id: "company", label: "Perfil da Empresa", icon: LayoutGrid },
  { id: "hr", label: "Recursos Humanos", icon: Building2 },
  { id: "fleet", label: "Frota", icon: Truck },
  { id: "history", label: "Histórico de Viagens", icon: FileText },
  { id: "reports", label: "Relatórios", icon: Activity },
];

const fleetOptions = [
  { id: "drivers", label: "Funcionários", icon: Users },
  { id: "contracts", label: "Operações", icon: FileText },
  { id: "vehicles", label: "Veículos", icon: Truck },
  { id: "trailers", label: "Reboques", icon: Container },
];

const topLevelTabIds: readonly Tab[] = [
  "operations",
  "company",
  "fleet",
  "hr",
  "history",
  "reports",
];

const fleetTabIds: readonly FleetTab[] = [
  "drivers",
  "contracts",
  "vehicles",
  "trailers",
];

const FleetPanelTransition = () => (
  <div className="min-h-[220px]" aria-hidden="true" />
);

const preloadTopLevelTab = (tab: Tab): Promise<void> => {
  const loader =
    tab === "operations"
      ? loadOperationsTab
      : tab === "company"
        ? loadCompanyTab
        : tab === "hr"
          ? loadRecruitmentTab
          : tab === "history"
            ? loadTripHistory
            : tab === "fleet"
              ? loadDriversTab
              : null;
  return loader
    ? loader().then(() => undefined).catch(() => undefined)
    : Promise.resolve();
};

const preloadFleetSubTab = (tab: FleetTab): Promise<void> => {
  const loader =
    tab === "drivers"
      ? loadDriversTab
      : tab === "contracts"
        ? loadContractsTab
        : tab === "vehicles"
          ? loadVehiclesTab
          : loadTrailersTab;
  return loader().then(() => undefined).catch(() => undefined);
};

type FleetNavigation = {
  activeTab: Tab;
  activeFleetTab: FleetTab;
  editContractId: string | null;
};

const getFleetNavigation = (state: unknown): FleetNavigation => {
  const routeState =
    state && typeof state === "object"
      ? (state as Record<string, unknown>)
      : {};
  const requestedTab = routeState.activeTab;
  const requestedFleetTab = routeState.activeFleetTab ?? routeState.fleetTab;
  const editContractId =
    typeof routeState.editContractId === "string"
      ? routeState.editContractId
      : null;

  // Preserve compatibility with older links that used activeTab for a
  // nested fleet selector.
  if (
    typeof requestedTab === "string" &&
    fleetTabIds.includes(requestedTab as FleetTab)
  ) {
    return {
      activeTab: "fleet",
      activeFleetTab: requestedTab as FleetTab,
      editContractId,
    };
  }

  const activeTab =
    typeof requestedTab === "string" &&
    topLevelTabIds.includes(requestedTab as Tab)
      ? (requestedTab as Tab)
      : "operations";
  const activeFleetTab =
    typeof requestedFleetTab === "string" &&
    fleetTabIds.includes(requestedFleetTab as FleetTab)
      ? (requestedFleetTab as FleetTab)
      : "drivers";

  return { activeTab, activeFleetTab, editContractId };
};

export default function Fleet() {
  const location = useLocation();
  const navigate = useNavigate();
  const { currentUser } = useSessionStore();
  const { simulators } = useOperationalStore();
  const {
    activeCompanyId,
    setActiveCompanyId,
    companies,
    memberships,
    recruitmentApplications,
  } = useCompanyStore();
  const [activeTab, setActiveTab] = useState<Tab>(() =>
    getFleetNavigation(location.state).activeTab,
  );
  const [visitedTabs, setVisitedTabs] = useState<Set<string>>(new Set([activeTab]));
  const [activeFleetTab, setActiveFleetTab] = useState<FleetTab>(() => getFleetNavigation(location.state).activeFleetTab);
  const [visitedFleetTabs, setVisitedFleetTabs] = useState<Set<string>>(new Set([getFleetNavigation(location.state).activeFleetTab]));
  // The selected panel is the visual acknowledgement of the user's click.
  // Deferring these values made the active tab wait for React's background
  // scheduler on slower WebViews, even though the click had already
  // committed the new state.
  const renderedTab = activeTab;
  const renderedFleetTab = activeFleetTab;
  // Only the selected panel is mounted initially, so no timer is needed
  // before displaying it.
  const [showSecondary] = useState(true);

  useEffect(() => {
    setVisitedTabs((previous) =>
      previous.has(renderedTab)
        ? previous
        : new Set(previous).add(renderedTab),
    );
  }, [renderedTab]);

  useEffect(() => {
    setVisitedFleetTabs((prev) => {
      if (prev.has(renderedFleetTab)) return prev;
      const next = new Set(prev);
      next.add(renderedFleetTab);
      return next;
    });
  }, [renderedFleetTab]);
  const [editContractId, setEditContractId] = useState<string | null>(null);
  const [preselectedDriverId, setPreselectedDriverId] = useState<string | null>(
    null,
  );
  const [preselectedContractId, setPreselectedContractId] = useState<
    string | null
  >(null);
  const [isTabMenuOpen, setIsTabMenuOpen] = useState(false);
  const [isFleetMenuOpen, setIsFleetMenuOpen] = useState(false);
  const [isSimulatorMenuOpen, setIsSimulatorMenuOpen] = useState(false);
  const [isRecruitmentFormOpen, setIsRecruitmentFormOpen] = useState(false);
  const [isTripDetailsOpen, setIsTripDetailsOpen] = useState(false);
  const [isCompanyEditMode, setIsCompanyEditMode] = useState(false);
  const [isCompanyViewMode, setIsCompanyViewMode] = useState(false);
  const [isCompanySettingsOpen, setIsCompanySettingsOpen] = useState(false);
  const tabRequestRef = useRef(0);

  useEffect(() => {
    if (!isTabMenuOpen) return;
    // Opening the selector itself must stay light. Warm only the visible
    // panel here; the remaining chunks are handled by the idle warm-up.
    void preloadTopLevelTab(activeTab);
  }, [activeTab, isTabMenuOpen]);

  useEffect(() => {
    if (!isFleetMenuOpen) return;
    void preloadFleetSubTab(activeFleetTab);
  }, [activeFleetTab, isFleetMenuOpen]);

  useEffect(() => {
    const connection = (navigator as Navigator & {
      connection?: { saveData?: boolean; effectiveType?: string };
    }).connection;
    if (connection?.saveData || connection?.effectiveType === "2g") return;

    // The company profile is a frequent first tap and its chunk is small.
    // Warm it shortly after the operational shell commits instead of making
    // mobile users wait for a long idle callback that may never fire.
    const companyTimer = window.setTimeout(() => {
      void loadCompanyTab().catch(() => undefined);
    }, 180);

    const warmAllPanels = () => {
      const loaders = [
        loadRecruitmentTab,
        loadTripHistory,
        loadDriversTab,
        loadContractsTab,
        loadVehiclesTab,
        loadTrailersTab,
      ];
      let index = 0;
      const worker = async () => {
        while (index < loaders.length) {
          const loader = loaders[index++];
          try {
            await loader();
          } catch {
            // Best-effort warm-up; click navigation can retry.
          }
        }
      };
      void Promise.all([worker(), worker()]);
    };

    // Parsing every secondary panel in the first frame competes with the
    // first user interaction. Use an idle slice after the shell is usable.
    const idleApi = window as Window & {
      requestIdleCallback?: (
        callback: () => void,
        options?: { timeout: number },
      ) => number;
      cancelIdleCallback?: (id: number) => void;
    };
    if (idleApi.requestIdleCallback) {
      const idleId = idleApi.requestIdleCallback(warmAllPanels, { timeout: 1400 });
      return () => {
        window.clearTimeout(companyTimer);
        idleApi.cancelIdleCallback?.(idleId);
      };
    }
    const timer = window.setTimeout(warmAllPanels, 900);
    return () => {
      window.clearTimeout(companyTimer);
      window.clearTimeout(timer);
    };
  }, []);

  const handleContractEditComplete = React.useCallback(() => {
    setEditContractId(null);
  }, []);

  const activeCompany = companies.find((c) => c.id === activeCompanyId);
  const getSimulatorLabel = (company: any) =>
    resolveSimulatorDisplayLabel(
      company,
      simulators as any[],
      companies as any[],
    ) || company?.simulatorName || "";

  const pendingRecruitmentCount = React.useMemo(
    () =>
      recruitmentApplications.filter(
        (application) =>
          application.companyId === activeCompanyId &&
          application.status === "pending" &&
          application.isCurrent !== false &&
          application.type !== "company_registration",
      ).length,
    [activeCompanyId, recruitmentApplications],
  );

  useEffect(() => {
    const nextNavigation = getFleetNavigation(location.state);
    setActiveTab((current) =>
      current === nextNavigation.activeTab ? current : nextNavigation.activeTab,
    );
    setActiveFleetTab((current) =>
      current === nextNavigation.activeFleetTab
        ? current
        : nextNavigation.activeFleetTab,
    );
    setEditContractId((current) =>
      current === nextNavigation.editContractId
        ? current
        : nextNavigation.editContractId,
    );
  }, [location.state]);

  // These selectors are views inside the same route. Keeping their state in
  // React Router creates real browser/WebView history entries, so Back
  // returns through the previous corporate view instead of closing the app.
  const selectFleetTab = (nextTab: Tab) => {
    setIsTabMenuOpen(false);
    if (nextTab === activeTab) return;
    const requestId = ++tabRequestRef.current;

    const currentState =
      location.state && typeof location.state === "object"
        ? (location.state as Record<string, unknown>)
        : {};
    const nextState: Record<string, unknown> = {
      ...currentState,
      activeTab: nextTab,
    };
    if (nextTab === "fleet") nextState.activeFleetTab = activeFleetTab;
    else delete nextState.editContractId;

    // Start the module request and switch the selected panel in the click frame.
    void prepareAndCommitNavigation(
      () => preloadTopLevelTab(nextTab),
      () => {
        if (requestId !== tabRequestRef.current) return;
        setActiveTab(nextTab);
        navigate(location.pathname, { state: nextState });
      },
    );
  };

  const selectFleetSubTab = (nextTab: FleetTab) => {
    setIsFleetMenuOpen(false);
    if (activeTab === "fleet" && nextTab === activeFleetTab) return;
    const requestId = ++tabRequestRef.current;

    const currentState =
      location.state && typeof location.state === "object"
        ? (location.state as Record<string, unknown>)
        : {};
    const nextState: Record<string, unknown> = {
      ...currentState,
      activeTab: "fleet",
      activeFleetTab: nextTab,
    };
    if (nextTab !== "contracts") delete nextState.editContractId;

    void prepareAndCommitNavigation(
      () => preloadFleetSubTab(nextTab),
      () => {
        if (requestId !== tabRequestRef.current) return;
        setActiveTab("fleet");
        setActiveFleetTab(nextTab);
        navigate(location.pathname, { state: nextState });
      },
    );
  };

  const activeTabDetails =
    tabOptions.find((t) => t.id === activeTab) || tabOptions[0];
  const ActiveIcon = activeTabDetails.icon;

  return (
    <div className="min-h-[calc(100vh-64px)] bg-slate-50 dark:bg-[#09090b] font-sans pb-8 w-full box-border">
      <div className="max-w-3xl mx-auto flex flex-col gap-4 pt-4 sm:pt-6 w-full px-4 sm:px-4 md:px-0 box-border">
        {/* Company Main Card */}
        {activeCompany && !isRecruitmentFormOpen && !isTripDetailsOpen && (
          <div className="bg-white dark:bg-[#1A1F26] border border-slate-200 dark:border-[#2A2F3A] rounded-[14px] sm:rounded-[16px] flex flex-col shadow-sm relative z-30 w-full box-border">
            <div className="p-3 sm:p-4 flex items-start sm:items-center justify-between gap-3 w-full">
              <div className="flex items-center gap-3 min-w-0 flex-1">
                <div className="w-12 h-12 sm:w-14 sm:h-14 bg-slate-900 dark:bg-[#2A2F3A] rounded-lg overflow-hidden flex items-center justify-center shrink-0 border border-slate-200 dark:border-[#3A3F4A] shadow-sm relative">
                  {activeCompany.logoUrl ? (
                    <StableImage
                      src={activeCompany.logoUrl}
                      alt="Logo"
                      loading="eager"
                      decoding="async"
                      fetchPriority="high"
                      wrapperClassName="w-full h-full"
                      className="object-cover"
                      fallback={
                        <span className="h-full w-full bg-slate-900 dark:bg-[#2A2F3A] flex items-center justify-center text-base sm:text-lg font-bold text-white tracking-tighter">
                          {activeCompany.companyName?.substring(0, 2).toUpperCase() || "NV"}
                        </span>
                      }
                    />
                  ) : (
                    <span className="text-base sm:text-lg font-bold text-white tracking-tighter">
                      {activeCompany.companyName
                        ?.substring(0, 2)
                        .toUpperCase() || "NV"}
                    </span>
                  )}
                </div>
                <div className="flex flex-col flex-1 min-w-0 justify-center">
                  <div className="flex items-center gap-1.5 w-full">
                    <h2 className="text-[11px] sm:text-[13px] font-bold text-slate-900 dark:text-white leading-none whitespace-nowrap">
                      {activeCompany.companyName}
                    </h2>
                    <ShieldCheck
                      size={14}
                      className="text-blue-500 shrink-0"
                      fill="currentColor"
                    />
                  </div>
                  <p className="text-[9px] sm:text-[11px] text-slate-500 dark:text-slate-400 font-medium leading-none mt-1 whitespace-nowrap">
                    CNPJ {activeCompany.cnpj || "12.345.678/0001-90"}
                  </p>
                </div>
              </div>

              <div className="flex items-center pl-3 sm:pl-4 border-l border-slate-200 dark:border-[#2A2F3A] shrink-0 w-auto">
                <div className="flex flex-col w-auto min-w-0 justify-center">
                  <span className="text-[8px] sm:text-[9px] text-slate-500 dark:text-slate-400 font-bold uppercase tracking-wider mb-0.5 px-0.5 whitespace-nowrap">
                    Simulador
                  </span>
                  <button
                    onClick={() => setIsSimulatorMenuOpen(!isSimulatorMenuOpen)}
                    className="flex items-center justify-between gap-1.5 bg-slate-50 dark:bg-[#1A1F26] hover:bg-slate-100 dark:hover:bg-[#2A2F3A] transition-colors border border-slate-200 dark:border-[#2A2F3A] shadow-sm rounded-md px-1.5 py-1 w-auto active:scale-[0.98]"
                  >
                    <div className="flex items-center gap-1.5 min-w-0">
                      <Truck
                        size={10}
                        className="text-slate-500 dark:text-slate-400 shrink-0"
                      />
                      <span className="text-[9px] sm:text-[10px] font-semibold text-slate-800 dark:text-slate-200 whitespace-nowrap">
                        {getSimulatorLabel(activeCompany) || "G. Truck"}
                      </span>
                    </div>
                    <ChevronDown
                      size={10}
                      className={cn(
                        "text-slate-400 shrink-0 transition-transform ml-1",
                        isSimulatorMenuOpen && "rotate-180",
                      )}
                    />
                  </button>
                </div>
              </div>
            </div>

            {isSimulatorMenuOpen && (
              <div className="border-t border-slate-100 dark:border-[#2A2F3A] p-2 sm:p-3 bg-slate-50/50 dark:bg-[#1A1F26]/50 rounded-b-[14px] sm:rounded-b-[16px] animate-in fade-in slide-in-from-top-2">
                <div className="text-[10px] font-bold text-slate-500 uppercase tracking-wider mb-2 px-2">
                  Selecione o Simulador:
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-1.5">
                  {companies
                    .filter((c) =>
                      memberships?.some(
                        (m) =>
                          m.companyId === c.id &&
                          (m.roles?.includes("admin") ||
                            m.roles?.includes("owner" as any)),
                      ),
                    )
                    .map((c) => {
                      const isSelected = c.id === activeCompanyId;
                      return (
                        <button
                          key={c.id}
                          onClick={() => {
                            setActiveCompanyId(c.id);
                            setIsSimulatorMenuOpen(false);
                          }}
                          className={cn(
                            "w-full flex items-center justify-between px-3 py-2 border border-transparent rounded-md transition-colors text-left",
                            isSelected
                              ? "bg-white dark:bg-[#2A2F3A] border-slate-200 dark:border-[#3A3F4A] shadow-sm"
                              : "hover:bg-white dark:hover:bg-[#2A2F3A] hover:border-slate-200 dark:hover:border-[#3A3F4A]",
                          )}
                        >
                          <span
                            className={cn(
                              "text-[11px] sm:text-[12px] font-semibold truncate mr-2",
                              isSelected
                                ? "text-slate-900 dark:text-white"
                                : "text-slate-600 dark:text-slate-300",
                            )}
                          >
                            {getSimulatorLabel(c) || "Global Truck"}
                          </span>
                          {isSelected && (
                            <Check
                              size={14}
                              className="text-blue-600 dark:text-blue-400 shrink-0"
                            />
                          )}
                        </button>
                      );
                    })}
                </div>
              </div>
            )}
          </div>
        )}

        {/* Custom Tab Selector */}
        {activeCompany && !isRecruitmentFormOpen && !isTripDetailsOpen && (
          <div className="w-full flex flex-col gap-2 sm:gap-3 mb-4 z-20">
            <div className="flex items-center justify-between sm:justify-start gap-2 w-full sm:w-auto">
              <div className="flex items-center gap-2 min-w-0 flex-1 sm:flex-none">
                {/* Primary Tab Selector */}
                <div className="min-w-0 flex-1 sm:flex-none">
                  <button
                    onClick={() => setIsTabMenuOpen(!isTabMenuOpen)}
                    className="w-full sm:w-auto h-9 bg-white dark:bg-[#1A1F26] border border-slate-200 dark:border-[#2A2F3A] rounded-lg px-3 flex items-center justify-center sm:justify-start gap-2 shadow-sm focus:outline-none transition-colors hover:bg-slate-50 dark:hover:bg-[#2A2F3A]"
                  >
                    <ActiveIcon
                      size={14}
                      className="text-slate-600 dark:text-slate-400 shrink-0"
                    />
                    <span className="text-[11px] sm:text-[12px] font-semibold text-slate-800 dark:text-slate-200 whitespace-nowrap">
                      {activeTabDetails.label}
                    </span>
                    {activeTab === "hr" && pendingRecruitmentCount > 0 && (
                      <span
                        className="inline-flex min-w-4 h-4 px-1 items-center justify-center rounded-full bg-slate-100 dark:bg-[#2A2F3A] border border-slate-200 dark:border-[#3A3F4A] text-[9px] leading-none font-semibold text-slate-600 dark:text-slate-300"
                        aria-label={`${pendingRecruitmentCount} inscrições pendentes`}
                      >
                        {pendingRecruitmentCount > 99
                          ? "99+"
                          : pendingRecruitmentCount}
                      </span>
                    )}
                    <ChevronDown
                      size={14}
                      className={cn(
                        "text-slate-400 shrink-0 transition-transform",
                        isTabMenuOpen && "rotate-180",
                      )}
                    />
                  </button>
                </div>

                {/* Sub-selector for Fleet */}
                {activeTab === "fleet" && (
                  <div className="min-w-0 flex-1 sm:flex-none">
                    <button
                      onClick={() => setIsFleetMenuOpen(!isFleetMenuOpen)}
                      className="w-full sm:w-auto h-9 bg-white dark:bg-[#1A1F26] border border-slate-200 dark:border-[#2A2F3A] rounded-lg px-3 flex items-center justify-center sm:justify-start gap-2 shadow-sm focus:outline-none transition-colors hover:bg-slate-50 dark:hover:bg-[#2A2F3A]"
                    >
                      {fleetOptions.find((o) => o.id === activeFleetTab)?.icon &&
                        React.createElement(
                          fleetOptions.find((o) => o.id === activeFleetTab)!.icon,
                          {
                            size: 14,
                            className:
                              "text-slate-600 dark:text-slate-400 shrink-0",
                          },
                        )}
                      <span className="text-[11px] sm:text-[12px] font-semibold text-slate-800 dark:text-slate-200 whitespace-nowrap">
                        {fleetOptions.find((o) => o.id === activeFleetTab)?.label}
                      </span>
                      <ChevronDown
                        size={14}
                        className={cn(
                          "text-slate-400 shrink-0 transition-transform",
                          isFleetMenuOpen && "rotate-180",
                        )}
                      />
                    </button>
                  </div>
                )}
              </div>

              {/* Settings Button */}
              {activeTab === "company" && (
                <div className="shrink-0 relative">
                  <button
                    onClick={() => setIsCompanySettingsOpen(!isCompanySettingsOpen)}
                    className="w-9 h-9 bg-white dark:bg-[#1A1F26] border border-slate-200 dark:border-[#2A2F3A] rounded-lg flex items-center justify-center shadow-sm active:scale-[0.99] transition-transform hover:bg-slate-50 dark:hover:bg-[#2A2F3A]"
                  >
                    <Settings
                      size={16}
                      className="text-slate-600 dark:text-slate-400"
                    />
                  </button>

                  {isCompanySettingsOpen && (
                    <>
                      <div
                        className="fixed inset-0 z-40"
                        onClick={() => setIsCompanySettingsOpen(false)}
                      />
                      <div className="absolute right-0 top-full mt-2 w-48 bg-white dark:bg-[#1A1F26] border border-slate-200 dark:border-[#2A2F3A] rounded-xl shadow-lg z-50 overflow-hidden animate-in fade-in slide-in-from-top-2">
                        <button
                          onClick={() => {
                            setIsCompanySettingsOpen(false);
                            setIsCompanyEditMode(true);
                          }}
                          className="w-full flex items-center gap-2.5 px-4 py-3 text-left hover:bg-slate-50 dark:hover:bg-[#2A2F3A] transition-colors border-b border-slate-100 dark:border-[#2A2F3A]"
                        >
                          <Pencil size={16} className="text-blue-600 dark:text-blue-400" />
                          <span className="text-[13px] font-medium text-slate-700 dark:text-slate-300">
                            Editar Perfil
                          </span>
                        </button>
                        <button
                          onClick={() => {
                            setIsCompanySettingsOpen(false);
                            setIsCompanyViewMode(true);
                          }}
                          className="w-full flex items-center gap-2.5 px-4 py-3 text-left hover:bg-slate-50 dark:hover:bg-[#2A2F3A] transition-colors"
                        >
                          <Eye size={16} className="text-slate-600 dark:text-slate-400" />
                          <span className="text-[13px] font-medium text-slate-700 dark:text-slate-300">
                            Visualizar Informações
                          </span>
                        </button>
                      </div>
                    </>
                  )}
                </div>
              )}
            </div>

            {/* Dropdown in normal document flow for Tab Menu */}
            {isTabMenuOpen && (
              <div className="w-full bg-white dark:bg-[#1A1F26] border border-slate-200 dark:border-[#2A2F3A] rounded-lg shadow-sm overflow-hidden animate-in fade-in slide-in-from-top-2">
                {tabOptions.map((tab) => {
                  const Icon = tab.icon;
                  return (
                    <button
                      key={tab.id}
                      onClick={() => {
                        if (tab.id === "reports") {
                          void prepareAndCommitNavigation(
                            () => preloadRoute("/admin/reports"),
                            () => navigate("/admin/reports"),
                          );
                        } else {
                          selectFleetTab(tab.id as Tab);
                        }
                        setIsTabMenuOpen(false);
                      }}
                      className={cn(
                        "w-full flex items-center gap-2.5 px-3 py-2.5 border-b border-slate-100 dark:border-[#2A2F3A]/60 last:border-0 hover:bg-slate-50 dark:hover:bg-[#2A2F3A] transition-colors text-left",
                        activeTab === tab.id
                          ? "bg-slate-50 dark:bg-[#2A2F3A]"
                          : "",
                      )}
                    >
                      <Icon
                        size={14}
                        className={cn(
                          activeTab === tab.id
                            ? "text-blue-600 dark:text-[#0cb49f]"
                            : "text-slate-500",
                        )}
                      />
                      <div className="flex min-w-0 flex-1 items-center justify-between gap-2">
                        <span
                          className={cn(
                            "text-[12px] font-semibold whitespace-nowrap",
                            activeTab === tab.id
                              ? "text-blue-700 dark:text-[#0cb49f]"
                              : "text-slate-600 dark:text-slate-300",
                          )}
                        >
                          {tab.label}
                        </span>
                        {tab.id === "hr" && pendingRecruitmentCount > 0 && (
                          <span
                            className={cn(
                              "inline-flex min-w-4 h-4 px-1 items-center justify-center rounded-full border text-[9px] leading-none font-semibold",
                              activeTab === tab.id
                                ? "bg-blue-50 dark:bg-[#0cb49f]/10 border-blue-100 dark:border-[#0cb49f]/20 text-blue-600 dark:text-[#0cb49f]"
                                : "bg-slate-100 dark:bg-[#27272a] border-slate-200 dark:border-[#3f3f46] text-slate-500 dark:text-slate-400",
                            )}
                            aria-label={`${pendingRecruitmentCount} inscrições pendentes`}
                          >
                            {pendingRecruitmentCount > 99
                              ? "99+"
                              : pendingRecruitmentCount}
                          </span>
                        )}
                      </div>
                    </button>
                  );
                })}
              </div>
            )}

            {/* Dropdown in normal document flow for Fleet Menu */}
            {isFleetMenuOpen && activeTab === "fleet" && (
              <div className="w-full bg-white dark:bg-[#1A1F26] border border-slate-200 dark:border-[#2A2F3A] rounded-lg shadow-sm overflow-hidden animate-in fade-in slide-in-from-top-2">
                {fleetOptions.map((opt) => {
                  const Icon = opt.icon;
                  const isSelected = activeFleetTab === opt.id;
                  return (
                    <button
                      key={opt.id}
                      onClick={() => {
                        selectFleetSubTab(opt.id as FleetTab);
                        setIsFleetMenuOpen(false);
                      }}
                      className={cn(
                        "w-full flex items-center gap-2.5 px-3 py-2.5 border-b border-slate-100 dark:border-[#2A2F3A]/60 last:border-0 hover:bg-slate-50 dark:hover:bg-[#2A2F3A] transition-colors text-left",
                        isSelected ? "bg-slate-50 dark:bg-[#2A2F3A]" : "",
                      )}
                    >
                      <Icon
                        size={14}
                        className={cn(
                          isSelected ? "text-blue-600" : "text-slate-500",
                        )}
                      />
                      <span
                        className={cn(
                          "text-[12px] font-semibold whitespace-nowrap",
                          isSelected
                            ? "text-blue-700 dark:text-blue-400"
                            : "text-slate-600 dark:text-slate-300",
                        )}
                      >
                        {opt.label}
                      </span>
                    </button>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {/* Content Area */}
        <div className="relative">
          <Suspense fallback={<FleetPanelTransition />}>
            {(visitedTabs.has("operations") || renderedTab === "operations") && <div className={cn(renderedTab !== "operations" && "hidden")}>{showSecondary && <OperationsTab />}</div>}
            {(visitedTabs.has("company") || renderedTab === "company") && (
              <div className={cn(renderedTab !== "company" && "hidden")}>
                {showSecondary && <CompanyTab
                  isEditingProp={isCompanyEditMode}
                  setIsEditingProp={setIsCompanyEditMode}
                  isViewingProp={isCompanyViewMode}
                  setIsViewingProp={setIsCompanyViewMode}
                />}
              </div>
            )}
            {(visitedTabs.has("hr") || renderedTab === "hr") && (
              <div className={cn(renderedTab !== "hr" && "hidden")}>
                {showSecondary && <RecruitmentTab onFormOpen={setIsRecruitmentFormOpen} />}
              </div>
            )}
            {(visitedTabs.has("history") || renderedTab === "history") && (
              <div className={cn(renderedTab !== "history" && "hidden")}>
                {showSecondary && <TripHistory
                  isInsideAdminTab={true}
                  onTripDetailsOpen={setIsTripDetailsOpen}
                  mode="company"
                  companyId={activeCompanyId || undefined}
                />}
              </div>
            )}

            {(visitedTabs.has("fleet") || renderedTab === "fleet") && (
              <div className={cn("space-y-4", renderedTab !== "fleet" && "hidden")}>
                {(visitedFleetTabs.has("drivers") || renderedFleetTab === "drivers") && <div className={cn(renderedFleetTab !== "drivers" && "hidden")}>{showSecondary && <DriversTab />}</div>}
                {(visitedFleetTabs.has("contracts") || renderedFleetTab === "contracts") && (
                  <div className={cn(renderedFleetTab !== "contracts" && "hidden")}>
                    {showSecondary && <ContractsTab
                      editContractId={editContractId}
                      onEditComplete={handleContractEditComplete}
                    />}
                  </div>
                )}
                {(visitedFleetTabs.has("vehicles") || renderedFleetTab === "vehicles") && <div className={cn(renderedFleetTab !== "vehicles" && "hidden")}><VehiclesTab /></div>}
                {(visitedFleetTabs.has("trailers") || renderedFleetTab === "trailers") && <div className={cn(renderedFleetTab !== "trailers" && "hidden")}><TrailersTab /></div>}
              </div>
            )}
          </Suspense>
        </div>
      </div>
    </div>
  );
}
