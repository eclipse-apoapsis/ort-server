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

import { createFileRoute } from '@tanstack/react-router';

import { prefetchUseRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRunsByOrtRunIndex } from '@/api/queries/prefetch';
import { useRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRunsByOrtRunIndexSuspense } from '@/api/queries/suspense';
import { LoadingIndicator } from '@/components/loading-indicator';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { AdvisorJobDetails } from './-components/advisor-job-details';
import { AnalyzerJobDetails } from './-components/analyzer-job-details';
import { EvaluatorJobDetails } from './-components/evaluator-job-details';
import { NotifierJobDetails } from './-components/notifier-job-details';
import { ReporterJobDetails } from './-components/reporter-job-details';
import { ScannerJobDetails } from './-components/scanner-job-details';

const ConfigComponent = () => {
  const params = Route.useParams();

  const { data: ortRun } =
    useRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRunsByOrtRunIndexSuspense(
      {
        repositoryId: Number.parseInt(params.repoId),
        ortRunIndex: Number.parseInt(params.runIndex),
      }
    );

  return (
    <div className='flex flex-col gap-4'>
      <Card>
        <CardHeader>
          <CardTitle>Run Configuration</CardTitle>
        </CardHeader>
        <CardContent>
          {ortRun.path && (
            <div className='text-sm'>
              <Label className='font-semibold'>Path:</Label> {ortRun.path}
            </div>
          )}
          {(ortRun.jobConfigContext || ortRun.resolvedJobConfigContext) && (
            <div className='flex items-center gap-1 text-sm'>
              <Label className='font-semibold'>Configuration context:</Label>
              {ortRun.jobConfigContext && <div>{ortRun.jobConfigContext}</div>}
              {ortRun.resolvedJobConfigContext && (
                <div>({ortRun.resolvedJobConfigContext})</div>
              )}
            </div>
          )}
          {Object.keys(ortRun.labels).length > 0 && (
            <div className='text-sm'>
              <Label className='font-semibold'>Labels:</Label>{' '}
              {Object.entries(ortRun.labels).map(([key, value]) => (
                <div key={key} className='ml-4 grid grid-cols-12 text-xs'>
                  <div className='col-span-3 text-left break-all'>{key}</div>
                  <div className='col-span-1 text-center'>=</div>
                  <div className='col-span-8 text-left break-all'>{value}</div>
                </div>
              ))}
            </div>
          )}
          {ortRun.jobConfigs.parameters &&
            Object.keys(ortRun.jobConfigs.parameters).length > 0 && (
              <div className='text-sm'>
                <Label className='font-semibold'>Parameters:</Label>{' '}
                {Object.entries(ortRun.jobConfigs.parameters).map(
                  ([key, value]) => (
                    <div key={key} className='ml-4 grid grid-cols-12 text-xs'>
                      <div className='col-span-3 text-left break-all'>
                        {key}
                      </div>
                      <div className='col-span-1 text-center'>=</div>
                      <div className='col-span-8 text-left break-all'>
                        {value}
                      </div>
                    </div>
                  )
                )}
              </div>
            )}
        </CardContent>
      </Card>
      <div id='analyzer' className='scroll-mt-16'>
        <AnalyzerJobDetails run={ortRun} />
      </div>
      <div id='advisor' className='scroll-mt-16'>
        <AdvisorJobDetails run={ortRun} />
      </div>
      <div id='scanner' className='scroll-mt-16'>
        <ScannerJobDetails run={ortRun} />
      </div>
      <div id='evaluator' className='scroll-mt-16'>
        <EvaluatorJobDetails run={ortRun} />
      </div>
      <div id='reporter' className='scroll-mt-16'>
        <ReporterJobDetails run={ortRun} />
      </div>
      <NotifierJobDetails run={ortRun} />
    </div>
  );
};

export const Route = createFileRoute(
  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/config/'
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
  component: ConfigComponent,
  pendingComponent: LoadingIndicator,
});
