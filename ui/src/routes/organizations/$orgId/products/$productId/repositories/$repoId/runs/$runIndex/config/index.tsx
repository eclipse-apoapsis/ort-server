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

import { useQuery, useSuspenseQuery } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';
import { ChevronsDownUp, ChevronsUpDown } from 'lucide-react';
import { useState } from 'react';

import { getRepositoryRunOptions } from '@/api/@tanstack/react-query.gen';
import { LoadingIndicator } from '@/components/loading-indicator';
import { Sha1Component } from '@/components/sha1-component';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { createRunPluginsOptions } from '@/hooks/use-create-run-plugins';
import { jobSearchParameterSchema } from '@/schemas';
import { jobSections } from './-components/config-utils';
import { JobConfigurations } from './-components/job-configurations';
import { RunIncludesExcludes } from './-components/run-includes-excludes';

const allJobValues = jobSections.map(({ value }) => value);

const ConfigComponent = () => {
  const params = Route.useParams();
  const { job } = Route.useSearch();
  const [openSections, setOpenSections] = useState<string[]>(job ? [job] : []);

  const { data: ortRun } = useSuspenseQuery({
    ...getRepositoryRunOptions({
      path: {
        repositoryId: Number.parseInt(params.repoId),
        ortRunIndex: Number.parseInt(params.runIndex),
      },
    }),
  });

  const { data: plugins } = useQuery(
    createRunPluginsOptions(
      ortRun.repositoryId,
      ortRun.resolvedJobConfigContext ?? ortRun.jobConfigContext ?? undefined
    )
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
              {ortRun.resolvedJobConfigContext &&
                ortRun.jobConfigContext !== ortRun.resolvedJobConfigContext && (
                  <div>
                    <Sha1Component sha1={ortRun.resolvedJobConfigContext} />
                  </div>
                )}
            </div>
          )}
          {ortRun.environmentConfigPath && (
            <div className='flex items-center gap-1 text-sm'>
              <Label className='font-semibold'>
                Environment configuration path:
              </Label>
              {ortRun.environmentConfigPath && (
                <div>{ortRun.environmentConfigPath}</div>
              )}
            </div>
          )}
          {(() => {
            const labels = ortRun.labels;
            const hasLabels = Object.keys(labels).length > 0;
            return (
              <div className='text-sm'>
                <Label className='font-semibold'>Labels:</Label>{' '}
                {hasLabels ? (
                  Object.entries(labels).map(([key, value]) => (
                    <div key={key} className='ml-4 grid grid-cols-12 text-xs'>
                      <div className='col-span-3 text-left break-all'>
                        {key}
                      </div>
                      <div className='col-span-1 text-center'>=</div>
                      <div className='col-span-8 text-left break-all'>
                        {value}
                      </div>
                    </div>
                  ))
                ) : (
                  <span className='text-muted-foreground ml-4 text-xs italic'>
                    undefined
                  </span>
                )}
              </div>
            );
          })()}
          {(() => {
            const parameters = ortRun.jobConfigs.parameters ?? {};
            const hasParameters = Object.keys(parameters).length > 0;
            return (
              <div className='text-sm'>
                <Label className='font-semibold'>Parameters:</Label>{' '}
                {hasParameters ? (
                  Object.entries(parameters).map(([key, value]) => (
                    <div key={key} className='ml-4 grid grid-cols-12 text-xs'>
                      <div className='col-span-3 text-left break-all'>
                        {key}
                      </div>
                      <div className='col-span-1 text-center'>=</div>
                      <div className='col-span-8 text-left break-all'>
                        {value}
                      </div>
                    </div>
                  ))
                ) : (
                  <span className='text-muted-foreground ml-4 text-xs italic'>
                    undefined
                  </span>
                )}
              </div>
            );
          })()}
          <RunIncludesExcludes scopes={ortRun.scopes} paths={ortRun.paths} />
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle className='flex items-center justify-between'>
            <span>Job Configurations</span>
            <Button
              variant='ghost'
              size='xs'
              className='-mr-1.5'
              onClick={() =>
                setOpenSections((prev) =>
                  prev.length === allJobValues.length ? [] : [...allJobValues]
                )
              }
            >
              {openSections.length === allJobValues.length ? (
                <>
                  Collapse all
                  <ChevronsDownUp className='size-4' />
                </>
              ) : (
                <>
                  Expand all
                  <ChevronsUpDown className='size-4' />
                </>
              )}
            </Button>
          </CardTitle>
        </CardHeader>
        <CardContent>
          <JobConfigurations
            run={ortRun}
            plugins={plugins ?? []}
            openSections={openSections}
            onOpenSectionsChange={setOpenSections}
          />
        </CardContent>
      </Card>
    </div>
  );
};

export const Route = createFileRoute(
  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/config/'
)({
  validateSearch: jobSearchParameterSchema,
  component: ConfigComponent,
  pendingComponent: LoadingIndicator,
});
