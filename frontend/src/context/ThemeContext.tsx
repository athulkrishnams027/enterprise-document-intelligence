import React, { createContext, useContext, useMemo, useState } from 'react';
import { CssBaseline, ThemeProvider, createTheme } from '@mui/material';

interface ThemeContextValue {
  mode: 'light' | 'dark';
  toggleMode: () => void;
}

const ThemeModeContext = createContext<ThemeContextValue | undefined>(undefined);

export function AppThemeProvider({ children }: { children: React.ReactNode }) {
  const [mode, setMode] = useState<'light' | 'dark'>(() => {
    const saved = localStorage.getItem('docintel_theme');
    return saved === 'dark' ? 'dark' : 'light';
  });

  const toggleMode = () => {
    setMode((prev) => {
      const next = prev === 'light' ? 'dark' : 'light';
      localStorage.setItem('docintel_theme', next);
      return next;
    });
  };

  const theme = useMemo(
    () =>
      createTheme({
        palette: {
          mode,
          primary: {
            main: '#1565c0',
            dark: '#0d47a1',
            light: '#1e88e5',
          },
          secondary: {
            main: '#0288d1',
          },
          background: {
            default: mode === 'light' ? '#f4f7fb' : '#0b1220',
            paper: mode === 'light' ? '#ffffff' : '#111a2e',
          },
        },
        shape: {
          borderRadius: 10,
        },
        typography: {
          fontFamily: '"Inter", "Roboto", "Helvetica", "Arial", sans-serif',
          h5: { fontWeight: 700 },
          h6: { fontWeight: 600 },
        },
        components: {
          MuiCard: {
            styleOverrides: {
              root: {
                border: mode === 'light' ? '1px solid #e2e8f0' : '1px solid #1e293b',
                boxShadow: mode === 'light' ? '0 1px 3px rgba(15, 23, 42, 0.05)' : 'none',
              },
            },
          },
          MuiButton: {
            styleOverrides: {
              root: {
                textTransform: 'none',
                fontWeight: 600,
              },
            },
          },
        },
      }),
    [mode]
  );

  return (
    <ThemeModeContext.Provider value={{ mode, toggleMode }}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        {children}
      </ThemeProvider>
    </ThemeModeContext.Provider>
  );
}

export function useThemeMode(): ThemeContextValue {
  const ctx = useContext(ThemeModeContext);
  if (!ctx) throw new Error('useThemeMode must be used inside AppThemeProvider');
  return ctx;
}
