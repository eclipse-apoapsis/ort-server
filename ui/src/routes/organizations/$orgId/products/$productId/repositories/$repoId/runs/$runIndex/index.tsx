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
import { Repeat } from 'lucide-react';

import { prefetchUseRepositoriesServiceGetOrtRunByIndex } from '@/api/queries/prefetch';
import { useRepositoriesServiceGetOrtRunByIndexSuspense } from '@/api/queries/suspense';
import { LoadingIndicator } from '@/components/loading-indicator';
import { OrtRunJobStatus } from '@/components/ort-run-job-status';
import { TimestampWithUTC } from '@/components/timestamp-with-utc';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { config } from '@/config';
import { calculateDuration } from '@/helpers/get-run-duration';
import { getStatusBackgroundColor } from '@/helpers/get-status-class';
import { IssuesStatisticsCard } from './-components/issues-statistics-card';
import { PackagesStatisticsCard } from './-components/packages-statistics-card';
import { RuleViolationsStatisticsCard } from './-components/rule-violations-statistics-card';
import { VulnerabilitiesStatisticsCard } from './-components/vulnerabilities-statistics-card';

const RunComponent = () => {
  const params = Route.useParams();
  const pollInterval = config.pollInterval;

  const { data: ortRun } = useRepositoriesServiceGetOrtRunByIndexSuspense(
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
        <div className='flex flex-row gap-2'>
          <Card>
            <CardHeader>
              <CardTitle className='flex flex-col gap-2'>
                <div className='flex items-center justify-between'>
                  <Badge
                    className={`border ${getStatusBackgroundColor(ortRun.status)}`}
                  >
                    {ortRun.status}
                  </Badge>
                  <Button variant='outline' asChild size='sm'>
                    <Link
                      to='/organizations/$orgId/products/$productId/repositories/$repoId/create-run'
                      params={{
                        orgId: params.orgId,
                        productId: params.productId,
                        repoId: params.repoId,
                      }}
                      search={{
                        rerunIndex: ortRun.index,
                      }}
                    >
                      Rerun
                      <Repeat className='ml-1 h-4 w-4' />
                    </Link>
                  </Button>
                </div>
                <div>
                  <OrtRunJobStatus
                    jobs={ortRun.jobs}
                    orgId={params.orgId}
                    productId={params.productId}
                    repoId={params.repoId}
                    runIndex={params.runIndex}
                  />
                </div>
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className='text-sm'>
                <Label className='font-semibold'>Run index:</Label>{' '}
                {ortRun.index}
              </div>
              <div className='text-sm'>
                <Label className='font-semibold'>Global id:</Label> {ortRun.id}
              </div>
              <div className='text-sm'>
                <Label className='font-semibold'>Created at:</Label>{' '}
                <TimestampWithUTC timestamp={ortRun.createdAt} />
              </div>
              {ortRun.finishedAt && (
                <div>
                  <div className='text-sm'>
                    <Label className='font-semibold'>Finished at:</Label>{' '}
                    <TimestampWithUTC timestamp={ortRun.finishedAt} />
                  </div>
                  <div className='text-sm'>
                    <Label className='font-semibold'>Duration:</Label>{' '}
                    {calculateDuration(ortRun.createdAt, ortRun.finishedAt)}
                  </div>
                </div>
              )}
            </CardContent>
          </Card>
          <Card className='flex-1'>
            <CardHeader>
              <CardTitle>Context information</CardTitle>
            </CardHeader>
            <CardContent>
              <div className='text-sm'>
                <Label className='font-semibold'>Revision:</Label>{' '}
                {ortRun.revision}
              </div>
              {ortRun.path && (
                <div className='text-sm'>
                  <Label className='font-semibold'>Path:</Label> {ortRun.path}
                </div>
              )}
              {ortRun.jobConfigContext && (
                <div className='text-sm'>
                  <Label className='font-semibold'>
                    Job configuration context:
                  </Label>{' '}
                  {ortRun.jobConfigContext}
                </div>
              )}
              {ortRun.resolvedJobConfigContext && (
                <div className='text-sm'>
                  <Label className='font-semibold'>Resolved to:</Label>{' '}
                  {ortRun.resolvedJobConfigContext}
                </div>
              )}
              {Object.keys(ortRun.labels).length > 0 && (
                <div className='text-sm'>
                  <Label className='font-semibold'>Labels:</Label>{' '}
                  {Object.entries(ortRun.labels).map(([key, value]) => (
                    <div key={key} className='ml-4 grid grid-cols-12 text-xs'>
                      <div className='col-span-3 break-all text-left'>
                        {key}
                      </div>
                      <div className='col-span-1 text-center'>=</div>
                      <div className='col-span-8 break-all text-left'>
                        {value}
                      </div>
                    </div>
                  ))}
                </div>
              )}
              {ortRun.jobConfigs.parameters && (
                <div className='text-sm'>
                  <Label className='font-semibold'>Parameters:</Label>{' '}
                  {Object.entries(ortRun.jobConfigs.parameters).map(
                    ([key, value]) => (
                      <div key={key} className='ml-4 grid grid-cols-12 text-xs'>
                        <div className='col-span-3 break-all text-left'>
                          {key}
                        </div>
                        <div className='col-span-1 text-center'>=</div>
                        <div className='col-span-8 break-all text-left'>
                          {value}
                        </div>
                      </div>
                    )
                  )}
                </div>
              )}
            </CardContent>
          </Card>
        </div>
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
    await prefetchUseRepositoriesServiceGetOrtRunByIndex(context.queryClient, {
      repositoryId: Number.parseInt(params.repoId),
      ortRunIndex: Number.parseInt(params.runIndex),
    });
  },
  component: RunComponent,
  pendingComponent: LoadingIndicator,
});
