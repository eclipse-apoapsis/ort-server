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
import { act, render, screen } from '@testing-library/react';
import type { ComponentType } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { getCliOidcConfig } from '@/api/sdk.gen';
import { TooltipProvider } from '@/components/ui/tooltip';
import { Route as TokenCallbackRoute } from '@/routes/profile/token/callback';
import { exchangeOfflineToken } from '@/routes/profile/token/callback/-components/exchange-offline-token';

const navigate = vi.hoisted(() => vi.fn());

vi.mock('@tanstack/react-router', () => ({
  createFileRoute: () => (options: unknown) => ({ options }),
  useNavigate: () => navigate,
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  getCliOidcConfig: vi.fn(),
}));

vi.mock(
  '@/routes/profile/token/callback/-components/exchange-offline-token',
  () => ({ exchangeOfflineToken: vi.fn() })
);

const TokenCallbackPage = TokenCallbackRoute.options.component as ComponentType;

describe('TokenCallbackPage', () => {
  afterEach(() => {
    window.history.replaceState({}, '', '/');
  });

  it('exchanges the code once, also when it renders again meanwhile', async () => {
    window.history.replaceState(
      {},
      '',
      '/profile/token/callback?state=s&code=c'
    );
    vi.mocked(getCliOidcConfig).mockResolvedValue({
      data: { clientId: 'cli' },
    } as Awaited<ReturnType<typeof getCliOidcConfig>>);
    let resolveExchange: (token: string) => void = () => {};
    vi.mocked(exchangeOfflineToken).mockReturnValue(
      new Promise((resolve) => {
        resolveExchange = resolve;
      })
    );

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const tree = () => (
      <QueryClientProvider client={queryClient}>
        <TooltipProvider>
          <TokenCallbackPage />
        </TooltipProvider>
      </QueryClientProvider>
    );
    const { rerender } = render(tree());

    await vi.waitFor(() => expect(exchangeOfflineToken).toHaveBeenCalled());
    rerender(tree());
    await act(async () => resolveExchange('offline-token'));

    expect(await screen.findByDisplayValue('offline-token')).toBeVisible();
    expect(exchangeOfflineToken).toHaveBeenCalledOnce();
  });
});
