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

import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { JobStatus } from '@/api';
import { getRepositoryRuns, getRunProjects } from '@/api/sdk.gen';
import { analyzedRunProjectsOptions } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/create-run/-components/analyzed-run-projects';
import { createOrtRun, createProject } from '../fixtures/create-run';

vi.mock('@/api/sdk.gen', () => ({
  getRepositoryRuns: vi.fn(),
  getRunProjects: vi.fn(),
}));

const run = (id: number, status: JobStatus) => ({
  id,
  index: id,
  jobs: { analyzer: { status } },
});

const mockRuns = (...runs: ReturnType<typeof run>[]) =>
  vi.mocked(getRepositoryRuns).mockResolvedValue({
    data: { data: runs },
  } as unknown as Awaited<ReturnType<typeof getRepositoryRuns>>);

const mockProjects = (totalCount: number) =>
  vi.mocked(getRunProjects).mockImplementation((async ({
    query,
  }: Parameters<typeof getRunProjects>[0]) => {
    const offset = query?.offset ?? 0;
    const count = Math.max(0, Math.min(query?.limit ?? 0, totalCount - offset));

    return {
      data: {
        data: Array.from({ length: count }, () => createProject()),
        pagination: { totalCount },
      },
    };
  }) as unknown as typeof getRunProjects);

const load = (rerun: ReturnType<typeof createOrtRun> | null = null) => {
  const { queryFn } = analyzedRunProjectsOptions(3, rerun);
  return (queryFn as (context: { signal: AbortSignal }) => Promise<unknown>)({
    signal: new AbortController().signal,
  });
};

describe('analyzedRunProjectsOptions', () => {
  beforeEach(() => {
    vi.mocked(getRepositoryRuns).mockReset();
    vi.mocked(getRunProjects).mockReset();
  });

  it('reads the projects of the run being rerun', async () => {
    mockProjects(1);

    const result = await load(
      createOrtRun({
        id: 7,
        index: 4,
        jobs: { analyzer: { status: 'FINISHED' } },
      })
    );

    expect(result).toMatchObject({ runIndex: 4, capped: false });
    expect(getRepositoryRuns).not.toHaveBeenCalled();
    expect(getRunProjects).toHaveBeenCalledWith(
      expect.objectContaining({ path: { runId: 7 } })
    );
  });

  it('reads the projects of the newest run whose analyzer finished', async () => {
    mockRuns(
      run(9, 'FAILED'),
      run(8, 'FINISHED_WITH_ISSUES'),
      run(7, 'FINISHED')
    );
    mockProjects(1);

    const result = await load();

    expect(result).toMatchObject({ runIndex: 8 });
    expect(getRunProjects).toHaveBeenCalledWith(
      expect.objectContaining({ path: { runId: 8 } })
    );
  });

  it('returns null without a run whose analyzer finished', async () => {
    mockRuns(run(9, 'RUNNING'));

    expect(await load()).toBeNull();
    expect(getRunProjects).not.toHaveBeenCalled();
  });

  it('reads at most 500 projects', async () => {
    mockRuns(run(9, 'FINISHED'));
    mockProjects(1200);

    const result = (await load()) as { projects: unknown[]; capped: boolean };

    expect(result.projects).toHaveLength(500);
    expect(result.capped).toBe(true);
    expect(getRunProjects).toHaveBeenCalledTimes(5);
  });
});
