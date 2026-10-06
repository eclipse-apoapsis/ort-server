/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

// @vitest-environment jsdom

import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ColorThemeToggle } from '@/components/color-theme-toggle';
import { ModeToggle } from '@/components/mode-toggle';
import { ThemeProvider } from '@/components/theme-provider';
import { useTheme } from '@/components/theme-provider-context';
import { isCompiledByReactCompiler } from '../fixtures/react-compiler';

const root = document.documentElement;

const ThemeConsumer = () => {
  const { mode, setMode, colorTheme, setColorTheme } = useTheme();

  return (
    <>
      <span>
        {mode} {colorTheme}
      </span>
      <button onClick={() => setMode('dark')}>Dark</button>
      <button onClick={() => setMode('system')}>System</button>
      <button onClick={() => setColorTheme('shadcn')}>Shadcn</button>
      <button onClick={() => setColorTheme('default')}>Default</button>
    </>
  );
};

const renderThemeProvider = (children = <ThemeConsumer />) =>
  render(<ThemeProvider>{children}</ThemeProvider>);

const prefersDarkMode = (matches: boolean) =>
  vi.spyOn(window, 'matchMedia').mockImplementation(
    (media) =>
      ({
        media,
        matches,
        addEventListener: () => {},
        removeEventListener: () => {},
      }) as unknown as MediaQueryList
  );

describe('ThemeProvider', () => {
  afterEach(() => {
    localStorage.clear();
    root.className = '';
    root.removeAttribute('data-theme');
    vi.restoreAllMocks();
  });

  it.each([
    ['ThemeProvider', ThemeProvider],
    ['ModeToggle', ModeToggle],
    ['ColorThemeToggle', ColorThemeToggle],
  ])('compiles %s with React Compiler', (_, component) => {
    expect(isCompiledByReactCompiler(component)).toBe(true);
  });

  it('passes a changed mode to its consumers and the document', async () => {
    const user = userEvent.setup();
    renderThemeProvider();

    expect(root).toHaveClass('light');

    await user.click(screen.getByRole('button', { name: 'Dark' }));

    expect(screen.getByText('dark default')).toBeInTheDocument();
    expect(root).toHaveClass('dark');
    expect(root).not.toHaveClass('light');
    expect(localStorage.getItem('vite-ui-theme')).toBe('dark');
  });

  it('follows the system preference in the system mode', async () => {
    const user = userEvent.setup();
    prefersDarkMode(true);
    renderThemeProvider();

    await user.click(screen.getByRole('button', { name: 'System' }));

    expect(screen.getByText('system default')).toBeInTheDocument();
    expect(root).toHaveClass('dark');
  });

  it('sets and clears the color theme of the document', async () => {
    const user = userEvent.setup();
    renderThemeProvider();

    await user.click(screen.getByRole('button', { name: 'Shadcn' }));

    expect(screen.getByText('light shadcn')).toBeInTheDocument();
    expect(root).toHaveAttribute('data-theme', 'shadcn');
    expect(localStorage.getItem('vite-ui-theme-color')).toBe('shadcn');

    await user.click(screen.getByRole('button', { name: 'Default' }));

    expect(root).not.toHaveAttribute('data-theme');
  });

  it('changes the mode of the document from the mode menu', async () => {
    const user = userEvent.setup();
    renderThemeProvider(<ModeToggle />);

    await user.click(screen.getByRole('button', { name: 'Toggle theme' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Dark' }));

    expect(root).toHaveClass('dark');
  });
});
