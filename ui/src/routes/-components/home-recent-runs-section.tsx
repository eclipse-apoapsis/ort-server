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

import { useQuery } from '@tanstack/react-query';
import { Link, type LinkProps } from '@tanstack/react-router';
import { AxiosError } from 'axios';
import { PlayCircle } from 'lucide-react';
import { useEffect } from 'react';

import { OrtRunStatus } from '@/api';
import {
  getRepositoryRunOptions,
  getRunStatisticsOptions,
} from '@/api/@tanstack/react-query.gen';
import { ItemCounts } from '@/components/item-counts';
import { OrtRunJobStatus } from '@/components/ort-run-job-status';
import { RunDuration } from '@/components/run-duration';
import { TimestampWithUTC } from '@/components/timestamp-with-utc';
import { Badge } from '@/components/ui/badge';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { config } from '@/config';
import { getStatusBackgroundColor } from '@/helpers/get-status-class';
import { isJobFinished } from '@/helpers/job-helpers';
import { RecentRunItem, useHomeRecentRunActions } from '@/providers/home-data';
import { HomeEmptyState } from './home-empty-state';

const isNotFoundError = (error: unknown) =>
  error instanceof AxiosError &&
  (error.status ?? error.response?.status) === 404;

// Stop polling once a run has reached a terminal state, matching the behavior
// of the run details page.
const isRunFinished = (status: OrtRunStatus | undefined) =>
  status === 'FINISHED' ||
  status === 'FINISHED_WITH_ISSUES' ||
  status === 'FAILED';

/** Render a recent run and remove it from the list if the run is deleted. */
const RecentRunListItem = ({ recentRun }: { recentRun: RecentRunItem }) => {
  const { removeRecentRun } = useHomeRecentRunActions();
  const runQuery = useQuery({
    ...getRepositoryRunOptions({
      path: {
        repositoryId: recentRun.repositoryId,
        ortRunIndex: recentRun.runIndex,
      },
    }),
    refetchInterval: (query) =>
      isRunFinished(query.state.data?.status) ? false : config.pollInterval,
    retry: (failureCount, error) => !isNotFoundError(error) && failureCount < 3,
  });

  useEffect(() => {
    if (isNotFoundError(runQuery.error)) {
      removeRecentRun(recentRun.id);
    }
  }, [recentRun.id, removeRecentRun, runQuery.error]);

  const run = runQuery.data;
  const statistics = useQuery({
    ...getRunStatisticsOptions({
      path: { runId: run?.id ?? recentRun.runId },
    }),
    enabled: Boolean(run?.jobs) && !isNotFoundError(runQuery.error),
    refetchInterval: isRunFinished(run?.status) ? false : config.pollInterval,
  });

  const status = run?.status ?? recentRun.status;
  const createdAt = run?.createdAt ?? recentRun.createdAt;
  const params = {
    orgId: recentRun.organizationId.toString(),
    productId: recentRun.productId.toString(),
    repoId: recentRun.repositoryId.toString(),
    runIndex: recentRun.runIndex.toString(),
  };

  return (
    <li className='space-y-1 rounded-lg border px-3 py-2 text-sm'>
      <div className='flex items-start justify-between gap-3'>
        <Link
          to={recentRun.to as LinkProps['to']}
          params={params as LinkProps['params']}
          className='min-w-0 font-medium hover:underline'
        >
          <span className='text-blue-400'>Run {recentRun.runIndex}</span>
          <span className='text-muted-foreground'> of </span>
          <span className='break-words'>
            {recentRun.organizationName} / {recentRun.productName} /{' '}
            {recentRun.repositoryName}
          </span>
        </Link>
        {status && (
          <Badge
            className={`shrink-0 border ${getStatusBackgroundColor(status)}`}
          >
            {status}
          </Badge>
        )}
      </div>

      <div className='flex items-center justify-between gap-3'>
        <div className='flex min-w-0 flex-wrap items-center gap-1'>
          <span className='text-muted-foreground'>Created at</span>
          {createdAt && <TimestampWithUTC timestamp={createdAt} />}
          {createdAt && (
            <div className='flex items-center gap-1'>
              (
              <RunDuration
                createdAt={createdAt}
                finishedAt={run?.finishedAt ?? undefined}
              />
              )
            </div>
          )}
        </div>
        {run?.jobs && (
          <div className='shrink-0'>
            <OrtRunJobStatus jobs={run.jobs} {...params} />
          </div>
        )}
      </div>

      <div className='flex min-h-6 justify-end'>
        {run?.jobs && (
          <ItemCounts
            statistics={statistics.data}
            compact
            showIssues={Boolean(isJobFinished(run.jobs.analyzer?.status))}
            showVulnerabilities={Boolean(
              isJobFinished(run.jobs.advisor?.status)
            )}
            showRuleViolations={Boolean(
              isJobFinished(run.jobs.evaluator?.status)
            )}
            link={{
              params,
              issuesSearch: {
                sortBy: [{ id: 'severity', desc: true }],
                itemResolved: ['Unresolved'],
              },
              vulnerabilitiesSearch: {
                sortBy: [{ id: 'rating', desc: true }],
                itemResolved: ['Unresolved'],
              },
              ruleViolationsSearch: {
                sortBy: [{ id: 'severity', desc: true }],
                itemResolved: ['Unresolved'],
              },
            }}
          />
        )}
      </div>
    </li>
  );
};

/** Render runs recently started from this browser UI. */
export const HomeRecentRunsSection = ({
  recentRuns,
}: {
  recentRuns: RecentRunItem[];
}) => (
  <Card>
    <CardHeader>
      <CardTitle className='flex items-center gap-2'>
        <PlayCircle className='h-5 w-5 text-white' />
        Recently started runs
      </CardTitle>
      <CardDescription>
        Runs started recently (max. 10) are shown here.
      </CardDescription>
    </CardHeader>
    <CardContent>
      {recentRuns.length > 0 ? (
        <ul className='space-y-2'>
          {recentRuns.map((recentRun) => (
            <RecentRunListItem key={recentRun.id} recentRun={recentRun} />
          ))}
        </ul>
      ) : (
        <HomeEmptyState>
          Runs you start from the UI in this browser will appear here.
        </HomeEmptyState>
      )}
    </CardContent>
  </Card>
);
