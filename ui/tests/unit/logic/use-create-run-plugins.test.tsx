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
import { act, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { PreconfiguredPluginDescriptor } from '@/api';
import { getPluginsForRepository } from '@/api/sdk.gen';
import { useCreateRunPlugins } from '@/hooks/use-create-run-plugins';
import { createPluginDescriptor } from '../fixtures/create-run';
import { createDeferred } from '../fixtures/loader-test-utils';

vi.mock('@/api/sdk.gen', () => ({
  getPluginsForRepository: vi.fn(),
}));

type PluginsResult = Awaited<ReturnType<typeof getPluginsForRepository>>;

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: false } },
});

const wrapper = ({ children }: { children: ReactNode }) => (
  <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
);

/** Let each plugin request wait until the test resolves it with the given plugins. */
const deferPluginRequests = () => {
  const requests: Array<(plugins: PreconfiguredPluginDescriptor[]) => void> =
    [];
  vi.mocked(getPluginsForRepository).mockImplementation(() => {
    const deferred = createDeferred<PluginsResult>();
    requests.push((plugins) =>
      deferred.resolve({ data: plugins } as PluginsResult)
    );
    return deferred.promise as ReturnType<typeof getPluginsForRepository>;
  });
  return requests;
};

const osv = createPluginDescriptor({ id: 'OSV', type: 'ADVISOR' });
const vulnerableCode = createPluginDescriptor({
  id: 'VulnerableCode',
  type: 'ADVISOR',
});

describe('useCreateRunPlugins', () => {
  afterEach(() => {
    queryClient.clear();
    vi.clearAllMocks();
  });

  it('reports the plugins as loading until they are available', async () => {
    const requests = deferPluginRequests();

    const { result } = renderHook(() => useCreateRunPlugins(3, ''), {
      wrapper,
    });

    expect(result.current).toMatchObject({
      plugins: [],
      pluginsLoading: true,
    });
    expect(getPluginsForRepository).toHaveBeenCalledWith(
      expect.objectContaining({ path: { repositoryId: 3 }, query: undefined })
    );

    act(() => requests[0]!([osv]));

    await waitFor(() =>
      expect(result.current).toMatchObject({
        plugins: [osv],
        pluginsLoading: false,
      })
    );
  });

  it('keeps the previous plugins while reloading them for a changed config context', async () => {
    const requests = deferPluginRequests();

    const { result, rerender } = renderHook(
      ({ configContext }) => useCreateRunPlugins(3, configContext),
      { initialProps: { configContext: '' }, wrapper }
    );
    act(() => requests[0]!([osv]));
    await waitFor(() => expect(result.current.pluginsLoading).toBe(false));

    rerender({ configContext: 'ctx' });

    await waitFor(() => expect(requests).toHaveLength(2));
    expect(getPluginsForRepository).toHaveBeenLastCalledWith(
      expect.objectContaining({ query: { configContext: 'ctx' } })
    );
    expect(result.current).toMatchObject({
      plugins: [osv],
      pluginsLoading: true,
    });

    act(() => requests[1]!([vulnerableCode]));

    await waitFor(() =>
      expect(result.current).toMatchObject({
        plugins: [vulnerableCode],
        pluginsLoading: false,
      })
    );
  });

  it('reports an error if the plugins cannot be loaded', async () => {
    const error = new Error('Config repository unavailable');
    vi.mocked(getPluginsForRepository).mockRejectedValue(error);

    const { result } = renderHook(() => useCreateRunPlugins(3, ''), {
      wrapper,
    });

    await waitFor(() =>
      expect(result.current).toMatchObject({
        plugins: [],
        pluginsLoading: false,
        pluginsError: error,
      })
    );
  });
});
