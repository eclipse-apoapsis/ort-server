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

import { OrtRunJobStatus } from '@/components/ort-run-job-status';
import { QueryBoundary } from '@/components/query-boundary';
import { Skeleton } from '@/components/ui/skeleton';
import { useLatestRepositoryRun } from '@/hooks/use-latest-repository-run';

export const LastJobStatus = ({ repoId }: { repoId: number }) => (
  <QueryBoundary
    fallback={
      <div className='flex min-h-3 items-center space-x-1'>
        {Array.from({ length: 5 }).map((_, index) => (
          <Skeleton key={index} className='h-3 w-3 rounded-full' />
        ))}
      </div>
    }
    resetKey={repoId}
  >
    <LastJobStatusInner repoId={repoId} />
  </QueryBoundary>
);

const LastJobStatusInner = ({ repoId }: { repoId: number }) => {
  const run = useLatestRepositoryRun(repoId);

  if (!run) return <div className='min-h-3' />;

  return (
    <div className='min-h-3'>
      <OrtRunJobStatus
        jobs={run.jobs}
        orgId={run.organizationId.toString()}
        productId={run.productId.toString()}
        repoId={run.repositoryId.toString()}
        runIndex={run.index.toString()}
      />
    </div>
  );
};
