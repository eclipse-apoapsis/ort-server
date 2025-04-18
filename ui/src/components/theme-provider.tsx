/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

import { useEffect, useState } from 'react';

import { ThemeProviderContext } from '@/components/theme-provider-context';

export type Theme = 'dark' | 'light';
export type SelectedTheme = 'dark' | 'light' | 'system';

type ThemeProviderProps = {
  children: React.ReactNode;
  defaultTheme?: Theme;
  storageKey?: string;
};

export function ThemeProvider({
  children,
  defaultTheme = 'light',
  storageKey = 'vite-ui-theme',
  ...props
}: ThemeProviderProps) {
  const [selectedTheme, setSelectedTheme] = useState<SelectedTheme>(
    () => (localStorage.getItem(storageKey) as SelectedTheme) || defaultTheme
  );
  const [activeTheme, setActiveTheme] = useState<Theme>('light');

  useEffect(() => {
    const root = window.document.documentElement;

    root.classList.remove('light', 'dark');

    if (selectedTheme === 'system') {
      const systemTheme = window.matchMedia('(prefers-color-scheme: dark)')
        .matches
        ? 'dark'
        : 'light';

      root.classList.add(systemTheme);
      setActiveTheme(systemTheme);
      return;
    }

    root.classList.add(selectedTheme);
    setActiveTheme(selectedTheme);
  }, [selectedTheme]);

  const value = {
    activeTheme: activeTheme,
    selectedTheme,
    setTheme: (theme: SelectedTheme) => {
      localStorage.setItem(storageKey, theme);
      setSelectedTheme(theme);
    },
  };

  return (
    <ThemeProviderContext.Provider {...props} value={value}>
      {children}
    </ThemeProviderContext.Provider>
  );
}
