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

import { createFileRoute, Link } from '@tanstack/react-router';

import { prefetchUseRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRunsByOrtRunIndex } from '@/api/queries/prefetch';
import { useRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRunsByOrtRunIndexSuspense } from '@/api/queries/suspense';
import { LoadingIndicator } from '@/components/loading-indicator';
import { config } from '@/config';
import { IssuesStatisticsCard } from './-components/issues-statistics-card';
import { PackagesStatisticsCard } from './-components/packages-statistics-card';
import { RuleViolationsStatisticsCard } from './-components/rule-violations-statistics-card';
import { VulnerabilitiesStatisticsCard } from './-components/vulnerabilities-statistics-card';

const RunComponent = () => {
  const params = Route.useParams();
  const pollInterval = config.pollInterval;

  const { data: ortRun } =
    useRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRunsByOrtRunIndexSuspense(
      {
        repositoryId: Number.parseInt(params.repoId),
        ortRunIndex: Number.parseInt(params.runIndex),
      },
      undefined,
      {
        refetchInterval: (run) => {
          if (
            run.state.data?.status === 'FINISHED' ||
            run.state.data?.status === 'FINISHED_WITH_ISSUES' ||
            run.state.data?.status === 'FAILED'
          )
            return false;
          return pollInterval;
        },
      }
    );

  return (
    <>
      <div className='flex flex-col gap-2'>
        <div className='grid grid-cols-4 gap-2'>
          <Link
            to='/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/vulnerabilities'
            params={{
              orgId: params.orgId,
              productId: params.productId,
              repoId: params.repoId,
              runIndex: params.runIndex,
            }}
            search={{ sortBy: [{ id: 'rating', desc: true }] }}
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
            search={{ sortBy: [{ id: 'severity', desc: true }] }}
          >
            <IssuesStatisticsCard
              jobIncluded={ortRun.jobConfigs.analyzer !== undefined}
              runId={ortRun.id}
              status={ortRun.jobs.analyzer?.status}
            />
          </Link>
          <Link
            to='/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/rule-violations'
            params={{
              orgId: params.orgId,
              productId: params.productId,
              repoId: params.repoId,
              runIndex: params.runIndex,
            }}
            search={{ sortBy: [{ id: 'severity', desc: true }] }}
          >
            <RuleViolationsStatisticsCard
              jobIncluded={ortRun.jobConfigs.evaluator !== undefined}
              runId={ortRun.id}
              status={ortRun.jobs.evaluator?.status}
            />
          </Link>
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
    </>
  );
};

export const Route = createFileRoute(
  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/'
)({
  loader: async ({ context, params }) => {
    await prefetchUseRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRunsByOrtRunIndex(
      context.queryClient,
      {
        repositoryId: Number.parseInt(params.repoId),
        ortRunIndex: Number.parseInt(params.runIndex),
      }
    );
  },
  component: RunComponent,
  pendingComponent: LoadingIndicator,
});
