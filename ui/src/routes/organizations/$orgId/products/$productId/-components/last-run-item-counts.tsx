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
import { Link } from '@tanstack/react-router';
import { Bug, Loader2, Scale, ShieldQuestion } from 'lucide-react';

import { JobSummary, OrtRunSummary } from '@/api';
import {
  getRepositoryRunsOptions,
  getRunStatisticsOptions,
} from '@/api/@tanstack/react-query.gen';
import { Button } from '@/components/ui/button';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { config } from '@/config';
import { isJobFinished } from '@/helpers/job-helpers';

type LastRunItemCountsProps = {
  repoId: number;
};

export const LastRunItemCounts = ({ repoId }: LastRunItemCountsProps) => {
  const {
    data: runs,
    isPending: runsIsPending,
    isError: runsIsError,
  } = useQuery({
    ...getRepositoryRunsOptions({
      path: { repositoryId: repoId },
      query: { limit: 1, sort: '-index' },
    }),
    refetchInterval: (query) => {
      const curData = query.state.data?.data;
      if (curData && curData[0] && curData[0].finishedAt) {
        return undefined;
      }
      return config.pollInterval;
    },
  });

  if (runsIsPending) {
    return (
      <>
        <span className='sr-only'>Loading...</span>
        <Loader2 size={16} className='mx-3 animate-spin' />
      </>
    );
  }

  if (runsIsError) {
    return <span>Error loading run.</span>;
  }

  if (!runs.data[0]) return null;

  const run = runs.data[0];

  return <LastRunItemCountsInner summary={run} />;
};

const LastRunItemCountsInner = ({ summary }: { summary: OrtRunSummary }) => {
  const showBadge = (jobSummary: JobSummary | null | undefined) => {
    return jobSummary != null && isJobFinished(jobSummary.status);
  };

  const statistics = useQuery({
    ...getRunStatisticsOptions({
      path: { runId: summary.id },
    }),
    refetchInterval: config.pollInterval,
  });

  return (
    <div className='grid grid-cols-3 gap-1'>
      {showBadge(summary.jobs.analyzer) && (
        <CountBadgeLink
          count={statistics.data?.issuesCount}
          icon={Bug}
          label='issues'
          colStart='col-start-1'
          to='/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/issues'
          summary={summary}
        />
      )}
      {showBadge(summary.jobs.advisor) && (
        <CountBadgeLink
          count={statistics.data?.vulnerabilitiesCount}
          icon={ShieldQuestion}
          label='vulnerabilities'
          colStart='col-start-2'
          to='/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/vulnerabilities'
          summary={summary}
        />
      )}
      {showBadge(summary.jobs.evaluator) && (
        <CountBadgeLink
          count={statistics.data?.ruleViolationsCount}
          icon={Scale}
          label='rule violations'
          colStart='col-start-3'
          to='/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/rule-violations'
          summary={summary}
        />
      )}
    </div>
  );
};

type CountBadgeLinkProps = {
  count: number | null | undefined;
  icon: typeof Bug;
  label: string;
  colStart: string;
  to: string;
  summary: OrtRunSummary;
};

const CountBadgeLink = ({
  count,
  icon: Icon,
  label,
  colStart,
  to,
  summary,
}: CountBadgeLinkProps) => {
  const displayCount = count == null ? '' : count > 99 ? '99+' : count;
  const tooltipText =
    count == null ? '' : count > 99 ? `View all ${count}` : 'View';

  return (
    <Tooltip>
      <TooltipTrigger asChild className={`${colStart} w-12`}>
        <Button
          variant='outline'
          size='xs'
          asChild
          className='flex justify-start'
        >
          <Link
            to={to}
            params={{
              orgId: summary.organizationId.toString(),
              productId: summary.productId.toString(),
              repoId: summary.repositoryId.toString(),
              runIndex: summary.index.toString(),
            }}
            search={{ itemResolved: ['Unresolved'] }}
          >
            <Icon className='size-3' />
            <div className='text-xs'>{displayCount}</div>
          </Link>
        </Button>
      </TooltipTrigger>
      <TooltipContent>
        {tooltipText} {label} of run {summary.index}
      </TooltipContent>
    </Tooltip>
  );
};
