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

import { ALL_ITEMS } from '@/lib/constants';
import { Route as DependenciesRoute } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/dependencies/index';
import { Route as ProjectsRoute } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/projects/index';
import { getQueryKeyRequest } from '../fixtures/loader-test-utils';

const params = {
  orgId: '1',
  productId: '2',
  repoId: '3',
  runIndex: '7',
};

describe('run subpage loaders', () => {
  it('prefetches dependency graph data for the resolved run', async () => {
    const fetchQuery = vi.fn().mockResolvedValue({ id: 123, index: 7 });
    const prefetchQuery = vi.fn().mockResolvedValue(undefined);

    const loader = DependenciesRoute.options.loader as unknown as (
      options: unknown
    ) => Promise<void>;

    await loader({
      context: {
        queryClient: { fetchQuery, prefetchQuery },
      },
      params,
    });

    const runRequest = getQueryKeyRequest(fetchQuery.mock.calls[0]![0]);
    expect(runRequest._id).toBe('getRepositoryRun');
    expect(runRequest.path).toEqual({
      repositoryId: 3,
      ortRunIndex: 7,
    });

    const dependencyGraphRequest = getQueryKeyRequest(
      prefetchQuery.mock.calls[0]![0]
    );
    expect(dependencyGraphRequest._id).toBe('getRunDependencyGraph');
    expect(dependencyGraphRequest.path).toEqual({ runId: 123 });
  });

  it('prefetches project data for the resolved run', async () => {
    const ensureQueryData = vi.fn().mockResolvedValue({ id: 123, index: 7 });
    const prefetchQuery = vi.fn().mockResolvedValue(undefined);

    const loader = ProjectsRoute.options.loader as unknown as (
      options: unknown
    ) => Promise<void>;

    await loader({
      context: {
        queryClient: { ensureQueryData, prefetchQuery },
      },
      params,
    });

    const runRequest = getQueryKeyRequest(ensureQueryData.mock.calls[0]![0]);
    expect(runRequest._id).toBe('getRepositoryRun');
    expect(runRequest.path).toEqual({
      repositoryId: 3,
      ortRunIndex: 7,
    });

    const projectsRequest = getQueryKeyRequest(prefetchQuery.mock.calls[0]![0]);
    expect(projectsRequest._id).toBe('getRunProjects');
    expect(projectsRequest.path).toEqual({ runId: 123 });
    expect(projectsRequest.query).toEqual({ limit: ALL_ITEMS });
  });
});
