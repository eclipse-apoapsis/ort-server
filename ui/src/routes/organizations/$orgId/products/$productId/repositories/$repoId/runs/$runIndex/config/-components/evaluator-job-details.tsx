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

import { OrtRun } from '@/api/requests';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { JobTitle } from './job-title';

type EvaluatorJobDetailsProps = {
  run: OrtRun;
};

export const EvaluatorJobDetails = ({ run }: EvaluatorJobDetailsProps) => {
  const job = run.jobs.evaluator;
  const jobConfigs = run.resolvedJobConfigs?.evaluator;

  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <JobTitle title='Evaluator' job={job} />
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className='space-y-2 text-sm'>
          {jobConfigs && (
            <div className='space-y-2'>
              <Label className='font-semibold'>
                Resolved job configuration:
              </Label>
              <div className='ml-2 space-y-2'>
                {jobConfigs?.ruleSet && (
                  <div>
                    <Label className='font-semibold'>Ruleset:</Label>{' '}
                    {jobConfigs.ruleSet}
                  </div>
                )}
                {jobConfigs?.licenseClassificationsFile && (
                  <div>
                    <Label className='font-semibold'>
                      License classifications:
                    </Label>{' '}
                    {jobConfigs.licenseClassificationsFile}
                  </div>
                )}
                {jobConfigs?.copyrightGarbageFile && (
                  <div>
                    <Label className='font-semibold'>Copyright garbage:</Label>{' '}
                    {jobConfigs.copyrightGarbageFile}
                  </div>
                )}
                {jobConfigs?.resolutionsFile && (
                  <div>
                    <Label className='font-semibold'>Resolutions:</Label>{' '}
                    {jobConfigs.resolutionsFile}
                  </div>
                )}
                {jobConfigs?.packageConfigurationProviders && (
                  <div className='space-y-2'>
                    <Label className='font-semibold'>
                      Package configuration providers:
                    </Label>{' '}
                    {jobConfigs.packageConfigurationProviders.map(
                      (provider) => (
                        <div className='ml-2' key={provider.id}>
                          <Label className='font-semibold'>
                            {provider.type}
                          </Label>
                          {provider.id && (
                            <div className='ml-2'>
                              <Label className='font-semibold'>Id:</Label>{' '}
                              {provider.id.toString()}
                            </div>
                          )}
                          {provider.enabled && (
                            <div className='ml-2'>
                              <Label className='font-semibold'>Enabled:</Label>{' '}
                              {provider.enabled.toString()}
                            </div>
                          )}
                          {provider.options && (
                            <div className='ml-2'>
                              <Label className='font-semibold'>Options:</Label>{' '}
                              <div className='ml-2'>
                                {Object.entries(provider.options).map(
                                  ([key, value]) => (
                                    <div key={key}>
                                      <Label className='font-semibold'>
                                        {key}:
                                      </Label>{' '}
                                      {value.toString()}
                                    </div>
                                  )
                                )}
                              </div>
                            </div>
                          )}
                          {provider.secrets && (
                            <div className='ml-2'>
                              <Label className='font-semibold'>Secrets:</Label>{' '}
                              <div className='ml-2'>
                                {Object.entries(provider.secrets).map(
                                  ([key, value]) => (
                                    <div key={key}>
                                      <Label className='font-semibold'>
                                        {key}:
                                      </Label>{' '}
                                      {value.toString()}
                                    </div>
                                  )
                                )}
                              </div>
                            </div>
                          )}
                        </div>
                      )
                    )}
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      </CardContent>
    </Card>
  );
};
