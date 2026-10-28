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
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { DefaultPlugins, PreconfiguredPluginDescriptor } from '@/api';
import {
  getDefaultPluginsForRepository,
  getPluginsForRepository,
} from '@/api/sdk.gen';
import { useCreateRunPlugins } from '@/hooks/use-create-run-plugins';
import { createPluginDescriptor } from '../fixtures/create-run';
import { createDeferred } from '../fixtures/loader-test-utils';

vi.mock('@/api/sdk.gen', () => ({
  getDefaultPluginsForRepository: vi.fn(),
  getPluginsForRepository: vi.fn(),
}));

type PluginsResult = Awaited<ReturnType<typeof getPluginsForRepository>>;
type DefaultPluginsResult = Awaited<
  ReturnType<typeof getDefaultPluginsForRepository>
>;

const advisorDefaults: DefaultPlugins[] = [
  { category: 'ADVISOR', pluginIds: ['OSV'], source: 'BUILT_IN' },
];

/** Let the requests for the default plugins return the given defaults. */
const mockDefaultPlugins = (defaultPlugins: DefaultPlugins[]) =>
  vi
    .mocked(getDefaultPluginsForRepository)
    .mockResolvedValue({ data: defaultPlugins } as DefaultPluginsResult);

// The default plugins query retries once on its own, so let it retry without waiting.
const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: false, retryDelay: 0 } },
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
  beforeEach(() => {
    mockDefaultPlugins(advisorDefaults);
  });

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
        pluginsLoadFailed: true,
        pluginsError: error,
      })
    );
  });

  it('loads the default plugins for the config context', async () => {
    const requests = deferPluginRequests();

    const { result } = renderHook(() => useCreateRunPlugins(3, 'ctx'), {
      wrapper,
    });
    act(() => requests[0]!([osv]));

    await waitFor(() =>
      expect(result.current).toMatchObject({
        plugins: [osv],
        defaultPlugins: advisorDefaults,
        pluginsLoading: false,
      })
    );
    expect(getDefaultPluginsForRepository).toHaveBeenCalledWith(
      expect.objectContaining({
        path: { repositoryId: 3 },
        query: { configContext: 'ctx' },
      })
    );
  });

  it('reports the plugins as loading until the default plugins are available', async () => {
    const deferredDefaults = createDeferred<DefaultPluginsResult>();
    vi.mocked(getDefaultPluginsForRepository).mockReturnValue(
      deferredDefaults.promise as ReturnType<
        typeof getDefaultPluginsForRepository
      >
    );
    const requests = deferPluginRequests();

    const { result } = renderHook(() => useCreateRunPlugins(3, ''), {
      wrapper,
    });
    act(() => requests[0]!([osv]));

    await waitFor(() => expect(result.current.plugins).toEqual([osv]));
    expect(result.current.pluginsLoading).toBe(true);

    act(() =>
      deferredDefaults.resolve({
        data: advisorDefaults,
      } as DefaultPluginsResult)
    );

    await waitFor(() =>
      expect(result.current).toMatchObject({
        defaultPlugins: advisorDefaults,
        pluginsLoading: false,
      })
    );
  });

  it('reports the plugins as failed if the default plugins cannot be loaded', async () => {
    const error = new Error('Default plugins unavailable');
    vi.mocked(getDefaultPluginsForRepository).mockRejectedValue(error);
    vi.mocked(getPluginsForRepository).mockResolvedValue({
      data: [osv],
    } as PluginsResult);

    const { result } = renderHook(() => useCreateRunPlugins(3, ''), {
      wrapper,
    });

    await waitFor(() =>
      expect(result.current).toMatchObject({
        plugins: [osv],
        defaultPlugins: undefined,
        pluginsLoading: false,
        pluginsLoadFailed: true,
        pluginsError: null,
        defaultPluginsError: error,
      })
    );
  });
});
