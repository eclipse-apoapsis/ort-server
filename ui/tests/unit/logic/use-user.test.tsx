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
import { act, renderHook } from '@testing-library/react';
import type { ReactNode } from 'react';
import type { AuthContextProps } from 'react-oidc-context';
import { describe, expect, it, vi } from 'vitest';

import { getSuperuser } from '@/api/sdk.gen';
import { authRef, useUser } from '@/hooks/use-user';
import { isCompiledByReactCompiler } from '../fixtures/react-compiler';

const auth = vi.hoisted(() => ({ current: undefined as unknown }));

vi.mock('react-oidc-context', () => ({ useAuth: () => auth.current }));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  getSuperuser: vi.fn(),
}));

const authContext = (
  name: string,
  overrides: Partial<AuthContextProps> = {}
): AuthContextProps =>
  ({
    user: { profile: { name, preferred_username: name.toLowerCase() } },
    signinSilent: vi.fn(),
    signinRedirect: vi.fn(),
    ...overrides,
  }) as unknown as AuthContextProps;

const renderUser = (context: AuthContextProps) => {
  auth.current = context;
  vi.mocked(getSuperuser).mockResolvedValue({
    data: false,
  } as Awaited<ReturnType<typeof getSuperuser>>);
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  return renderHook(() => useUser(), { wrapper });
};

describe('useUser', () => {
  it('is compiled by React Compiler', () => {
    expect(isCompiledByReactCompiler(useUser)).toBe(true);
  });

  it('keeps the current auth context for use outside of components', () => {
    const first = authContext('Ada');
    const second = authContext('Grace');
    const { result, rerender } = renderUser(first);

    expect(authRef.current).toBe(first);

    auth.current = second;
    rerender();

    expect(authRef.current).toBe(second);
    expect(result.current.fullName).toBe('Grace');
    expect(result.current.username).toBe('grace');
  });

  it('signs in again with a redirect when silent sign-in returns no user', async () => {
    const context = authContext('Ada', {
      signinSilent: vi.fn().mockResolvedValue(null),
    });
    vi.spyOn(console, 'warn').mockImplementation(() => {});
    const { result } = renderUser(context);

    await act(() => result.current.refreshUser());

    expect(context.signinRedirect).toHaveBeenCalledWith({
      redirect_uri: window.location.href,
    });
  });
});
