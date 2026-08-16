import React, { useState } from "react";
import { Outlet, NavLink, useNavigate, useLocation } from "react-router-dom";
import {
  useActivityStore,
  useOperationalStore,
  useSessionStore,
} from "../context/AppContext";
import { useNotificationStore } from "../context/NotificationsContext";
import { useCompanyStore } from "../context/CompanyContext";
import { resolveProfilePhoto } from "../lib/resolveProfilePhoto";
import {
  Package,
  LayoutDashboard,
  FileText,
  Activity,
  Users,
  Settings,
  LogOut,
  Truck,
  Container,
  UserCheck,
  ChevronDown,
  Bell,
  Menu,
  User,
  Moon,
  Sun,
  UserPlus,
  Building2,
  Crown,
  ChevronLeft,
  Check,
  Trophy,
  Newspaper,
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

export default function AdminLayout() {
  const { currentUser, switchRole, activeRole, logOutApp } = useSessionStore();
  const {
    companies,
    activeCompanyId,
    setActiveCompanyId,
    memberships,
  } = useCompanyStore();
  const { notifications, markNotificationAsRead } = useNotificationStore();
  const { simulators } = useOperationalStore();
  const { jobDemands } = useActivityStore();
  const navigate = useNavigate();
  const { theme, toggleTheme } = useTheme();
  const location = useLocation();
  const isSeniorPanelRoute = location.pathname.startsWith("/admin/senior");
  // The Senior route has its own Firebase-backed password/claim gate. Admin users
  // must be able to see and open that gate even before the senior claim has been
  // granted; hiding the entry behind the claim makes the panel impossible to discover.
  // AdminLayout is mounted only behind ProtectedRoute allowedRole="admin".
  // Keep the Senior entry visible for every authenticated admin; SeniorPanel itself
  // still enforces the Firebase-backed password/custom-claim gate before exposing
  // protected actions. This avoids hiding the only entry point because of stale or
  // not-yet-refreshed role metadata.
  const hasSeniorPanelAccess = true;
  const isNewsRoute = location.pathname.includes("/admin/news");
  const isManualRoute = location.pathname.includes("/admin/manual");
  const [isMobileMenuOpen, setIsMobileMenuOpen] = React.useState(false);
  const [isProfileMenuOpen, setIsProfileMenuOpen] = React.useState(false);
  const [isNotificationsOpen, setIsNotificationsOpen] = React.useState(false);
  const [isProfileModalOpen, setIsProfileModalOpen] = React.useState(false);
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
      if (type === "RH_APPLICATION") {
        navigate("/admin/fleet", { state: { activeTab: "hr" } });
      } else if (type === "WORK_REQUEST") {
        navigate("/admin/fleet", { state: { activeTab: "operations" } });
      } else {
        navigate("/admin/fleet");
      }
    };
    window.addEventListener("nvu-notification-open", handleOpenNotification);
    return () => window.removeEventListener("nvu-notification-open", handleOpenNotification);
  }, [navigate]);

  React.useEffect(() => {
    if (!isProfileMenuOpen) setShowCompanySwitch(false);
  }, [isProfileMenuOpen]);

  React.useEffect(() => {
    if (!isMobileMenuOpen) return;
    void preloadRoute("/admin/fleet");
    void preloadRoute("/ranking");
    void preloadRoute("/admin/news");
    if (!isNewsRoute) void warmNewsFeed();
    void preloadRoute("/admin/reports");
  }, [isMobileMenuOpen, isNewsRoute, warmNewsFeed]);


  React.useEffect(() => {
    // Keep only the route chunk warm after authentication. Ranking data and
    // photos are intentionally deferred so they do not compete with the first
    // profile/panel render.
    void preloadRoute("/ranking");
    void preloadRoute("/admin/news");
    const newsWarmupTimer = isNewsRoute
      ? null
      : window.setTimeout(() => {
          void warmNewsFeed();
        }, 900);
    if (memberships.some((membership) => membershipHasRole(membership, "driver", currentUser))) {
      void preloadRoute("/driver/profile");
    }
    return () => {
      if (newsWarmupTimer !== null) window.clearTimeout(newsWarmupTimer);
    };
  }, [currentUser?.id, isNewsRoute, memberships, warmNewsFeed]);

  const unreadNotifications = notifications.filter((n) => !n.lida);
  const pendingCount = unreadNotifications.length;

  const pendingDemandsCount = React.useMemo(() => {
    return (
      jobDemands?.filter(
        (d) => d.companyId === activeCompanyId && d.status === "pending",
      ).length || 0
    );
  }, [jobDemands, activeCompanyId]);

  const availableCompanies = React.useMemo(() => {
    const list: { companyId: string; companyName: string; roles: string[] }[] =
      [];
    if (memberships && memberships.length > 0) {
      memberships.forEach((membership) => {
        if (membership.status === "active") {
          const comp = companies.find((c) => c.id === membership.companyId);
          if (companies.length > 0 && !comp) return; // Ghost company ignore
          const cName = comp ? comp.companyName : "Carregando...";
          const roles = new Set(
            resolveMembershipRoles(membership, currentUser),
          );
          if (
            comp &&
            (comp.ownerId === currentUser?.id || comp.userId === currentUser?.id)
          ) {
            roles.add("admin");
            roles.add("driver");
          }
          list.push({
            companyId: membership.companyId,
            companyName: cName,
            roles: Array.from(roles),
          });
        }
      });
    }
    return list;
  }, [memberships, companies, currentUser]);

  const handleSwitchRole = (newRole: "admin" | "driver") => {
    setIsProfileMenuOpen(false);
    if (activeRole === newRole) return;

    const currentMember = memberships.find(
      (m) => m.companyId === activeCompanyId,
    );
    const currentCompany = companies.find(
      (company) => company.id === activeCompanyId,
    );
    const isOwner = Boolean(
      currentCompany &&
        (currentCompany.ownerId === currentUser?.id ||
          currentCompany.userId === currentUser?.id),
    );
    const hasLegacyRoleForActiveCompany =
      currentUser?.companyId === activeCompanyId &&
      currentUser?.roles?.includes(newRole);
    const hasRole =
      membershipHasRole(currentMember, newRole, currentUser) ||
      hasLegacyRoleForActiveCompany ||
      (newRole === "admin" && isOwner);

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
    const nextRole: "admin" | "driver" =
      roles.length > 0 && !roles.includes("admin") ? "driver" : "admin";
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
        // Ranking must acknowledge the tap in the same interaction frame.
        // Route/code/photo warm-up continues after the destination is visible.
        commit();
        void preloadRoute(path).catch(() => undefined);
        void warmRankingAssets().catch(() => undefined);
      } else {
        void prepareAndCommitNavigation(() => preloadRoute(path), commit);
      }
    },
    [location, navigate, warmRankingAssets],
  );

  if (!currentUser) return null;

  const navGroups = [
    {
      title: "GESTÃO DA FROTA",
      items: [
        {
          label: "Estrutura da Frota",
          icon: Truck,
          path: "/admin/fleet",
        },
        {
          label: "Ranking",
          icon: Trophy,
          path: "/ranking",
        },
        {
          label: "NVU News",
          icon: Newspaper,
          path: "/admin/news",
        },
        {
          label: "Relatórios",
          icon: Activity,
          path: "/admin/reports",
        },
        {
          label: "Manual",
          icon: BookOpen,
          path: "/admin/manual",
        },
      ],
    },
  ];

  const currentActiveAdminCompany = companies.find(
    (c) => c.id === activeCompanyId,
  );

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-[#09090b] flex flex-col text-gray-900 dark:text-[#fafafa] font-sans">
      {/* Top Bar fixed at the top */}
      <header className="fixed top-0 left-0 right-0 h-11 md:h-12 bg-white dark:bg-[#09090b] border-b border-gray-100 dark:border-[#2A2F3A] flex items-center px-4 md:px-6 justify-between z-50">
        <div className="flex items-center gap-3 md:gap-4">
          <button
            onClick={() => setIsMobileMenuOpen(true)}
            className="lg:hidden p-1.5 -ml-1.5 text-gray-600 dark:text-[#d4d4d8] hover:bg-gray-50 dark:bg-[#09090b] dark:hover:bg-[#3f3f46] rounded-lg"
          >
            <Menu size={24} />
          </button>
          <div
            data-nvu-background-brand
            data-nvu-layout-brand
            className="hidden lg:flex items-center"
          >
            <h1 className="font-bold text-lg text-gray-900 dark:text-[#fafafa] tracking-tight">
              NVU
            </h1>
          </div>
          <div
            data-nvu-background-brand
            data-nvu-layout-brand
            className="lg:hidden flex items-center font-bold text-lg text-gray-900 dark:text-[#fafafa] leading-none"
          >
            <span>NVU</span>
          </div>
        </div>

        {/* Right Box: Bell and User */}
        <div className="flex items-center gap-1.5 md:gap-4">
          <NotificationCenter
            notifications={notifications}
            onRead={markNotificationAsRead}
            isOpen={isNotificationsOpen}
            onToggle={() => setIsNotificationsOpen((open) => !open)}
            onClose={() => setIsNotificationsOpen(false)}
            buttonClassName="relative p-2 text-gray-500 dark:text-[#a1a1aa] hover:bg-gray-50 dark:hover:bg-[#3f3f46] rounded-lg transition-colors"
            onOpen={(notification) => {
              const type = notification.type ?? notification.tipo;
              if (type === "RH_APPLICATION") {
                navigate("/admin/fleet", { state: { activeTab: "hr" } });
              } else if (type === "WORK_REQUEST" || type === "OPERATION_COMPLETED") {
                navigate("/admin/fleet", { state: { activeTab: "operations" } });
              } else {
                navigate("/admin/fleet");
              }
            }}
          />
          <button className="lg:hidden p-1.5 text-gray-500 dark:text-[#a1a1aa] hover:bg-gray-50 dark:bg-[#09090b] dark:hover:bg-[#3f3f46] rounded-lg shrink-0">
            {resolveProfilePhoto(currentUser) ? (
              <StableImage
                src={resolveProfilePhoto(currentUser)}
                alt={currentUser.name}
                loading="eager"
                decoding="async"
                fetchPriority="high"
                wrapperClassName="w-7 h-7 rounded-full bg-gray-200 dark:bg-white/10"
                className="object-cover"
                referrerPolicy="no-referrer"
                fallback={
                  <span className="h-full w-full bg-blue-100 dark:bg-blue-500/20 flex items-center justify-center text-blue-600 dark:text-blue-400 font-bold text-[10px]">
                    {currentUser?.name?.substring(0, 2).toUpperCase() || "AD"}
                  </span>
                }
              />
            ) : (
              <div className="w-7 h-7 rounded-full bg-blue-100 dark:bg-blue-500/20 flex items-center justify-center text-blue-600 dark:text-blue-400 font-bold text-[10px]">
                {currentUser?.name?.substring(0, 2).toUpperCase() || "AD"}
              </div>
            )}
          </button>
          <button
            onClick={handleLogout}
            className="hidden lg:flex p-2 text-gray-400 hover:text-gray-600 dark:text-[#d4d4d8] dark:hover:text-[#a1a1aa]"
          >
            <LogOut size={20} />
          </button>
        </div>
      </header>

      {/* Main wrapper starts below the header */}
      <div className="flex-1 flex pt-11 md:pt-12 max-w-full relative">
        {/* Mobile Menu Overlay */}
        {isMobileMenuOpen && (
          <div
            className="fixed inset-0 top-11 md:top-12 bg-gray-900/50 z-40 lg:hidden overflow-hidden"
            onClick={() => setIsMobileMenuOpen(false)}
          />
        )}

        {/* Sidebar */}
        <aside
          className={cn(
            "w-64 bg-white dark:bg-[#09090b] border-r border-gray-100 dark:border-[#2A2F3A] flex flex-col fixed top-11 md:top-12 bottom-0 left-0 z-40 shadow-sm dark:shadow-none transition-transform duration-300 ease-in-out lg:translate-x-0 hidden lg:flex",
            isMobileMenuOpen ? "flex translate-x-0" : "-translate-x-full",
          )}
        >
          <div className="p-4 flex flex-col gap-2 relative border-b border-gray-100 dark:border-[#2A2F3A] bg-gray-50 dark:bg-[#09090b]">
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
                              ? "bg-blue-50 dark:bg-blue-500/10 text-blue-700 dark:text-blue-400"
                              : "hover:bg-gray-50 dark:hover:bg-[#3f3f46] text-gray-700 dark:text-[#d4d4d8]",
                          )}
                        >
                          <div className="flex items-center gap-2 truncate">
                            <Building2 size={16} className="shrink-0" />
                            <span className="truncate">{comp.companyName}</span>
                          </div>
                          {activeCompanyId === comp.companyId && (
                            <Check
                              size={16}
                              className="shrink-0 text-blue-600 dark:text-blue-400"
                            />
                          )}
                        </button>
                      ))}
                    </div>
                  </>
                ) : (
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
                          <User size={16} />
                          <span>Administrador</span>
                        </div>
                        {activeRole === "admin" && (
                          <UserCheck
                            size={16}
                            className="text-blue-600 dark:text-blue-400"
                          />
                        )}
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
                          <Truck size={16} />
                          <span>Motorista</span>
                        </div>
                        {activeRole === "driver" && (
                          <UserCheck
                            size={16}
                            className="text-green-600 dark:text-green-400"
                          />
                        )}
                      </button>
                    </div>
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
                  ? "border-blue-200 bg-blue-50 dark:bg-blue-500/10 dark:border-blue-500/20/50"
                  : "border-gray-100 dark:border-[#2A2F3A] bg-white dark:bg-[#09090b] hover:bg-gray-50 dark:hover:bg-[#3f3f46]",
              )}
            >
              <div className="flex items-center gap-3">
                {resolveProfilePhoto(currentUser) ? (
                  <StableImage
                    src={resolveProfilePhoto(currentUser)}
                    alt={currentUser.name}
                    loading="eager"
                    decoding="async"
                    fetchPriority="high"
                    wrapperClassName="w-9 h-9 rounded-full bg-gray-200 dark:bg-white/10 shrink-0"
                    className="object-cover"
                    referrerPolicy="no-referrer"
                    fallback={
                      <span className="h-full w-full bg-blue-600 flex items-center justify-center text-white font-bold text-sm">
                        {currentUser?.name?.substring(0, 2).toUpperCase() || "AD"}
                      </span>
                    }
                  />
                ) : (
                  <div className="w-9 h-9 rounded-full bg-blue-600 flex items-center justify-center text-white font-bold text-sm shrink-0">
                    {currentUser?.name?.substring(0, 2).toUpperCase() || "AD"}
                  </div>
                )}
                <div className="flex-1 min-w-0 pr-2">
                  <p className="text-sm font-bold text-gray-900 dark:text-[#fafafa] truncate">
                    {currentUser?.name}
                  </p>
                  <p className="text-[11px] text-[#0cb49f] dark:text-[#0cb49f] font-semibold truncate leading-tight mt-0.5">
                    Administrador
                  </p>
                </div>
              </div>
              <ChevronDown
                size={16}
                className={cn(
                  "text-gray-400 transition-transform duration-200",
                  isProfileMenuOpen
                    ? "rotate-180 text-[#0cb49f] dark:text-[#0cb49f]"
                    : "",
                )}
              />
            </div>
          </div>

          <nav className="flex-1 px-4 py-6 space-y-6 overflow-y-auto">
            {navGroups.map((group, idx) => (
              <div key={idx}>
                {group.title && (
                  <h3 className="px-4 text-[10px] font-bold text-gray-500 dark:text-[#a1a1aa] uppercase tracking-widest mb-3">
                    {group.title}
                  </h3>
                )}
                <div className="space-y-1">
                  {group.items.map((item) => (
                    <NavLink
                      key={item.path}
                      to={item.path}
                      end={(item as any).exact}
                    onClick={(event) => handleSidebarNavigation(event, item.path)}
                      className={({ isActive }) =>
                        cn(
                          "flex items-center justify-between px-4 py-2.5 rounded-xl text-sm font-medium transition-colors",
                          isActive
                            ? "bg-[#0cb49f]/10 dark:bg-[#0cb49f]/10 text-[#0cb49f] dark:text-[#0cb49f]"
                            : "text-gray-600 dark:text-[#d4d4d8] hover:bg-gray-50 dark:hover:bg-[#3f3f46] hover:text-gray-900 dark:hover:text-[#f4f4f5]",
                        )
                      }
                    >
                      <div className="flex items-center gap-3">
                        <item.icon size={18} strokeWidth={2} />
                        {item.label}
                      </div>
                    </NavLink>
                  ))}
                </div>
              </div>
            ))}
          </nav>

          <div className="p-4 flex flex-col gap-2 relative border-t border-gray-100 dark:border-[#2A2F3A]">
            <div className="mx-2 mb-1 p-3 bg-white dark:bg-[#09090b] border border-gray-100 dark:border-[#2A2F3A]/80 shadow-sm dark:shadow-none rounded-2xl flex items-center justify-between">
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-full bg-gray-50 dark:bg-[#27272a] border border-gray-100 dark:border-[#2A2F3A] flex items-center justify-center text-gray-500 dark:text-[#a1a1aa]">
                  {theme === "dark" ? <Moon size={15} /> : <Sun size={15} />}
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
            {hasSeniorPanelAccess && (
              <button
                onClick={() => {
                  navigate("/admin/senior");
                  setIsMobileMenuOpen(false);
                }}
                className="flex items-center gap-3 px-4 py-2.5 rounded-xl text-sm font-medium text-gray-600 dark:text-[#d4d4d8] hover:bg-gray-50 dark:bg-[#09090b] dark:hover:bg-[#3f3f46] hover:text-gray-900 dark:hover:text-[#f4f4f5] transition-colors"
              >
                <Crown size={18} />
                Painel Sênior
              </button>
            )}
            <button
              onClick={handleLogout}
              className="flex items-center gap-3 px-4 py-2.5 rounded-xl text-sm font-medium text-gray-600 dark:text-[#d4d4d8] hover:bg-gray-50 dark:bg-[#09090b] dark:hover:bg-[#3f3f46] hover:text-gray-900 dark:hover:text-[#f4f4f5] transition-colors"
            >
              <LogOut size={18} />
              Sair
            </button>
          </div>
        </aside>

        {/* Main Content Viewport */}
        <main className="flex-1 min-w-0 lg:ml-64 flex flex-col min-h-[calc(100vh-3.5rem)] md:min-h-[calc(100vh-4rem)] max-w-full">
          <div
            className={cn(
              "flex-1",
              isSeniorPanelRoute
                ? "px-0 pt-0 pb-4 sm:px-2 sm:pt-1 md:px-4 md:pt-2"
                : isNewsRoute
                  ? "p-2 sm:p-4 md:p-6"
                  : "p-4 sm:p-6 md:p-10",
            )}
          >
            <div className="max-w-6xl mx-auto">
              {!isSeniorPanelRoute &&
              !isManualRoute &&
              !activeCompanyId &&
              companies.length > 0 &&
              !location.pathname.includes("/admin/fleet") ? (
                <div className="text-center py-12 bg-white dark:bg-[#09090b] rounded-3xl border border-gray-200 dark:border-[#2A2F3A] shadow-sm dark:shadow-none">
                  <h2 className="text-2xl font-bold text-gray-900 dark:text-[#fafafa] mb-2">
                    Selecione uma Frota
                  </h2>
                  <p className="text-gray-500 dark:text-[#a1a1aa] mb-6">
                    Você precisa selecionar uma frota ativa na página de Gestão
                    da Frota para continuar.
                  </p>
                  <button
                    onClick={() =>
                      navigate("/admin/fleet", {
                        state: { activeTab: "operations" },
                      })
                    }
                    className="px-6 py-2.5 bg-blue-600 hover:bg-blue-700 text-white rounded-xl font-medium transition-colors"
                  >
                    Ir para Gestão da Frota
                  </button>
                </div>
              ) : !isSeniorPanelRoute &&
                !isManualRoute &&
                !activeCompanyId &&
                companies.length === 0 &&
                !location.pathname.includes("/admin/fleet") ? (
                <div className="text-center py-12 bg-white dark:bg-[#09090b] rounded-3xl border border-gray-200 dark:border-[#2A2F3A] shadow-sm dark:shadow-none">
                  <div className="mx-auto w-16 h-16 bg-blue-50 dark:bg-blue-500/10 dark:border-blue-500/20 text-blue-500 flex items-center justify-center rounded-full mb-4">
                    <Truck size={32} />
                  </div>
                  <h2 className="text-2xl font-bold text-gray-900 dark:text-[#fafafa] mb-2">
                    Nenhuma frota cadastrada
                  </h2>
                  <p className="text-gray-500 dark:text-[#a1a1aa] mb-6">
                    Crie sua primeira empresa/frota para começar a gerenciar.
                  </p>
                  <button
                    onClick={() =>
                      navigate("/admin/fleet", {
                        state: { activeTab: "operations" },
                      })
                    }
                    className="px-6 py-2.5 bg-blue-600 hover:bg-blue-700 text-white rounded-xl font-medium transition-colors"
                  >
                    Criar Primeira Frota
                  </button>
                </div>
              ) : (
                <Outlet />
              )}
            </div>
          </div>
        </main>
      </div>

      <ProfileModal
        isOpen={isProfileModalOpen}
        onClose={() => setIsProfileModalOpen(false)}
      />
    </div>
  );
}
