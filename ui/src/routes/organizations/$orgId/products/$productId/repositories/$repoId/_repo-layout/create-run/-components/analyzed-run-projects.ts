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

import { queryOptions } from '@tanstack/react-query';

import type { JobStatus, OrtRun, Project } from '@/api';
import { getRepositoryRuns, getRunProjects } from '@/api/sdk.gen';

/** The projects found by the analyzer in a run. */
export type AnalyzedRunProjects = {
  runIndex: number;
  projects: Project[];
  /** Whether the run has more projects than were read. */
  capped: boolean;
};

/** The number of the newest runs of a repository that are searched for one with analyzer results. */
const RUNS_TO_SEARCH = 10;
const PROJECTS_PAGE_SIZE = 100;
const MAX_PROJECTS = 500;

const hasAnalyzerResults = (status: JobStatus | undefined) =>
  status === 'FINISHED' || status === 'FINISHED_WITH_ISSUES';

const findAnalyzedRun = async (
  repositoryId: number,
  rerun: OrtRun | null,
  signal: AbortSignal
) => {
  if (rerun && hasAnalyzerResults(rerun.jobs.analyzer?.status)) {
    return { id: rerun.id, index: rerun.index };
  }

  const { data: runs } = await getRepositoryRuns({
    path: { repositoryId },
    query: { limit: RUNS_TO_SEARCH, sort: '-index' },
    signal,
    throwOnError: true,
  });

  return runs.data.find((run) => hasAnalyzerResults(run.jobs.analyzer?.status));
};

const readProjects = async (runId: number, signal: AbortSignal) => {
  const projects: Project[] = [];
  let totalCount = Infinity;

  while (projects.length < Math.min(totalCount, MAX_PROJECTS)) {
    const { data } = await getRunProjects({
      path: { runId },
      query: { limit: PROJECTS_PAGE_SIZE, offset: projects.length },
      signal,
      throwOnError: true,
    });
    projects.push(...data.data);
    totalCount = data.pagination.totalCount;
    if (data.data.length === 0) break;
  }

  return { projects, capped: totalCount > projects.length };
};

/**
 * The query options for the projects of the run that the suggestions for includes and excludes in
 * the create run form are based on: the run being rerun if it has analyzer results, otherwise the
 * newest run of the repository that has them. The data is null if there is no such run. The route
 * loader and the page share the options to use the same query.
 */
export const analyzedRunProjectsOptions = (
  repositoryId: number,
  rerun: OrtRun | null
) =>
  queryOptions({
    queryKey: [
      { _id: 'analyzedRunProjects', path: { repositoryId } },
      rerun?.id ?? null,
    ],
    queryFn: async ({ signal }): Promise<AnalyzedRunProjects | null> => {
      const run = await findAnalyzedRun(repositoryId, rerun, signal);
      if (!run) return null;

      return { runIndex: run.index, ...(await readProjects(run.id, signal)) };
    },
  });
