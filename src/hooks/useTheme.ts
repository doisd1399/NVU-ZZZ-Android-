import { useEffect, useState } from "react";

type Theme = "light" | "dark";

const readStoredTheme = (): Theme => {
  try {
    return localStorage.getItem("frotalog-theme") === "dark" ? "dark" : "light";
  } catch {
    // Storage can be unavailable in embedded/private contexts. Theme must never
    // prevent the application from mounting.
    return "light";
  }
};

export function useTheme() {
  const [theme, setTheme] = useState<Theme>(readStoredTheme);

  useEffect(() => {
    const root = window.document.documentElement;
    root.classList.remove("light", "dark");
    root.classList.add(theme);

    document
      .querySelector('meta[name="theme-color"]')
      ?.setAttribute("content", theme === "dark" ? "#09090b" : "#f8fafc");

    try {
      localStorage.setItem("frotalog-theme", theme);
    } catch {
      // The visual theme still applies even when persistence is unavailable.
    }
  }, [theme]);

  const toggleTheme = () => {
    setTheme((prev) => (prev === "light" ? "dark" : "light"));
  };

  return { theme, toggleTheme };
}
