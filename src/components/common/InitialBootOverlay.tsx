import React, { useEffect, useLayoutEffect, useRef, useState } from "react";
import { useSessionStore } from "../../context/AppContext";
import { preloadRoute } from "../../lib/routePreload";
import { waitForRankingWarmup } from "../../lib/rankingPhotoWarmup";
import { getRuntimePerformanceProfile } from "../../lib/runtimePerformance";

const INITIAL_BOOT_ROOT_CLASS = "nvu-initial-boot-active";
const INITIAL_BUSY_LAYER_SELECTOR =
  "[data-nvu-route-loading], [data-nvu-role-transition]";
const STABLE_FRAMES_REQUIRED = 2;

const shouldCoverInitialPath = (pathname: string) =>
  /^\/(?:admin|driver)(?:\/|$)/.test(pathname) ||
  /^\/(?:ranking|select-profile)\/?$/.test(pathname);

const readInitialPathname = () =>
  typeof window === "undefined" ? "/" : window.location.pathname;

/**
 * Owns the visual identity for the complete first workspace boot.
 *
 * Session hydration, a protected-route gate and a lazy page can finish in
 * separate React commits. Keeping this one overlay mounted across those
 * commits prevents each stage from painting its own NVU and restarting the
 * progress animation.
 */
export default function InitialBootOverlay() {
  const { authInitialized, sessionReady, currentUser } = useSessionStore();
  const initialPathRef = useRef(readInitialPathname());
  const [visible, setVisible] = useState(() =>
    shouldCoverInitialPath(initialPathRef.current),
  );
  const frameRef = useRef<number | null>(null);
  const stableFramesRef = useRef(0);
  const rankingWarmupResolvedRef = useRef(
    !/^\/(?:ranking)\/?$/.test(initialPathRef.current),
  );

  useLayoutEffect(() => {
    if (!visible || typeof document === "undefined") return;

    document.documentElement.classList.add(INITIAL_BOOT_ROOT_CLASS);

    // Start the deep-link chunk before Firebase finishes restoring the
    // session. The overlay still waits for the actual route DOM to become
    // stable, so a failed best-effort preload cannot reveal an empty frame.
    void preloadRoute(initialPathRef.current).catch(() => {
      // The route's normal lazy boundary and deploy recovery own retry/error
      // handling. Preloading must never block session restoration.
    });

    return () => {
      document.documentElement.classList.remove(INITIAL_BOOT_ROOT_CLASS);
    };
  }, [visible]);

  useEffect(() => {
    if (!visible || !authInitialized || !sessionReady) return;

    let disposed = false;
    if (
      !rankingWarmupResolvedRef.current &&
      currentUser?.id &&
      /^\/(?:ranking)\/?$/.test(initialPathRef.current)
    ) {
      const runtime = getRuntimePerformanceProfile();
      if (!runtime.allowRankingWarmup) {
        // Constrained mobile runtimes intentionally skip speculative ranking
        // work. The route owns its normal on-demand loading and must not wait
        // for a background barrier that was deliberately disabled.
        rankingWarmupResolvedRef.current = true;
      } else {
        void waitForRankingWarmup(currentUser.id).finally(() => {
          rankingWarmupResolvedRef.current = true;
        });
      }
    } else if (
      !rankingWarmupResolvedRef.current &&
      /^\/(?:ranking)\/?$/.test(initialPathRef.current) &&
      !currentUser?.id
    ) {
      // An unauthenticated deep link will be redirected by the protected
      // route; there is no account-scoped ranking barrier to await.
      rankingWarmupResolvedRef.current = true;
    } else if (!/^\/(?:ranking)\/?$/.test(initialPathRef.current)) {
      rankingWarmupResolvedRef.current = true;
    }

    const observeStableDestination = () => {
      if (disposed) return;

      const hasPendingVisualLayer = Boolean(
        document.querySelector(INITIAL_BUSY_LAYER_SELECTOR),
      );
      stableFramesRef.current = hasPendingVisualLayer
        ? 0
        : stableFramesRef.current + 1;

      if (
        stableFramesRef.current >= STABLE_FRAMES_REQUIRED &&
        rankingWarmupResolvedRef.current
      ) {
        frameRef.current = null;
        setVisible(false);
        return;
      }

      frameRef.current = window.requestAnimationFrame(
        observeStableDestination,
      );
    };

    frameRef.current = window.requestAnimationFrame(observeStableDestination);

    return () => {
      disposed = true;
      if (frameRef.current !== null) {
        window.cancelAnimationFrame(frameRef.current);
        frameRef.current = null;
      }
    };
  }, [authInitialized, currentUser?.id, sessionReady, visible]);

  if (!visible) return null;

  return (
    <div
      data-nvu-initial-boot
      className="fixed inset-0 z-[2300] flex items-center justify-center bg-gray-50 dark:bg-[#09090b]"
      role="status"
      aria-live="polite"
      aria-label="Abrindo o NVU"
    >
      <div
        data-nvu-initial-boot-brand
        className="flex flex-col items-center gap-2 opacity-70"
      >
        <span className="text-lg font-bold tracking-[0.22em] text-slate-800 dark:text-white">
          NVU
        </span>
        <span
          className="h-0.5 w-10 overflow-hidden rounded-full bg-slate-200 dark:bg-white/10"
          aria-hidden="true"
        >
          <span className="block h-full w-1/2 rounded-full bg-blue-500 motion-safe:animate-[nvu-progress_900ms_ease-in-out_infinite]" />
        </span>
      </div>
    </div>
  );
}
