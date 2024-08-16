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
import { z } from 'zod';

import { useRepositoriesServiceGetOrtRunByIndexKey } from '@/api/queries';
import { RepositoriesService } from '@/api/requests';
import { LoadingIndicator } from '@/components/loading-indicator';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { calculateDuration } from '@/helpers/get-run-duration';
import { getStatusBackgroundColor } from '@/helpers/get-status-colors';
import { AdvisorJobDetails } from './-components/advisor-job-details';
import { AnalyzerJobDetails } from './-components/analyzer-job-details';
import { EvaluatorJobDetails } from './-components/evaluator-job-details';
import { NotifierJobDetails } from './-components/notifier-job-details';
import { ReporterJobDetails } from './-components/reporter-job-details';
import { ScannerJobDetails } from './-components/scanner-job-details';

const RunComponent = () => {
  const params = Route.useParams();
  const searchParams = Route.useSearch();
  const locale = navigator.language;

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
  });

  return (
    <>
      <div className='flex flex-1 flex-col gap-2'>
        <div className='flex flex-row gap-2'>
          <Card>
            <CardHeader>
              <CardTitle className='flex flex-row items-center justify-between'>
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
                    <span key={key}>
                      {key} = {value},{' '}
                    </span>
                  ))}
                </div>
              )}
              {ortRun.jobConfigs.parameters && (
                <div className='text-sm'>
                  <Label className='font-semibold'>Parameters:</Label>{' '}
                  {Object.entries(ortRun.jobConfigs.parameters).map(
                    ([key, value]) => (
                      <span key={key}>
                        {key} = {value},{' '}
                      </span>
                    )
                  )}
                </div>
              )}
            </CardContent>
          </Card>
        </div>
        <Card className='flex flex-1 overflow-hidden'>
          <CardHeader>
            <CardTitle>Issues</CardTitle>
            <CardContent className='space-y-2 overflow-auto'>
              <p>
                Lorem ipsum dolor, sit amet consectetur adipisicing elit.
                Possimus nobis necessitatibus amet deleniti quia quis
                consequuntur cumque impedit accusantium dolorem, eos inventore
                in sed magni dolorum nemo repellendus voluptates velit.
              </p>
              <p>
                Lorem ipsum dolor, sit amet consectetur adipisicing elit.
                Possimus nobis necessitatibus amet deleniti quia quis
                consequuntur cumque impedit accusantium dolorem, eos inventore
                in sed magni dolorum nemo repellendus voluptates velit.
              </p>
              <p>
                Lorem ipsum dolor, sit amet consectetur adipisicing elit.
                Possimus nobis necessitatibus amet deleniti quia quis
                consequuntur cumque impedit accusantium dolorem, eos inventore
                in sed magni dolorum nemo repellendus voluptates velit.
              </p>
              <p>
                Lorem ipsum dolor, sit amet consectetur adipisicing elit.
                Possimus nobis necessitatibus amet deleniti quia quis
                consequuntur cumque impedit accusantium dolorem, eos inventore
                in sed magni dolorum nemo repellendus voluptates velit.
              </p>
              <p>
                Lorem ipsum dolor, sit amet consectetur adipisicing elit.
                Possimus nobis necessitatibus amet deleniti quia quis
                consequuntur cumque impedit accusantium dolorem, eos inventore
                in sed magni dolorum nemo repellendus voluptates velit.
              </p>
              <p>
                Lorem ipsum dolor, sit amet consectetur adipisicing elit.
                Possimus nobis necessitatibus amet deleniti quia quis
                consequuntur cumque impedit accusantium dolorem, eos inventore
                in sed magni dolorum nemo repellendus voluptates velit.
              </p>
              <p>
                Lorem ipsum dolor, sit amet consectetur adipisicing elit.
                Possimus nobis necessitatibus amet deleniti quia quis
                consequuntur cumque impedit accusantium dolorem, eos inventore
                in sed magni dolorum nemo repellendus voluptates velit.
              </p>
            </CardContent>
          </CardHeader>
        </Card>
      </div>
      <div className='flex w-4/12'>
        <Tabs defaultValue={searchParams.job}>
          <TabsList className='flex-wrap justify-start bg-white'>
            <TabsTrigger
              value='analyzer'
              className={`border font-semibold text-white ${getStatusBackgroundColor(ortRun.jobs.analyzer?.status)}`}
            >
              <Link search={() => ({ job: 'analyzer' })}>Analyzer</Link>
            </TabsTrigger>
            <TabsTrigger
              value='advisor'
              className={`border font-semibold text-white ${getStatusBackgroundColor(ortRun.jobs.advisor?.status)}`}
            >
              <Link search={() => ({ job: 'advisor' })}>Advisor</Link>
            </TabsTrigger>
            <TabsTrigger
              value='scanner'
              className={`border font-semibold text-white ${getStatusBackgroundColor(ortRun.jobs.scanner?.status)}`}
            >
              <Link search={() => ({ job: 'scanner' })}>Scanner</Link>
            </TabsTrigger>
            <TabsTrigger
              value='evaluator'
              className={`border font-semibold text-white ${getStatusBackgroundColor(ortRun.jobs.evaluator?.status)}`}
            >
              <Link search={() => ({ job: 'evaluator' })}>Evaluator</Link>
            </TabsTrigger>
            <TabsTrigger
              value='reporter'
              className={`border font-semibold text-white ${getStatusBackgroundColor(ortRun.jobs.reporter?.status)}`}
            >
              <Link search={() => ({ job: 'reporter' })}>Reporter</Link>
            </TabsTrigger>
            <TabsTrigger
              value='notifier'
              className={`border font-semibold text-white ${getStatusBackgroundColor(ortRun.jobs.notifier?.status)}`}
            >
              <Link search={() => ({ job: 'notifier' })}>Notifier</Link>
            </TabsTrigger>
          </TabsList>
          <TabsContent value='analyzer' className='mt-10'>
            <AnalyzerJobDetails run={ortRun} />
          </TabsContent>
          <TabsContent value='advisor' className='mt-10'>
            <AdvisorJobDetails run={ortRun} />
          </TabsContent>
          <TabsContent value='scanner' className='mt-10'>
            <ScannerJobDetails run={ortRun} />
          </TabsContent>
          <TabsContent value='evaluator' className='mt-10'>
            <EvaluatorJobDetails run={ortRun} />
          </TabsContent>
          <TabsContent value='reporter' className='mt-10'>
            <ReporterJobDetails run={ortRun} />
          </TabsContent>
          <TabsContent value='notifier' className='mt-10'>
            <NotifierJobDetails run={ortRun} />
          </TabsContent>
        </Tabs>
      </div>
    </>
  );
};

// These are the only valid search parameters for this route.
const jobSearchSchema = z.object({
  job: z
    .enum([
      'analyzer',
      'advisor',
      'scanner',
      'evaluator',
      'reporter',
      'notifier',
    ])
    .catch('analyzer'),
});

export const Route = createFileRoute(
  '/_layout/organizations/$orgId/products/$productId/repositories/$repoId/_layout/runs2/$runIndex/'
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
  validateSearch: jobSearchSchema,
});
