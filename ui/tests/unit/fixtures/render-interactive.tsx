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

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { afterEach } from 'vitest';

import {
  createTestRouter,
  RouterTestProvider,
  type CreateTestRouterOptions,
} from './router-harness';

export interface RenderInteractiveWithRouterOptions extends CreateTestRouterOptions {
  withQueryClient?: boolean;
}

const testQueryClients = new Set<QueryClient>();

export const renderInteractiveWithRouter = (
  ui: ReactNode,
  {
    withQueryClient = false,
    ...routerOptions
  }: RenderInteractiveWithRouterOptions
) => {
  const routes = routerOptions.routes ?? [{ path: '/' }];
  const router = createTestRouter({
    ...routerOptions,
    routes: routes.map((route) => ({
      ...route,
      component: route.component ?? (() => ui),
    })),
  });
  const queryClient = withQueryClient
    ? new QueryClient({
        defaultOptions: {
          mutations: { retry: false },
          queries: { retry: false },
        },
      })
    : undefined;

  if (queryClient) {
    testQueryClients.add(queryClient);
  }

  const result = render(
    queryClient ? (
      <QueryClientProvider client={queryClient}>
        <RouterTestProvider router={router} />
      </QueryClientProvider>
    ) : (
      <RouterTestProvider router={router} />
    )
  );

  return {
    ...result,
    queryClient,
    router,
    user: userEvent.setup(),
  };
};

afterEach(() => {
  testQueryClients.forEach((queryClient) => queryClient.clear());
  testQueryClients.clear();
});
