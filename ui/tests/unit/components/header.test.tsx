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

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import type { AuthContextProps } from 'react-oidc-context';
import { describe, expect, it, vi } from 'vitest';

import { getServerSettingByKey, getSuperuser } from '@/api/sdk.gen';
import { Header } from '@/components/header';
import { ThemeProvider } from '@/components/theme-provider';
import { TooltipProvider } from '@/components/ui/tooltip';
import { Route as ProfileRoute } from '@/routes/profile';
import { Route as TokenRoute } from '@/routes/profile/token';
import { Route as TokenCallbackRoute } from '@/routes/profile/token/callback';
import { isCompiledByReactCompiler } from '../fixtures/react-compiler';

const auth = vi.hoisted(() => ({ current: undefined as unknown }));

vi.mock('react-oidc-context', () => ({ useAuth: () => auth.current }));

vi.mock('@tanstack/react-router', () => ({
  createFileRoute: () => (options: unknown) => ({ options }),
  Link: ({ children }: { children: ReactNode }) => <a>{children}</a>,
  useNavigate: () => vi.fn(),
  useRouterState: () => [],
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  getServerSettingByKey: vi.fn(),
  getSuperuser: vi.fn(),
}));

const signedInAs = (name: string): AuthContextProps =>
  ({
    user: { profile: { name, preferred_username: name.toLowerCase() } },
    signoutRedirect: vi.fn(),
  }) as unknown as AuthContextProps;

const renderHeader = () => {
  vi.mocked(getServerSettingByKey).mockResolvedValue({
    data: { isEnabled: false, value: null },
  } as Awaited<ReturnType<typeof getServerSettingByKey>>);
  vi.mocked(getSuperuser).mockResolvedValue({
    data: false,
  } as Awaited<ReturnType<typeof getSuperuser>>);
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const tree = () => (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <TooltipProvider>
          <Header />
        </TooltipProvider>
      </ThemeProvider>
    </QueryClientProvider>
  );
  const { rerender } = render(tree());

  return { rerender: () => rerender(tree()) };
};

const openUserMenu = async (user: ReturnType<typeof userEvent.setup>) =>
  user.click(await screen.findByRole('button', { name: /Toggle user menu/ }));

describe('authentication UI', () => {
  it.each([
    ['Header', Header],
    ['ProfilePage', ProfileRoute.options.component],
    ['TokenPage', TokenRoute.options.component],
    ['TokenCallbackPage', TokenCallbackRoute.options.component],
  ])('compiles %s with React Compiler', (_, component) => {
    expect(isCompiledByReactCompiler(component)).toBe(true);
  });
});

describe('Header', () => {
  it('shows the signed-in user and follows a change of the user', async () => {
    const user = userEvent.setup();
    auth.current = signedInAs('Ada Lovelace');
    const { rerender } = renderHeader();

    await openUserMenu(user);

    expect(await screen.findByText('Ada Lovelace')).toBeInTheDocument();

    await user.keyboard('{Escape}');
    auth.current = signedInAs('Grace Hopper');
    rerender();
    await openUserMenu(user);

    expect(await screen.findByText('Grace Hopper')).toBeInTheDocument();
    expect(screen.queryByText('Ada Lovelace')).toBeNull();
  });

  it('signs out with the current auth context', async () => {
    const user = userEvent.setup();
    const first = signedInAs('Ada Lovelace');
    const second = signedInAs('Ada Lovelace');
    auth.current = first;
    const { rerender } = renderHeader();

    auth.current = second;
    rerender();
    await openUserMenu(user);
    await user.click(await screen.findByRole('menuitem', { name: 'Logout' }));

    expect(first.signoutRedirect).not.toHaveBeenCalled();
    expect(second.signoutRedirect).toHaveBeenCalled();
  });
});
