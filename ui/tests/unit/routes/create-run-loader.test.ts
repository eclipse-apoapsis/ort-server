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

import { describe, expect, it, vi } from 'vitest';

import { getAvailableRepositorySecrets, getRepositoryRun } from '@/api/sdk.gen';
import { Route } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/create-run/index';
import { createOrtRun } from '../fixtures/create-run';
import {
  createDeferred,
  getQueryKeyRequest,
} from '../fixtures/loader-test-utils';

vi.mock('@/api/sdk.gen', () => ({
  getAvailableRepositorySecrets: vi.fn(),
  getRepositoryRun: vi.fn(),
}));

const params = { orgId: '1', productId: '2', repoId: '3' };
const loader = Route.options.loader as unknown as (
  options: unknown
) => Promise<unknown>;

describe('create run loader', () => {
  it('does not wait for the plugins when creating a new run', async () => {
    const pending = createDeferred();
    const prefetchQuery = vi.fn().mockReturnValue(pending.promise);
    vi.mocked(getAvailableRepositorySecrets).mockResolvedValue({
      data: [],
    } as unknown as Awaited<ReturnType<typeof getAvailableRepositorySecrets>>);

    const result = await loader({
      params,
      deps: { rerunIndex: undefined },
      context: { queryClient: { prefetchQuery } },
    });

    expect(result).toEqual({ ortRun: null, secrets: { data: [] } });
    expect(getRepositoryRun).not.toHaveBeenCalled();
    expect(prefetchQuery).toHaveBeenCalledOnce();
    const request = getQueryKeyRequest(prefetchQuery.mock.calls[0]![0]);
    expect(request._id).toBe('getPluginsForRepository');
    expect(request.path).toEqual({ repositoryId: 3 });
    expect(request.query).toBeUndefined();
  });

  it('prefetches plugins for the config context of a rerun', async () => {
    const prefetchQuery = vi.fn().mockResolvedValue(undefined);
    vi.mocked(getRepositoryRun).mockResolvedValue({
      data: createOrtRun({ jobConfigContext: 'ctx' }),
    } as Awaited<ReturnType<typeof getRepositoryRun>>);
    vi.mocked(getAvailableRepositorySecrets).mockResolvedValue({
      data: [],
    } as unknown as Awaited<ReturnType<typeof getAvailableRepositorySecrets>>);

    await loader({
      params,
      deps: { rerunIndex: 7 },
      context: { queryClient: { prefetchQuery } },
    });

    expect(getRepositoryRun).toHaveBeenCalledWith({
      path: { repositoryId: 3, ortRunIndex: 7 },
    });
    const request = getQueryKeyRequest(prefetchQuery.mock.calls[0]![0]);
    expect(request._id).toBe('getPluginsForRepository');
    expect(request.query).toEqual({ configContext: 'ctx' });
  });
});
