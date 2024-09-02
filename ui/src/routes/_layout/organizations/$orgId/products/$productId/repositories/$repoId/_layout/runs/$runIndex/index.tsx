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
import { Repeat } from 'lucide-react';

import { useRepositoriesServiceGetOrtRunByIndexKey } from '@/api/queries';
import { RepositoriesService } from '@/api/requests';
import { LoadingIndicator } from '@/components/loading-indicator';
import { OrtRunJobStatus } from '@/components/ort-run-job-status';
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
import { getStatusBackgroundColor } from '@/helpers/get-status-colors';

const RunComponent = () => {
  const params = Route.useParams();
  const locale = navigator.language;
  const pollInterval = config.pollInterval;

  const { data: ortRun } = useSuspenseQuery({
    queryKey: [
      useRepositoriesServiceGetOrtRunByIndexKey,
      params.repoId,
      params.runIndex,
    ],
    queryFn: async () =>
      await RepositoriesService.getOrtRunByIndex({
        repositoryId: Number.parseInt(params.repoId),
        ortRunIndex: Number.parseInt(params.runIndex),
      }),
    refetchInterval: (run) => {
      if (
        run.state.data?.status === 'FINISHED' ||
        run.state.data?.status === 'FAILED'
      )
        return false;
      return pollInterval;
    },
  });

  return (
    <>
      <div className='flex flex-1 flex-col gap-2'>
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
                {new Date(ortRun.createdAt).toLocaleString(locale)}
              </div>
              {ortRun.finishedAt && (
                <div>
                  <div className='text-sm'>
                    <Label className='font-semibold'>Finished at:</Label>{' '}
                    {new Date(ortRun.finishedAt).toLocaleString(locale)}
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
    await context.queryClient.ensureQueryData({
      queryKey: [
        useRepositoriesServiceGetOrtRunByIndexKey,
        params.repoId,
        params.runIndex,
      ],
      queryFn: () =>
        RepositoriesService.getOrtRunByIndex({
          repositoryId: Number.parseInt(params.repoId),
          ortRunIndex: Number.parseInt(params.runIndex),
        }),
    });
  },
  component: RunComponent,
  pendingComponent: LoadingIndicator,
});
