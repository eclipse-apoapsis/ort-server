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

type ScannerJobDetailsProps = {
  run: OrtRun;
};

export const ScannerJobDetails = ({ run }: ScannerJobDetailsProps) => {
  const job = run.jobs.scanner;
  const jobConfigs = run.resolvedJobConfigs?.scanner;

  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <JobTitle title='Scanner' job={job} />
        </CardTitle>
      </CardHeader>
      <CardContent>
        {jobConfigs && (
          <div className='space-y-2 text-sm'>
            {jobConfigs?.skipConcluded && (
              <div>
                <Label className='font-semibold'>Skip concluded: </Label>
                {jobConfigs.skipConcluded.toString()}
              </div>
            )}
            {jobConfigs?.skipExcluded && (
              <div>
                <Label className='font-semibold'>Skip excluded: </Label>
                {jobConfigs.skipExcluded.toString()}
              </div>
            )}
            {jobConfigs?.scanners && (
              <div className='space-y-2'>
                <Label className='font-semibold'>Scanners:</Label>{' '}
                {jobConfigs.scanners.map((scanner) => (
                  <div className='ml-2' key={scanner}>
                    <Label className='font-semibold'>{scanner}</Label>
                    {jobConfigs?.config?.[scanner] && (
                      <div className='ml-2'>
                        <Label className='font-semibold'>Configuration:</Label>
                        {jobConfigs.config?.[scanner].options && (
                          <div className='ml-2'>
                            <Label className='font-semibold'>Options:</Label>
                            <div className='ml-2'>
                              {Object.entries(
                                jobConfigs.config[scanner].options
                              ).map(([key, value]) => (
                                <div key={key}>
                                  <Label className='font-semibold'>
                                    {key}:
                                  </Label>{' '}
                                  {value.toString()}
                                </div>
                              ))}
                            </div>
                          </div>
                        )}
                        {jobConfigs.config?.[scanner].secrets && (
                          <div className='ml-2'>
                            <Label className='font-semibold'>Secrets:</Label>
                            <div className='ml-2'>
                              {Object.entries(
                                jobConfigs.config[scanner].secrets
                              ).map(([key, value]) => (
                                <div key={key}>
                                  <Label className='font-semibold'>
                                    {key}:
                                  </Label>{' '}
                                  {value.toString()}
                                </div>
                              ))}
                            </div>
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  );
};
