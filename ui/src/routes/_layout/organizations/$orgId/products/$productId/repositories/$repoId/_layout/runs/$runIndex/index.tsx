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
import { Bug, Repeat, ShieldQuestion } from 'lucide-react';

import { useVulnerabilitiesServiceGetVulnerabilitiesByRunId } from '@/api/queries';
import { prefetchUseRepositoriesServiceGetOrtRunByIndex } from '@/api/queries/prefetch';
import { useRepositoriesServiceGetOrtRunByIndexSuspense } from '@/api/queries/suspense';
import { LoadingIndicator } from '@/components/loading-indicator';
import { OrtRunJobStatus } from '@/components/ort-run-job-status';
import { StatisticsCard } from '@/components/statistics-card';
import { ToastError } from '@/components/toast-error';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { config } from '@/config';
import { calculateDuration } from '@/helpers/get-run-duration';
import {
  getStatusBackgroundColor,
  getStatusFontColor,
} from '@/helpers/get-status-class';
import { toast } from '@/lib/toast';
import { getOrtDateTimeString } from '@/lib/utils.ts';
import { PackagesStatisticsCard } from './-components/packages-statistics-card';
import { RuleViolationsStatisticsCard } from './-components/rule-violations-statistics-card';

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

  // Note that this is very inefficient as it fetches all data from the endpoints,
  // while for this purpose we only need the total counts, so this is a temporary solution.
  // The queries will be replaced with the ORT Run statistics query once it is implemented.

  const {
    data: vulnerabilities,
    isPending: vulnIsPending,
    isError: vulnIsError,
    error: vulnError,
  } = useVulnerabilitiesServiceGetVulnerabilitiesByRunId({
    runId: ortRun.id,
  });

  if (vulnIsPending) {
    return <LoadingIndicator />;
  }

  if (vulnIsError) {
    toast.error('Unable to load data', {
      description: <ToastError error={vulnError} />,
      duration: Infinity,
      cancel: {
        label: 'Dismiss',
        onClick: () => {},
      },
    });
    return;
  }

  const vulnTotal = vulnerabilities.pagination.totalCount;

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
                    pollInterval={pollInterval}
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
                {getOrtDateTimeString(ortRun.createdAt)}
              </div>
              {ortRun.finishedAt && (
                <div>
                  <div className='text-sm'>
                    <Label className='font-semibold'>Finished at:</Label>{' '}
                    {getOrtDateTimeString(ortRun.finishedAt)}
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
            to='/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/issues'
            params={{
              orgId: params.orgId,
              productId: params.productId,
              repoId: params.repoId,
              runIndex: params.runIndex,
            }}
          >
            <StatisticsCard
              title='Issues'
              icon={() => <Bug className={`h-4 w-4 text-gray-300`} />}
              value='Unavailable'
              className='h-full hover:bg-muted/50'
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
              runId={ortRun.id}
              status={ortRun.jobs.analyzer?.status}
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
          >
            <StatisticsCard
              title='Vulnerabilities'
              icon={() => (
                <ShieldQuestion
                  className={`h-4 w-4 ${getStatusFontColor(ortRun.jobs.advisor?.status)}`}
                />
              )}
              value={ortRun.jobs.advisor ? vulnTotal : 'Skipped'}
              description={
                ortRun.jobs.advisor ? '' : 'Enable the job for results'
              }
              className='h-full hover:bg-muted/50'
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
          >
            <RuleViolationsStatisticsCard
              runId={ortRun.id}
              status={ortRun.jobs.evaluator?.status}
            />
          </Link>
        </div>
        <Card className='flex flex-1 overflow-hidden'>
          <CardHeader>
            <CardTitle>Summary</CardTitle>
            <CardDescription>
              When the corresponding API endpoints have been implemented, this
              section will include a summary of the run, for example number of
              issues by severity, and some statistical data from the run.
            </CardDescription>
          </CardHeader>
        </Card>
      </div>
    </>
  );
};

export const Route = createFileRoute(
  '/_layout/organizations/$orgId/products/$productId/repositories/$repoId/_layout/runs/$runIndex/'
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
