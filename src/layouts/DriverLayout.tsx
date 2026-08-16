import { resolveDriverPhoto } from '../lib/resolveDriverPhoto';
import React, { useState } from "react";
import { Outlet, NavLink, useLocation, useNavigate } from "react-router-dom";
import {
  useOperationalStore,
  useSessionStore,
} from "../context/AppContext";
import { useNotificationStore } from "../context/NotificationsContext";
import { useCompanyStore } from "../context/CompanyContext";
import {
  Package,
  LayoutDashboard,
  LogOut,
  User as UserIcon,
  Settings,
  Bell,
  Moon,
  Sun,
  Building2,
  ChevronLeft,
  Check,
  ClipboardList,
  Trophy,
  Newspaper,
  Activity,
  BookOpen,
} from "lucide-react";
import { useTheme } from "../hooks/useTheme";
import { cn } from "../lib/utils";
import { auth } from "../lib/firebase";
import { ProfileModal } from "../components/ProfileModal";
import { StableImage } from "../components/common/StableImage";
import { NotificationCenter } from "../components/NotificationCenter";
import { preloadRoute } from "../lib/routePreload";
import {
  commitRoleVisualTransition,
  finishRoleVisualTransition,
} from "../lib/roleVisualTransition";
import {
  prepareAndCommitNavigation,
  isPlainPrimaryNavigation,
} from "../lib/navigationTransition";
import { membershipHasRole, resolveMembershipRoles } from "../lib/membershipRoles";
import {
  beginRankingWarmupSession,
  waitForRankingWarmup,
  warmRegisteredRankingPhotos,
} from "../lib/rankingPhotoWarmup";

export default function DriverLayout() {
  const { currentUser, switchRole, activeRole, logOutApp } = useSessionStore();
  const { companies, activeCompanyId, setActiveCompanyId, memberships } = useCompanyStore();
  const { notifications, markNotificationAsRead } = useNotificationStore();
  const { simulators } = useOperationalStore();
  const navigate = useNavigate();
  const location = useLocation();
  const isNewsRoute = location.pathname.includes("/driver/news");
  const { theme, toggleTheme } = useTheme();
  const [isProfileMenuOpen, setIsProfileMenuOpen] = React.useState(false);
  const [isMobileMenuOpen, setIsMobileMenuOpen] = React.useState(false);
  const [isProfileModalOpen, setIsProfileModalOpen] = useState(false);
  const [isNotificationsOpen, setIsNotificationsOpen] = React.useState(false);
  const [showCompanySwitch, setShowCompanySwitch] = React.useState(false);

  const warmRankingAssets = React.useCallback(() => {
    beginRankingWarmupSession(currentUser?.id || "");
    return Promise.all([
      warmRegisteredRankingPhotos(12),
      waitForRankingWarmup(currentUser?.id),
    ]).then(() => undefined);
  }, [currentUser?.id]);

  const warmNewsFeed = React.useCallback(() => {
    const newsUser = currentUser as any;
    const activeCompany = companies.find((company) => company.id === activeCompanyId) ||
      companies.find((company) => company.id === currentUser?.companyId);
    const simulatorId = activeCompany?.simulatorId || activeCompany?.simuladorId ||
      newsUser?.simulatorId || newsUser?.simuladorId;
    const simulatorDocument = simulators.find((simulator) => simulator.id === simulatorId);
    return import("../pages/NewsFeed").then(({ warmNvuNewsFirstPage }) => warmNvuNewsFirstPage({
      userId: currentUser?.id,
      simulatorValues: [
        activeCompany?.simulatorName,
        activeCompany?.simuladorNome,
        activeCompany?.simulator,
        simulatorDocument?.name,
        newsUser?.simulatorName,
        newsUser?.simuladorNome,
        newsUser?.simulator,
        simulatorDocument?.id,
        activeCompany?.simulatorId,
        activeCompany?.simuladorId,
        newsUser?.simulatorId,
        newsUser?.simuladorId,
      ],
    })).catch((error) => {
      console.warn("[NVU NEWS] Pré-carregamento da página indisponível:", error);
    });
  }, [activeCompanyId, companies, currentUser, simulators]);

  React.useEffect(() => {
    const frame = window.requestAnimationFrame(() => {
      finishRoleVisualTransition();
    });
    return () => window.cancelAnimationFrame(frame);
  }, []);

  React.useEffect(() => {
    const handleOpenNotification = (event: Event) => {
      const notification = (event as CustomEvent).detail as { type?: string; tipo?: string };
      const type = notification?.type ?? notification?.tipo;
      if (type === "NEW_OPERATION") {
        navigate("/driver");
      } else {
        navigate("/driver/profile");
      }
    };
    window.addEventListener("nvu-notification-open", handleOpenNotification);
    return () => window.removeEventListener("nvu-notification-open", handleOpenNotification);
  }, [navigate]);

  const currentActiveCompany = React.useMemo(() => {
    return activeCompanyId
      ? companies.find((c) => c.id === activeCompanyId)
      : null;
  }, [companies, activeCompanyId]);

  const currentMembership = React.useMemo(
    () =>
      memberships.find(
        (membership) =>
          membership.companyId === activeCompanyId &&
          membership.status === "active",
      ),
    [memberships, activeCompanyId],
  );

  const canUseAdminProfile = React.useMemo(() => {
    const isOwner = Boolean(
      currentActiveCompany &&
        (currentActiveCompany.ownerId === currentUser?.id ||
          currentActiveCompany.userId === currentUser?.id),
    );
    const hasMembershipAdminRole = membershipHasRole(
      currentMembership,
      "admin",
      currentUser,
    );
    const hasLegacyAdminRole =
      currentUser?.roles?.includes("admin") &&
      (!activeCompanyId || currentUser.companyId === activeCompanyId);

    return Boolean(hasMembershipAdminRole || isOwner || hasLegacyAdminRole);
  }, [
    currentActiveCompany,
    currentMembership,
    currentUser,
    activeCompanyId,
  ]);

  // Redirect driver to join page if they don't have an active company and aren't an admin
  React.useEffect(() => {
    const isJoinRoute = window.location.pathname.includes("/driver/join");
    const needsToJoinCompany =
      !activeCompanyId && !currentUser?.roles?.includes("admin");

    if (needsToJoinCompany && !isJoinRoute) {
      navigate("/driver/join", { replace: true });
    } else if (!needsToJoinCompany && isJoinRoute) {
      navigate("/driver/profile", { replace: true });
    }
  }, [activeCompanyId, currentUser, navigate]);

  React.useEffect(() => {
    if (!isProfileMenuOpen) setShowCompanySwitch(false);
  }, [isProfileMenuOpen]);

  React.useEffect(() => {
    if (!isMobileMenuOpen) return;
    void preloadRoute("/driver/profile");
    void preloadRoute("/ranking");
    void preloadRoute("/driver/news");
    if (!isNewsRoute) void warmNewsFeed();
    void preloadRoute("/driver/reports");
    void preloadRoute("/driver/history");
  }, [isMobileMenuOpen, isNewsRoute, warmNewsFeed]);


  React.useEffect(() => {
    // Keep the most common route chunks ready without opening ranking data
    // listeners during the first authenticated render.
    void preloadRoute("/ranking");
    void preloadRoute("/driver/news");
    const newsWarmupTimer = isNewsRoute
      ? null
      : window.setTimeout(() => {
          void warmNewsFeed();
        }, 900);
    if (canUseAdminProfile) void preloadRoute("/admin/fleet");
    return () => {
      if (newsWarmupTimer !== null) window.clearTimeout(newsWarmupTimer);
    };
  }, [canUseAdminProfile, currentUser?.id, isNewsRoute, warmNewsFeed]);

  // availableCompanies
  const availableCompanies = React.useMemo(() => {
    const list: { companyId: string; companyName: string; roles: string[] }[] =
      [];
    if (memberships && memberships.length > 0) {
      memberships.forEach((membership) => {
        if (membership.status === "active") {
          const comp = companies.find((c) => c.id === membership.companyId);
          if (companies.length > 0 && !comp) return; // Ghost company ignore
          const cName = comp ? comp.companyName : "Carregando...";
          const roles = new Set(resolveMembershipRoles(membership, currentUser));
          if (
            comp &&
            (comp.ownerId === currentUser?.id || comp.userId === currentUser?.id)
          ) {
            roles.add("admin");
          }
          if (roles.size === 0) roles.add("driver");
          list.push({
            companyId: membership.companyId,
            companyName: cName,
            roles: Array.from(roles),
          });
        }
      });
    }
    return list;
  }, [memberships, companies, currentUser?.id]);

  const unreadNotifications =
    notifications?.filter((n) => !n.lida && n.userId === currentUser?.id) || [];
  const pendingCount = unreadNotifications.length;

  const handleSwitchRole = (newRole: "admin" | "driver") => {
    setIsProfileMenuOpen(false);
    if (activeRole === newRole) return;

    const currentMember = currentMembership;
    const hasRole =
      newRole === "admin"
        ? canUseAdminProfile
        : Boolean(
            (membershipHasRole(currentMember, "driver", currentUser) &&
              currentMember?.status === "active") ||
              (currentUser?.companyId === activeCompanyId &&
                currentUser?.roles?.includes("driver")),
          );

    if (!hasRole) {
      alert(
        newRole === "admin"
          ? "Você não possui permissão de administrador."
          : "Perfil de motorista não disponível.",
      );
      return;
    }

    const target = newRole === "admin" ? "/admin/fleet" : "/driver/profile";
    commitRoleVisualTransition(newRole, () => {
      void switchRole(newRole, activeCompanyId || undefined);
      navigate(target, { replace: true });
    });
    void preloadRoute(target).catch(() => undefined);
  };

  const handleCompanySwitch = (companyId: string, roles: string[]) => {
    const nextRole: "admin" | "driver" = roles.includes("admin")
      ? "admin"
      : "driver";
    const target =
      nextRole === "admin" ? "/admin/fleet" : "/driver/profile";
    const commitCompanySwitch = () => {
      setActiveCompanyId(companyId);
      void switchRole(nextRole, companyId);
      setIsProfileMenuOpen(false);
      if (nextRole !== activeRole) {
        navigate(target, { replace: true });
      }
    };

    if (nextRole === activeRole) {
      commitCompanySwitch();
      return;
    }

    commitRoleVisualTransition(nextRole, commitCompanySwitch);
    void preloadRoute(target).catch(() => undefined);
  };

  const handleLogout = async () => {
    await logOutApp();
    navigate("/login");
  };

  const handleSidebarNavigation = React.useCallback(
    (event: React.MouseEvent<HTMLAnchorElement>, path: string) => {
      setIsMobileMenuOpen(false);
      if (!isPlainPrimaryNavigation(event.nativeEvent)) return;
      event.preventDefault();
      const commit = () => {
        if (path === "/ranking") {
          navigate(path, { state: { backgroundLocation: location } });
          return;
        }
        navigate(path);
      };
      if (path === "/ranking") {
        // Commit first; the ranking warm-up is best-effort and never gates
        // the visible route transition.
        commit();
        void preloadRoute(path).catch(() => undefined);
        void warmRankingAssets().catch(() => undefined);
      } else {
        void prepareAndCommitNavigation(() => preloadRoute(path), commit);
      }
    },
    [location, navigate, warmRankingAssets],
  );

  // All hooks must run before the session guard. The authenticated user can be
  // briefly unavailable during resume/logout and React must still see the same
  // hook order on every render.
  if (!currentUser) return null;

  const isApproved =
    activeRole === "admin" ||
    (activeCompanyId &&
      memberships.find((m) => m.companyId === activeCompanyId)?.status ===
        "active");

  const navItems = isApproved
    ? [
        {
          label: "Painel Operacional",
          icon: LayoutDashboard,
          path: "/driver",
          exact: true,
        },
        {
          label: "Ranking Global",
          icon: Trophy,
          path: "/ranking",
          exact: false,
        },
        {
          label: "NVU News",
          icon: Newspaper,
          path: "/driver/news",
          exact: false,
        },
        {
          label: "Relatórios",
          icon: Activity,
          path: "/driver/reports",
          exact: false,
        },
        {
          label: "Manual",
          icon: BookOpen,
          path: "/driver/manual",
          exact: false,
        },
      ]
    : [];

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-[#09090b] flex flex-col text-gray-900 dark:text-[#fafafa] font-sans">
      {window.location.pathname.includes("/driver/join") ? (
        <Outlet />
      ) : (
        <>
          {/* Top Bar (Header) fixed at the top */}
          <header className="fixed top-0 left-0 right-0 h-11 md:h-12 bg-white dark:bg-[#09090b] border-b border-gray-100 dark:border-[#2A2F3A] flex items-center px-4 md:px-6 justify-between z-50">
            <div className="flex items-center gap-3 md:gap-4">
              <button
                onClick={() => setIsMobileMenuOpen(true)}
                className="md:hidden p-1.5 -ml-1.5 text-gray-600 dark:text-[#d4d4d8] hover:bg-gray-50 dark:bg-[#09090b] dark:hover:bg-[#3f3f46] rounded-lg"
              >
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  width="24"
                  height="24"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <line x1="4" x2="20" y1="12" y2="12" />
                  <line x1="4" x2="20" y1="6" y2="6" />
                  <line x1="4" x2="20" y1="18" y2="18" />
                </svg>
              </button>
              <div
                data-nvu-background-brand
                data-nvu-layout-brand
                className="hidden md:flex items-center"
              >
                <h1 className="font-bold text-lg text-gray-900 dark:text-[#fafafa] tracking-tight">
                  NVU
                </h1>
              </div>
              <div
                data-nvu-background-brand
                data-nvu-layout-brand
                className="md:hidden flex items-center font-bold text-lg text-gray-900 dark:text-[#fafafa] leading-none"
              >
                <span>NVU</span>
              </div>
            </div>

            <div className="flex items-center gap-2 sm:gap-4">
              <NotificationCenter
                notifications={notifications}
                onRead={markNotificationAsRead}
                isOpen={isNotificationsOpen}
                onToggle={() => setIsNotificationsOpen((open) => !open)}
                onClose={() => setIsNotificationsOpen(false)}
                buttonClassName="relative p-2 text-gray-500 dark:text-[#a1a1aa] hover:text-gray-700 dark:hover:text-[#d4d4d8] hover:bg-gray-100 dark:hover:bg-[#3f3f46]/50 rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500/20"
                onOpen={(notification) => {
                  const type = notification.type ?? notification.tipo;
                  navigate(type === "NEW_OPERATION" ? "/driver" : "/driver/profile");
                }}
              />

              <div className="hidden sm:block h-6 w-px bg-gray-200 dark:bg-[#2A2F3A] mx-1"></div>

              <button
                onClick={() => navigate("/driver/profile")}
                className="flex items-center gap-2 focus:outline-none hover:opacity-80 transition-opacity"
              >
                {currentActiveCompany?.logoUrl ? (
                  <StableImage
                    src={currentActiveCompany.logoUrl}
                    alt="Perfil"
                    loading="eager"
                    decoding="async"
                    fetchPriority="high"
                    wrapperClassName="w-8 h-8 sm:w-9 sm:h-9 rounded-full border border-gray-200 dark:border-[#2A2F3A] bg-gray-100 dark:bg-[#18181b] shrink-0"
                    className="object-cover"
                    referrerPolicy="no-referrer"
                    fallback={
                      <span className="h-full w-full bg-slate-900 dark:bg-[#18181b] flex items-center justify-center text-white text-xs sm:text-sm font-bold">
                        {currentActiveCompany.companyName?.substring(0, 2).toUpperCase() || "NV"}
                      </span>
                    }
                  />
                ) : currentActiveCompany ? (
                  <div className="w-8 h-8 sm:w-9 sm:h-9 rounded-full bg-slate-900 dark:bg-[#18181b] border border-transparent dark:border-[#2A2F3A] flex items-center justify-center text-white text-xs sm:text-sm font-bold shrink-0">
                    {currentActiveCompany.companyName
                      ?.substring(0, 2)
                      .toUpperCase()}
                  </div>
                ) : resolveDriverPhoto(currentUser) ? (
                  <StableImage
                    src={resolveDriverPhoto(currentUser)}
                    alt="Perfil"
                    loading="eager"
                    decoding="async"
                    fetchPriority="high"
                    wrapperClassName="w-8 h-8 sm:w-9 sm:h-9 rounded-full border border-gray-200 dark:border-[#2A2F3A] bg-gray-100 dark:bg-[#18181b] shrink-0"
                    className="object-cover"
                    referrerPolicy="no-referrer"
                    fallback={
                      <span className="h-full w-full bg-slate-900 dark:bg-[#18181b] flex items-center justify-center text-white text-xs sm:text-sm font-bold">
                        {currentUser?.name?.substring(0, 2).toUpperCase() || "MO"}
                      </span>
                    }
                  />
                ) : (
                  <div className="w-8 h-8 sm:w-9 sm:h-9 rounded-full bg-slate-900 dark:bg-[#18181b] border border-transparent dark:border-[#2A2F3A] flex items-center justify-center text-white text-xs sm:text-sm font-bold shrink-0">
                    {currentUser?.name?.substring(0, 2).toUpperCase()}
                  </div>
                )}
              </button>
            </div>
          </header>

          {/* Main wrapper starts below the header */}
          <div className="flex-1 flex pt-11 md:pt-12 max-w-full relative">
            {/* Mobile Menu Overlay */}
            {isMobileMenuOpen && (
              <div
                className="fixed inset-0 top-11 bg-gray-900/50 z-40 md:hidden overflow-hidden"
                onClick={() => setIsMobileMenuOpen(false)}
              />
            )}

            {/* Sidebar */}
            <aside
              className={cn(
                "w-64 bg-white dark:bg-[#09090b] border-r border-gray-100 dark:border-[#2A2F3A] flex flex-col fixed top-11 md:top-12 bottom-0 left-0 z-40 shadow-sm dark:shadow-none transition-transform duration-300 ease-in-out md:translate-x-0 hidden md:flex",
                isMobileMenuOpen ? "flex translate-x-0" : "-translate-x-full",
              )}
            >
              <div className="p-4 border-b border-gray-100 dark:border-[#2A2F3A] flex flex-col gap-2 relative bg-gray-50 dark:bg-[#09090b]">
                {isProfileMenuOpen && (
                  <div className="absolute left-4 right-4 top-full mt-2 bg-white dark:bg-[#09090b] border border-gray-200 dark:border-[#2A2F3A] shadow-lg dark:shadow-none rounded-xl overflow-hidden z-50 animate-in fade-in slide-in-from-top-2 duration-200">
                    {showCompanySwitch ? (
                      <>
                        <div className="px-3 py-2 border-b border-gray-100 dark:border-[#2A2F3A] bg-gray-50 dark:bg-[#09090b] flex items-center justify-between">
                          <p className="text-[11px] font-bold text-gray-500 dark:text-[#a1a1aa] uppercase tracking-wider">
                            Escolha a Empresa
                          </p>
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              setShowCompanySwitch(false);
                            }}
                            className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
                          >
                            <ChevronLeft size={16} />
                          </button>
                        </div>
                        <div className="max-h-60 overflow-y-auto p-1">
                          {availableCompanies.map((comp) => (
                            <button
                              key={comp.companyId}
                              onClick={() =>
                                handleCompanySwitch(comp.companyId, comp.roles)
                              }
                              className={cn(
                                "w-full flex items-center justify-between px-3 py-2.5 rounded-lg text-sm font-medium transition-colors text-left",
                                activeCompanyId === comp.companyId
                                  ? "bg-green-50 dark:bg-green-500/10 text-green-700 dark:text-green-400"
                                  : "hover:bg-gray-50 dark:hover:bg-[#3f3f46] text-gray-700 dark:text-[#d4d4d8]",
                              )}
                            >
                              <div className="flex items-center gap-2 truncate">
                                <Building2 size={16} className="shrink-0" />
                                <span className="truncate">
                                  {comp.companyName}
                                </span>
                              </div>
                              {activeCompanyId === comp.companyId && (
                                <Check
                                  size={16}
                                  className="shrink-0 text-green-600 dark:text-green-400"
                                />
                              )}
                            </button>
                          ))}
                        </div>
                      </>
                    ) : (
                      <>
                        {canUseAdminProfile && (
                          <>
                            <div className="px-3 py-2 border-b border-gray-100 dark:border-[#2A2F3A] bg-gray-50 dark:bg-[#09090b]">
                              <p className="text-[11px] font-bold text-gray-500 dark:text-[#a1a1aa] uppercase tracking-wider">
                                Alternar Perfil
                              </p>
                            </div>
                            <div className="p-1 border-b border-gray-100 dark:border-[#2A2F3A]">
                              <button
                                onClick={() => handleSwitchRole("admin")}
                                className={cn(
                                  "w-full flex items-center justify-between px-3 py-2.5 rounded-lg text-sm font-medium transition-colors",
                                  activeRole === "admin"
                                    ? "bg-blue-50 dark:bg-blue-500/10 dark:border-blue-500/20 text-blue-700"
                                    : "hover:bg-gray-50 dark:hover:bg-[#3f3f46] text-gray-700 dark:text-[#d4d4d8]",
                                )}
                              >
                                <div className="flex items-center gap-2">
                                  <svg
                                    xmlns="http://www.w3.org/2000/svg"
                                    width="16"
                                    height="16"
                                    viewBox="0 0 24 24"
                                    fill="none"
                                    stroke="currentColor"
                                    strokeWidth="2"
                                    strokeLinecap="round"
                                    strokeLinejoin="round"
                                  >
                                    <path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2" />
                                    <circle cx="12" cy="7" r="4" />
                                  </svg>
                                  <span>Administrador</span>
                                </div>
                              </button>
                              <button
                                onClick={() => handleSwitchRole("driver")}
                                className={cn(
                                  "w-full flex items-center justify-between px-3 py-2.5 rounded-lg text-sm font-medium transition-colors",
                                  activeRole === "driver"
                                    ? "bg-green-50 dark:bg-green-500/10 dark:border-green-500/20 text-green-700"
                                    : "hover:bg-gray-50 dark:hover:bg-[#3f3f46] text-gray-700 dark:text-[#d4d4d8]",
                                )}
                              >
                                <div className="flex items-center gap-2">
                                  <svg
                                    xmlns="http://www.w3.org/2000/svg"
                                    width="16"
                                    height="16"
                                    viewBox="0 0 24 24"
                                    fill="none"
                                    stroke="currentColor"
                                    strokeWidth="2"
                                    strokeLinecap="round"
                                    strokeLinejoin="round"
                                  >
                                    <path d="M14 18V6a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v11a1 1 0 0 0 1 1h2" />
                                    <path d="M15 18H9" />
                                    <path d="M19 18h2a1 1 0 0 0 1-1v-3.65a1 1 0 0 0-.22-.624l-3.48-4.35A1 1 0 0 0 17.52 8H14" />
                                    <circle cx="17" cy="18" r="2" />
                                    <circle cx="7" cy="18" r="2" />
                                  </svg>
                                  <span>Motorista</span>
                                </div>
                                {activeRole === "driver" && (
                                  <svg
                                    xmlns="http://www.w3.org/2000/svg"
                                    className="text-green-600 dark:text-green-400"
                                    width="16"
                                    height="16"
                                    viewBox="0 0 24 24"
                                    fill="none"
                                    stroke="currentColor"
                                    strokeWidth="2"
                                    strokeLinecap="round"
                                    strokeLinejoin="round"
                                  >
                                    <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
                                    <circle cx="9" cy="7" r="4" />
                                    <polyline points="16 11 18 13 22 9" />
                                  </svg>
                                )}
                              </button>
                            </div>
                          </>
                        )}
                        <div className="p-1 border-b border-gray-100 dark:border-[#2A2F3A]">
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              setShowCompanySwitch(true);
                            }}
                            className="w-full flex items-center justify-between px-3 py-2.5 rounded-lg text-sm font-medium text-gray-700 dark:text-[#d4d4d8] hover:bg-gray-50 dark:bg-[#09090b] dark:hover:bg-[#3f3f46] transition-colors"
                          >
                            <div className="flex items-center gap-2">
                              <Building2 size={16} />
                              <span>Trocar de Empresa</span>
                            </div>
                            <ChevronLeft size={16} className="rotate-180" />
                          </button>
                        </div>
                        <div className="p-1">
                          <button
                            onClick={() => {
                              setIsProfileMenuOpen(false);
                              setIsProfileModalOpen(true);
                            }}
                            className="w-full flex items-center gap-2 px-3 py-2.5 rounded-lg text-sm font-medium text-gray-700 dark:text-[#d4d4d8] hover:bg-gray-50 dark:bg-[#09090b] dark:hover:bg-[#3f3f46] transition-colors"
                          >
                            <Settings size={16} />
                            <span>Editar Perfil</span>
                          </button>
                        </div>
                      </>
                    )}
                  </div>
                )}

                <div
                  onClick={() => setIsProfileMenuOpen(!isProfileMenuOpen)}
                  className={cn(
                    "flex items-center justify-between px-3 py-3 rounded-xl border transition-colors cursor-pointer shadow-sm dark:shadow-none relative",
                    isProfileMenuOpen
                      ? "border-green-200 bg-green-50 dark:bg-green-500/10 dark:border-green-500/20/50"
                      : "border-gray-100 dark:border-[#2A2F3A] bg-white dark:bg-[#09090b] hover:bg-gray-50 dark:hover:bg-[#3f3f46]",
                  )}
                >
                  <div className="flex items-center gap-3">
                    {resolveDriverPhoto(currentUser) ? (
                      <StableImage
                        src={resolveDriverPhoto(currentUser)}
                        alt="driver"
                        loading="eager"
                        decoding="async"
                        fetchPriority="high"
                        wrapperClassName="w-9 h-9 rounded-full bg-gray-200 dark:bg-white/10 shrink-0"
                        className="object-cover"
                        referrerPolicy="no-referrer"
                        fallback={
                          <span className="h-full w-full bg-slate-900 dark:bg-[#18181b] flex items-center justify-center text-white text-sm font-bold">
                            {currentUser?.name?.substring(0, 2).toUpperCase() || "MO"}
                          </span>
                        }
                      />
                    ) : (
                      <div className="w-9 h-9 rounded-full bg-slate-900 dark:bg-[#18181b] flex items-center justify-center text-white text-sm font-bold shrink-0">
                        {currentUser?.name?.substring(0, 2).toUpperCase()}
                      </div>
                    )}
                    <div className="flex-1 min-w-0 pr-2">
                      <p className="text-sm font-bold text-gray-900 dark:text-[#fafafa] truncate">
                        {currentUser?.name}
                      </p>
                      <p className="text-[11px] text-[#0cb49f] dark:text-[#0cb49f] font-semibold truncate leading-tight mt-0.5">
                        Motorista
                      </p>
                    </div>
                  </div>
                  <svg
                    xmlns="http://www.w3.org/2000/svg"
                    width="16"
                    height="16"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    className={cn(
                      "text-gray-400 transition-transform duration-200",
                      isProfileMenuOpen
                        ? "rotate-180 text-[#0cb49f] dark:text-[#0cb49f]"
                        : "",
                    )}
                  >
                    <path d="m6 9 6 6 6-6" />
                  </svg>
                </div>
              </div>

              <nav className="flex-1 px-4 py-6 space-y-2 overflow-y-auto">
                {navItems.map((item) => (
                  <NavLink
                    key={item.path}
                    to={item.path}
                    end={item.exact}
                    onClick={(event) => handleSidebarNavigation(event, item.path)}
                    className={({ isActive }) =>
                      cn(
                        "flex items-center gap-3 px-4 py-2.5 rounded-xl text-[13px] font-medium transition-colors",
                        isActive
                          ? "bg-[#0cb49f]/10 dark:bg-[#0cb49f]/10 text-[#0cb49f] dark:text-[#0cb49f]"
                          : "text-gray-600 dark:text-[#d4d4d8] hover:bg-gray-50 dark:hover:bg-[#3f3f46] hover:text-gray-900 dark:hover:text-[#f4f4f5]",
                      )
                    }
                  >
                    <item.icon size={18} />
                    {item.label}
                  </NavLink>
                ))}
              </nav>

              <div className="p-4 border-t border-gray-100 dark:border-[#2A2F3A] flex flex-col gap-2 relative">
                <div className="mx-2 mb-1 p-3 bg-white dark:bg-[#09090b] border border-gray-100 dark:border-[#2A2F3A]/80 shadow-sm dark:shadow-none rounded-2xl flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <div className="w-8 h-8 rounded-full bg-gray-50 dark:bg-[#27272a] border border-gray-100 dark:border-[#2A2F3A] flex items-center justify-center text-gray-500 dark:text-[#a1a1aa]">
                      {theme === "dark" ? (
                        <Moon size={15} />
                      ) : (
                        <Sun size={15} />
                      )}
                    </div>
                    <span className="text-[13px] font-bold text-gray-900 dark:text-[#fafafa]">
                      Modo Escuro
                    </span>
                  </div>
                  <button
                    onClick={toggleTheme}
                    className={cn(
                      "relative w-10 h-6 rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-[#0cb49f] focus:ring-offset-2 dark:focus:ring-offset-[#121213]",
                      theme === "dark" ? "bg-[#0cb49f]" : "bg-gray-200",
                    )}
                  >
                    <div
                      className={cn(
                        "absolute top-1 left-1 w-4 h-4 rounded-full bg-white dark:bg-[#09090b] shadow-sm transition-transform",
                        theme === "dark" ? "translate-x-4" : "",
                      )}
                    />
                  </button>
                </div>
                <button
                  onClick={handleLogout}
                  className="w-full flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl text-sm font-medium text-gray-600 dark:text-[#d4d4d8] hover:bg-gray-50 dark:bg-[#09090b] dark:hover:bg-[#3f3f46] transition-colors"
                >
                  <LogOut size={16} />
                  Sair
                </button>
              </div>
            </aside>

            {/* Main Content Viewport */}
            <main className="flex-1 min-w-0 md:ml-64 flex flex-col min-h-[calc(100vh-3.5rem)] md:min-h-[calc(100vh-4rem)] max-w-full">
              <div className="p-2 sm:p-4 md:p-6 flex-1 w-full max-w-full overflow-hidden">
                <Outlet />
              </div>
            </main>
          </div>

          <ProfileModal
            isOpen={isProfileModalOpen}
            onClose={() => setIsProfileModalOpen(false)}
          />
        </>
      )}
    </div>
  );
}
