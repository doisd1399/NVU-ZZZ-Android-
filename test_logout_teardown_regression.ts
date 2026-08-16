import fs from "node:fs";
import path from "node:path";

const roots = [".", "nvu26"];

for (const root of roots) {
  const contextPath = path.join(root, "src", "context", "AppContext.tsx");
  const source = fs.readFileSync(contextPath, "utf8");

  const logoutStart = source.indexOf("const logOutApp = async () =>");
  const logoutEnd = source.indexOf("const syncCompanyData", logoutStart);
  if (logoutStart < 0 || logoutEnd < 0) {
    throw new Error(`${contextPath}: logout block not found`);
  }

  const logoutBlock = source.slice(logoutStart, logoutEnd);
  const unsubscribeIndex = logoutBlock.indexOf("authenticatedUnsubscribes");
  const clearUserIndex = logoutBlock.indexOf("setCurrentUser(null)");
  const uiTeardownIndex = logoutBlock.indexOf("requestAnimationFrame");
  const signOutIndex = logoutBlock.indexOf("await signOut(auth)");

  if (
    unsubscribeIndex < 0 ||
    clearUserIndex < 0 ||
    uiTeardownIndex < 0 ||
    signOutIndex < 0
  ) {
    throw new Error(`${contextPath}: safe logout teardown markers are incomplete`);
  }

  if (
    !(
      unsubscribeIndex < clearUserIndex &&
      clearUserIndex < uiTeardownIndex &&
      uiTeardownIndex < signOutIndex
    )
  ) {
    throw new Error(
      `${contextPath}: authenticated listeners and UI must be torn down before signOut`,
    );
  }

  if (!logoutBlock.includes("if (isLoggingOutRef.current) return")) {
    throw new Error(`${contextPath}: duplicate logout guard is missing`);
  }

  if (logoutBlock.includes('console.error("Error during logOutApp signOut:"')) {
    throw new Error(`${contextPath}: legacy preview-crashing logout error remains`);
  }

  const requiredLifecycleMarkers = [
    "beginAuthTeardown()",
    "clearPushRegistrationContext()",
    "endAuthTeardown()",
    "isAuthTeardownActive()",
  ];

  for (const marker of requiredLifecycleMarkers) {
    if (!source.includes(marker)) {
      throw new Error(`${contextPath}: listener teardown marker missing: ${marker}`);
    }
  }

  const requiredResets = [
    "setIsSeniorAuthenticated(false)",
    "setSeniorCompanyId(null)",
    "setSequences([])",
    "pushRegisteredRef.current = false",
  ];

  for (const reset of requiredResets) {
    if (!logoutBlock.includes(reset)) {
      throw new Error(`${contextPath}: logout state reset missing: ${reset}`);
    }
  }
}

console.log(
  "Logout teardown regression passed: listeners stop, UI unmounts and only then Firebase Auth signs out.",
);
