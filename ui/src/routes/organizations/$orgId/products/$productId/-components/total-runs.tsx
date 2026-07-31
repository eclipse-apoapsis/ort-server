/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import { useSuspenseQuery } from '@tanstack/react-query';

import { getRepositoryRunsOptions } from '@/api/@tanstack/react-query.gen';
import { QueryBoundary } from '@/components/query-boundary';
import { Skeleton } from '@/components/ui/skeleton';
import { config } from '@/config';

export const TotalRuns = ({ repoId }: { repoId: number }) => (
  <QueryBoundary fallback={<Skeleton className='h-5 w-6' />} resetKey={repoId}>
    <TotalRunsInner repoId={repoId} />
  </QueryBoundary>
);

const TotalRunsInner = ({ repoId }: { repoId: number }) => {
  const { data: runs } = useSuspenseQuery({
    ...getRepositoryRunsOptions({
      path: { repositoryId: repoId },
      query: { limit: 1, sort: '-index' },
    }),
    refetchInterval: (query) => {
      const latestRun = query.state.data?.data[0];
      if (latestRun?.finishedAt) return undefined;

      return config.pollInterval;
    },
  });

  return <div className='min-h-5'>{runs.pagination.totalCount}</div>;
};
