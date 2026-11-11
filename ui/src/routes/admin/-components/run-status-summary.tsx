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

import { useQueries, useQuery, useSuspenseQuery } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';

import { QueryBoundary, QueryErrorFallback } from '@/components/query-boundary';
import { Skeleton } from '@/components/ui/skeleton';
import { getStatusBackgroundColor } from '@/helpers/get-status-class';
import { cn } from '@/lib/utils';
import { formatCount } from './overview-format';
import {
  endStatuses,
  runsCountOptions,
  runStatusCountOptions,
} from './overview-queries';

type EndStatus = (typeof endStatuses)[number];

const statusLabels: Record<EndStatus, string> = {
  FINISHED: 'Finished',
  FINISHED_WITH_ISSUES: 'Finished with issues',
  FAILED: 'Failed',
};

const StatusDot = ({ status }: { status: EndStatus }) => (
  <span
    className={cn(
      'size-2.5 shrink-0 rounded-full',
      getStatusBackgroundColor(status)
    )}
    aria-hidden
  />
);

const StatusCountLink = ({
  status,
  children,
}: {
  status: EndStatus;
  children: (count: React.ReactNode) => React.ReactNode;
}) => {
  const { data } = useSuspenseQuery(runStatusCountOptions(status));
  const count = data.pagination.totalCount;

  return (
    <Link
      to='/admin/runs'
      search={{ status: [status] }}
      className={cn(
        'text-muted-foreground hover:text-foreground flex items-center gap-1.5 underline-offset-4 hover:underline'
      )}
    >
      {children(
        <span
          className={cn(count === 0 && 'text-muted-foreground font-normal')}
        >
          {formatCount(count)}
        </span>
      )}
    </Link>
  );
};

const StatusCount = ({ status }: { status: EndStatus }) => {
  const label = statusLabels[status];
  const item = (count: React.ReactNode) => (
    <>
      <StatusDot status={status} />
      <span>{label}</span>
      <span className='text-foreground font-semibold tabular-nums'>
        {count}
      </span>
    </>
  );

  return (
    <li>
      <QueryBoundary
        fallback={
          <span className='text-muted-foreground flex items-center gap-1.5'>
            {item(<Skeleton className='h-4 w-8' />)}
          </span>
        }
        errorFallback={(props) => (
          <span className='text-muted-foreground flex items-center gap-1.5'>
            {item(<QueryErrorFallback {...props} />)}
          </span>
        )}
        loadingLabel={`Loading the number of ${label.toLowerCase()} runs...`}
      >
        <StatusCountLink status={status}>{item}</StatusCountLink>
      </QueryBoundary>
    </li>
  );
};

/**
 * The share of each end status among all runs as one bar. It draws the statuses whose counts have
 * loaded, and the counts in the list below it carry the same numbers for reading and linking.
 */
const RunStatusBar = () => {
  const { data: runs } = useQuery(runsCountOptions());
  const counts = useQueries({
    queries: endStatuses.map(runStatusCountOptions),
  });

  const total = runs?.pagination.totalCount ?? 0;

  return (
    <div
      className='bg-muted flex h-2 w-full overflow-hidden rounded-full'
      aria-hidden
    >
      {total > 0 &&
        endStatuses.map((status, index) => {
          const count = counts[index]?.data?.pagination.totalCount ?? 0;

          return count > 0 ? (
            <div
              key={status}
              className={cn(
                'h-full transition-[width] duration-500 ease-out',
                getStatusBackgroundColor(status)
              )}
              style={{ width: `${(count / total) * 100}%` }}
            />
          ) : null;
        })}
    </div>
  );
};

/**
 * The number of runs per end status: a bar of their shares and a list of linked counts.
 */
export const RunStatusSummary = () => (
  <div className='flex min-w-0 flex-1 basis-80 flex-col gap-3 pt-2'>
    <RunStatusBar />
    <ul
      aria-label='Finished runs by status'
      className='flex flex-wrap gap-x-5 gap-y-2 text-sm'
    >
      {endStatuses.map((status) => (
        <StatusCount key={status} status={status} />
      ))}
    </ul>
  </div>
);
