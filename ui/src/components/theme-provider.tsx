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
import { ColorTheme } from '@/lib/types';

export type Mode = 'dark' | 'light' | 'system';

type ThemeProviderProps = {
  children: React.ReactNode;
  defaultMode?: Mode;
  defaultColorTheme?: ColorTheme;
  storageKeyMode?: string;
  storageKeyColorTheme?: string;
};

export function ThemeProvider({
  children,
  defaultMode = 'light',
  defaultColorTheme = 'default',
  storageKeyMode = 'vite-ui-theme',
  storageKeyColorTheme = 'vite-ui-theme-color',
  ...props
}: ThemeProviderProps) {
  const [mode, setMode] = useState<Mode>(
    () => (localStorage.getItem(storageKeyMode) as Mode) || defaultMode
  );
  const [colorTheme, setColorTheme] = useState<ColorTheme>(
    () =>
      (localStorage.getItem(storageKeyColorTheme) as ColorTheme) ||
      defaultColorTheme
  );

  // Set mode classes.
  useEffect(() => {
    const root = window.document.documentElement;

    root.classList.remove('light', 'dark');

    if (mode === 'system') {
      const systemMode = window.matchMedia('(prefers-color-scheme: dark)')
        .matches
        ? 'dark'
        : 'light';

      root.classList.add(systemMode);
      return;
    }

    root.classList.add(mode);
  }, [mode]);

  // Set color theme classes.
  useEffect(() => {
    const root = window.document.documentElement;

    // If color theme is 'default', remove all color theme classes.
    if (colorTheme === 'default') {
      root.removeAttribute('data-theme');
    } else {
      root.setAttribute('data-theme', colorTheme);
    }
  }, [colorTheme]);

  const value = {
    mode,
    setMode: (mode: Mode) => {
      localStorage.setItem(storageKeyMode, mode);
      setMode(mode);
    },
    colorTheme,
    setColorTheme: (colorTheme: ColorTheme) => {
      localStorage.setItem(storageKeyColorTheme, colorTheme);
      setColorTheme(colorTheme);
    },
  };

  return (
    <ThemeProviderContext.Provider {...props} value={value}>
      {children}
    </ThemeProviderContext.Provider>
  );
}
