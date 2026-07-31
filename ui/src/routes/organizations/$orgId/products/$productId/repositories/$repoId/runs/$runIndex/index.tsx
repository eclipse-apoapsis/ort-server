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
import { createFileRoute, Link } from '@tanstack/react-router';
import { Bug, ListTree, Scale, ShieldQuestion } from 'lucide-react';
import type { ReactNode } from 'react';

import type { JobStatus } from '@/api';
import { getRepositoryRunOptions } from '@/api/@tanstack/react-query.gen';
import { LoadingIndicator } from '@/components/loading-indicator';
import { QueryBoundary, QueryErrorFallback } from '@/components/query-boundary';
import { StatisticsCard } from '@/components/statistics-card';
import { Skeleton } from '@/components/ui/skeleton';
import { config } from '@/config';
import { getStatusFontColor } from '@/helpers/get-status-class';
import { IssuesStatisticsCard } from './-components/issues-statistics-card';
import { PackagesStatisticsCard } from './-components/packages-statistics-card';
import { RuleViolationsStatisticsCard } from './-components/rule-violations-statistics-card';
import { VulnerabilitiesStatisticsCard } from './-components/vulnerabilities-statistics-card';

type RunStatisticsFallbackProps = {
  analyzerStatus: JobStatus | undefined;
  advisorStatus: JobStatus | undefined;
  evaluatorStatus: JobStatus | undefined;
  value: ReactNode;
};

export const RunStatisticsFallback = ({
  analyzerStatus,
  advisorStatus,
  evaluatorStatus,
  value,
}: RunStatisticsFallbackProps) => (
  <div className='flex flex-col gap-4'>
    <div className='flex flex-col gap-2'>
      <h2 className='text-lg font-semibold'>Status</h2>
      <div className='grid grid-cols-3 gap-2'>
        <StatisticsCard
          title='Rule Violations'
          icon={() => (
            <Scale
              className={`h-4 w-4 ${getStatusFontColor(evaluatorStatus)}`}
            />
          )}
          value={value}
          className='hover:bg-muted/50 h-full'
        />
        <StatisticsCard
          title='Vulnerabilities'
          icon={() => (
            <ShieldQuestion
              className={`h-4 w-4 ${getStatusFontColor(advisorStatus)}`}
            />
          )}
          value={value}
          className='hover:bg-muted/50 h-full'
        />
        <StatisticsCard
          title='Issues'
          icon={() => (
            <Bug className={`h-4 w-4 ${getStatusFontColor(analyzerStatus)}`} />
          )}
          value={value}
          className='hover:bg-muted/50 h-full'
        />
      </div>
    </div>
    <div className='flex flex-col gap-2'>
      <h2 className='text-lg font-semibold'>Statistics</h2>
      <div className='grid grid-cols-3 gap-2'>
        <StatisticsCard
          title='Packages'
          icon={() => (
            <ListTree
              className={`h-4 w-4 ${getStatusFontColor(analyzerStatus)}`}
            />
          )}
          value={value}
          className='hover:bg-muted/50 h-full'
        />
      </div>
    </div>
  </div>
);

const RunComponent = () => {
  const params = Route.useParams();
  const pollInterval = config.pollInterval;

  const { data: ortRun } = useSuspenseQuery({
    ...getRepositoryRunOptions({
      path: {
        repositoryId: Number.parseInt(params.repoId),
        ortRunIndex: Number.parseInt(params.runIndex),
      },
    }),
    refetchInterval: (run) => {
      if (
        run.state.data?.status === 'FINISHED' ||
        run.state.data?.status === 'FINISHED_WITH_ISSUES' ||
        run.state.data?.status === 'FAILED'
      )
        return false;
      return pollInterval;
    },
  });

  return (
    <QueryBoundary
      fallback={
        <RunStatisticsFallback
          analyzerStatus={ortRun.jobs.analyzer?.status}
          advisorStatus={ortRun.jobs.advisor?.status}
          evaluatorStatus={ortRun.jobs.evaluator?.status}
          value={<Skeleton className='h-8 w-12' />}
        />
      }
      errorFallback={(props) => (
        <RunStatisticsFallback
          analyzerStatus={ortRun.jobs.analyzer?.status}
          advisorStatus={ortRun.jobs.advisor?.status}
          evaluatorStatus={ortRun.jobs.evaluator?.status}
          value={<QueryErrorFallback {...props} />}
        />
      )}
      resetKey={ortRun.id}
    >
      <div className='flex flex-col gap-4'>
        {/* Status section */}
        <div className='flex flex-col gap-2'>
          <h2 className='text-lg font-semibold'>Status</h2>
          <div className='grid grid-cols-3 gap-2'>
            <Link
              to='/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/rule-violations'
              params={{
                orgId: params.orgId,
                productId: params.productId,
                repoId: params.repoId,
                runIndex: params.runIndex,
              }}
              search={{
                sortBy: [{ id: 'severity', desc: true }],
                itemResolved: ['Unresolved'],
              }}
            >
              <RuleViolationsStatisticsCard
                jobIncluded={ortRun.jobConfigs.evaluator !== undefined}
                runId={ortRun.id}
                status={ortRun.jobs.evaluator?.status}
              />
            </Link>
            <Link
              to='/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/vulnerabilities'
              params={{
                orgId: params.orgId,
                productId: params.productId,
                repoId: params.repoId,
                runIndex: params.runIndex,
              }}
              search={{
                sortBy: [{ id: 'rating', desc: true }],
                itemResolved: ['Unresolved'],
              }}
            >
              <VulnerabilitiesStatisticsCard
                jobIncluded={ortRun.jobConfigs.advisor !== undefined}
                runId={ortRun.id}
                status={ortRun.jobs.advisor?.status}
              />
            </Link>
            <Link
              to='/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/issues'
              params={{
                orgId: params.orgId,
                productId: params.productId,
                repoId: params.repoId,
                runIndex: params.runIndex,
              }}
              search={{
                sortBy: [{ id: 'severity', desc: true }],
                itemResolved: ['Unresolved'],
              }}
            >
              <IssuesStatisticsCard
                jobIncluded={ortRun.jobConfigs.analyzer !== undefined}
                runId={ortRun.id}
                status={ortRun.jobs.analyzer?.status}
              />
            </Link>
          </div>
        </div>

        {/* Statistics section */}
        <div className='flex flex-col gap-2'>
          <h2 className='text-lg font-semibold'>Statistics</h2>
          <div className='grid grid-cols-3 gap-2'>
            <Link
              to='/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/packages'
              params={{
                orgId: params.orgId,
                productId: params.productId,
                repoId: params.repoId,
                runIndex: params.runIndex,
              }}
            >
              <PackagesStatisticsCard
                jobIncluded={ortRun.jobConfigs.analyzer !== undefined}
                runId={ortRun.id}
                status={ortRun.jobs.analyzer?.status}
              />
            </Link>
          </div>
        </div>
      </div>
    </QueryBoundary>
  );
};

export const Route = createFileRoute(
  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/'
)({
  component: RunComponent,
  pendingComponent: LoadingIndicator,
});
