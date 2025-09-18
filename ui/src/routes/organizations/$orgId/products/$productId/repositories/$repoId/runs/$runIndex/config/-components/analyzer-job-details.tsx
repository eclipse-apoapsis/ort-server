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

import { OrtRun } from '@/api';
import { RenderProperty } from '@/components/render-property';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { JobTitle } from './job-title';

type AnalyzerJobDetailsProps = {
  run: OrtRun;
};

export const AnalyzerJobDetails = ({ run }: AnalyzerJobDetailsProps) => {
  const job = run.jobs.analyzer;
  const jobConfigs = run.resolvedJobConfigs?.analyzer;

  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <JobTitle title='Analyzer' job={job} />
        </CardTitle>
      </CardHeader>
      {jobConfigs && (
        <CardContent>
          <div className='flex flex-col gap-4 text-sm'>
            <RenderProperty
              label='Repository configuration path'
              value={jobConfigs.repositoryConfigPath}
              showIfEmpty={false}
            />
            <RenderProperty
              label='Allow dynamic versions'
              value={jobConfigs.allowDynamicVersions}
              showIfEmpty={false}
            />
            <RenderProperty
              label='Skip excluded'
              value={jobConfigs.skipExcluded}
              showIfEmpty={false}
            />
            <RenderProperty
              label='Enabled package managers'
              value={jobConfigs.enabledPackageManagers}
              showIfEmpty={false}
            />
            {jobConfigs.environmentConfig?.environmentVariables && (
              <div>
                <Label className='font-semibold'>Environment variables</Label>
                {jobConfigs.environmentConfig.environmentVariables.map(
                  (env) => (
                    <div
                      className='text-muted-foreground ml-2 flex gap-1'
                      key={env.name}
                    >
                      <div>{env.name}</div>
                      <div>=</div>
                      <div>{env.value}</div>
                    </div>
                  )
                )}
              </div>
            )}
            {jobConfigs.packageManagerOptions && (
              <div className='space-y-2'>
                <Label className='font-semibold'>Package manager options</Label>
                {Object.keys(jobConfigs.packageManagerOptions).map((pm) => (
                  <div className='ml-2' key={pm}>
                    <Label className='font-semibold'>{pm}</Label>
                    <div className='ml-2'>
                      <RenderProperty
                        label='Must run after'
                        value={
                          jobConfigs.packageManagerOptions?.[pm]?.mustRunAfter
                        }
                        showIfEmpty={false}
                      />
                      <RenderProperty
                        label='Options'
                        value={jobConfigs.packageManagerOptions?.[pm]?.options}
                        type='keyvalue'
                        showIfEmpty={false}
                      />
                    </div>
                  </div>
                ))}
              </div>
            )}
            {jobConfigs.packageCurationProviders && (
              <div className='space-y-2'>
                <Label className='font-semibold'>
                  Package curation providers
                </Label>
                {jobConfigs.packageCurationProviders.map((provider) => (
                  <div className='ml-2' key={provider.id}>
                    <Label className='font-semibold'>{provider.type}</Label>
                    <div className='ml-2'>
                      <RenderProperty
                        label='Id'
                        value={provider.id}
                        showIfEmpty={false}
                      />
                      <RenderProperty
                        label='Enabled'
                        value={provider.enabled}
                        showIfEmpty={false}
                      />
                      <RenderProperty
                        label='Options'
                        value={provider.options}
                        type='keyvalue'
                        showIfEmpty={false}
                      />
                      <RenderProperty
                        label='Secrets'
                        value={provider.secrets}
                        type='keyvalue'
                        showIfEmpty={false}
                      />
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </CardContent>
      )}
    </Card>
  );
};
