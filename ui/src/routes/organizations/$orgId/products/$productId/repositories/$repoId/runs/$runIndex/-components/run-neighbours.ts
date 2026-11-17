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

import { getRepositoryRunsQueryKey } from '@/api/@tanstack/react-query.gen';
import { getRepositoryRuns } from '@/api/sdk.gen';

/** The most runs the API returns in a single page. */
const WINDOW_SIZE = 100;

export type RunNeighbours = {
  previous: number | null;
  next: number | null;
};

/**
 * The offset of the page that ends two positions after the latest position a run can be at. Besides
 * the next run, the page then also holds the run after it, so that the neighbours of the next run
 * are known as well and moving forward finds them ready.
 */
const pageEndingAfter = (latestPosition: number) =>
  Math.max(0, latestPosition + 2 - (WINDOW_SIZE - 1));

/** The neighbours of a run that does not exist. */
const NO_NEIGHBOURS: RunNeighbours = { previous: null, next: null };

/**
 * Find the indexes of the runs before and after the run with the given index in a repository.
 *
 * The API cannot filter the runs by index, but it can tell where a run is: the indexes of a
 * repository count up from 1, and deleting a run only leaves a gap, so in the runs sorted by index
 * the run with index N is at position N - 1 or before it, and never past the last position. This
 * asks for the page of runs that ends two positions after that, which holds the run and both its
 * neighbours unless almost a whole page of earlier runs has been deleted. In that case, it asks for
 * an earlier page, until it has found the run or reached the first run.
 *
 * The page holds the neighbours of most of the other runs on it as well, so they are returned too,
 * keyed by the index of the run. A run is missing from the result if it does not exist, or if one of
 * its neighbours could be on another page.
 */
export async function findRunNeighbours(
  repositoryId: number,
  index: number,
  signal?: AbortSignal
): Promise<Map<number, RunNeighbours>> {
  let offset = pageEndingAfter(index - 1);

  for (;;) {
    const { data } = await getRepositoryRuns({
      path: { repositoryId },
      query: { limit: WINDOW_SIZE, offset, sort: 'index' },
      signal,
      throwOnError: true,
    });
    const indexes = data.data.map((run) => run.index);

    // The run is before this page, or is its first entry, so that its previous run is not on it.
    const first = indexes.at(0);
    const startsTooLate = offset > 0 && (first === undefined || first >= index);

    if (!startsTooLate) {
      const isFirstPage = offset === 0;
      const isLastPage = offset + indexes.length >= data.pagination.totalCount;
      const neighbours = new Map<number, RunNeighbours>();

      indexes.forEach((runIndex, position) => {
        const previous = indexes.at(position - 1);
        const next = indexes.at(position + 1);

        // At either end of the page, the neighbour is known only if the page is also the end of
        // the runs.
        if (position === 0 && !isFirstPage) return;
        if (next === undefined && !isLastPage) return;

        neighbours.set(runIndex, {
          previous: position > 0 ? (previous ?? null) : null,
          next: next ?? null,
        });
      });

      return neighbours;
    }

    // The run is at the first position of this page at the latest, and the total tells where the
    // runs end, which skips the pages past the end that deleted runs would otherwise lead to.
    offset = pageEndingAfter(Math.min(offset, data.pagination.totalCount - 1));
  }
}

/**
 * The query options for the neighbours of a run. The key starts with the key of the list of runs of
 * the repository, so that refreshing that list after a run was created or deleted refreshes the
 * neighbours too.
 *
 * The neighbours of the other runs on the same page are stored for those runs, so that moving to
 * the previous or next run finds its links ready instead of waiting for them.
 */
export const runNeighboursOptions = (repositoryId: number, index: number) =>
  queryOptions({
    queryKey: runNeighboursKey(repositoryId, index),
    queryFn: async ({ client, signal }) => {
      const neighbours = await findRunNeighbours(repositoryId, index, signal);

      neighbours.forEach((runNeighbours, runIndex) => {
        if (runIndex !== index) {
          client.setQueryData(
            runNeighboursKey(repositoryId, runIndex),
            runNeighbours
          );
        }
      });

      return neighbours.get(index) ?? NO_NEIGHBOURS;
    },
  });

const runNeighboursKey = (repositoryId: number, index: number) => [
  ...getRepositoryRunsQueryKey({ path: { repositoryId } }),
  { neighboursOf: index },
];
