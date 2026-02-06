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
import { createFileRoute } from '@tanstack/react-router';

import { OrtRun } from '@/api';
import { getRepositoryRunOptions } from '@/api/@tanstack/react-query.gen';
import { LoadingIndicator } from '@/components/loading-indicator';
import { Sha1Component } from '@/components/sha1-component';
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { jobSearchParameterSchema } from '@/schemas';
import { AdvisorJobDetails } from './-components/advisor-job-details';
import { AnalyzerJobDetails } from './-components/analyzer-job-details';
import { EvaluatorJobDetails } from './-components/evaluator-job-details';
import { JobTitle } from './-components/job-title';
import { NotifierJobDetails } from './-components/notifier-job-details';
import { ReporterJobDetails } from './-components/reporter-job-details';
import { ScannerJobDetails } from './-components/scanner-job-details';

const jobSections = [
  { value: 'analyzer', title: 'Analyzer', Component: AnalyzerJobDetails },
  { value: 'advisor', title: 'Advisor', Component: AdvisorJobDetails },
  { value: 'scanner', title: 'Scanner', Component: ScannerJobDetails },
  { value: 'evaluator', title: 'Evaluator', Component: EvaluatorJobDetails },
  { value: 'reporter', title: 'Reporter', Component: ReporterJobDetails },
  { value: 'notifier', title: 'Notifier', Component: NotifierJobDetails },
] as const;

type JobKey = (typeof jobSections)[number]['value'];

const getJob = (run: OrtRun, key: JobKey) => run.jobs[key];

const ConfigComponent = () => {
  const params = Route.useParams();
  const { job } = Route.useSearch();

  const { data: ortRun } = useSuspenseQuery({
    ...getRepositoryRunOptions({
      path: {
        repositoryId: Number.parseInt(params.repoId),
        ortRunIndex: Number.parseInt(params.runIndex),
      },
    }),
  });

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
              {ortRun.resolvedJobConfigContext &&
                ortRun.jobConfigContext !== ortRun.resolvedJobConfigContext && (
                  <div>
                    <Sha1Component sha1={ortRun.resolvedJobConfigContext} />
                  </div>
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
      <Accordion type='multiple' defaultValue={job ? [job] : []}>
        {jobSections.map(({ value, title, Component }) => (
          <AccordionItem key={value} value={value}>
            <AccordionTrigger>
              <JobTitle title={title} job={getJob(ortRun, value)} />
            </AccordionTrigger>
            <AccordionContent>
              <Component run={ortRun} />
            </AccordionContent>
          </AccordionItem>
        ))}
      </Accordion>
    </div>
  );
};

export const Route = createFileRoute(
  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/config/'
)({
  validateSearch: jobSearchParameterSchema,
  loader: async ({ context: { queryClient }, params }) => {
    await queryClient.prefetchQuery({
      ...getRepositoryRunOptions({
        path: {
          repositoryId: Number.parseInt(params.repoId),
          ortRunIndex: Number.parseInt(params.runIndex),
        },
      }),
    });
  },
  component: ConfigComponent,
  pendingComponent: LoadingIndicator,
});
