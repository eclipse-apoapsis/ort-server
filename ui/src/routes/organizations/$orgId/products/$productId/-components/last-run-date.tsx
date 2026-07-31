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

import { QueryBoundary } from '@/components/query-boundary';
import { TimestampWithUTC } from '@/components/timestamp-with-utc';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { useLatestRepositoryRun } from '@/hooks/use-latest-repository-run';

export const LastRunDate = ({ repoId }: { repoId: number }) => (
  <QueryBoundary
    fallback={
      <div className='flex min-h-10 flex-col gap-1'>
        <Skeleton className='h-4 w-32' />
        <Skeleton className='h-4 w-20' />
      </div>
    }
    resetKey={repoId}
  >
    <LastRunDateInner repoId={repoId} />
  </QueryBoundary>
);

const LastRunDateInner = ({ repoId }: { repoId: number }) => {
  const run = useLatestRepositoryRun(repoId);

  if (!run) return <div className='min-h-10' />;

  return (
    <div className='flex min-h-10 flex-col items-start'>
      {run.finishedAt ? (
        <TimestampWithUTC timestamp={run.finishedAt} />
      ) : (
        <TimestampWithUTC className='italic' timestamp={run.createdAt} />
      )}
      {run.userDisplayName &&
        (run.userDisplayName.username ? (
          <Tooltip>
            <TooltipTrigger className='cursor-pointer'>
              {run.userDisplayName.fullName}
            </TooltipTrigger>
            <TooltipContent>{run.userDisplayName.username}</TooltipContent>
          </Tooltip>
        ) : (
          <span>{run.userDisplayName.fullName}</span>
        ))}
    </div>
  );
};
