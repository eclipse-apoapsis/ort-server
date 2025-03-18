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

type NotifierJobDetailsProps = {
  run: OrtRun;
};

export const NotifierJobDetails = ({ run }: NotifierJobDetailsProps) => {
  const job = run.jobs.notifier;
  const jobConfigs = run.resolvedJobConfigs?.notifier;

  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <JobTitle title='Notifier' job={job} />
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className='space-y-2 text-sm'>
          {jobConfigs && (
            <div className='space-y-2'>
              <div className='ml-2 space-y-2'>
                {jobConfigs?.notifierRules && (
                  <div>
                    <Label className='font-semibold'>Rules:</Label>{' '}
                    {jobConfigs.notifierRules}
                  </div>
                )}
                {jobConfigs?.resolutionsFile && (
                  <div>
                    <Label className='font-semibold'>Resolutions:</Label>{' '}
                    {jobConfigs.resolutionsFile}
                  </div>
                )}
                {jobConfigs?.mail && (
                  <div className='space-y-2'>
                    <Label className='font-semibold'>Mail:</Label>{' '}
                    {jobConfigs.mail.recipientAddresses && (
                      <div className='ml-2'>
                        <Label className='font-semibold'>
                          Recipient addresses:
                        </Label>{' '}
                        {jobConfigs.mail.recipientAddresses.join(', ')}
                      </div>
                    )}
                    {jobConfigs.mail.mailServerConfiguration && (
                      <div className='ml-2'>
                        <Label className='font-semibold'>
                          Mail server configuration:
                        </Label>{' '}
                        <div className='ml-2'>
                          <div>
                            <Label className='font-semibold'>Host name: </Label>
                            {jobConfigs.mail.mailServerConfiguration.hostName}
                          </div>
                          <div>
                            <Label className='font-semibold'>Port: </Label>
                            {jobConfigs.mail.mailServerConfiguration.port}
                          </div>
                          <div>
                            <Label className='font-semibold'>Username: </Label>
                            {jobConfigs.mail.mailServerConfiguration.username}
                          </div>
                          <div>
                            <Label className='font-semibold'>Password: </Label>
                            {jobConfigs.mail.mailServerConfiguration.password}
                          </div>
                          <div>
                            <Label className='font-semibold'>Use SSL: </Label>
                            {jobConfigs.mail.mailServerConfiguration.useSsl.toString()}
                          </div>
                          <div>
                            <Label className='font-semibold'>
                              From address:{' '}
                            </Label>
                            {
                              jobConfigs.mail.mailServerConfiguration
                                .fromAddress
                            }
                          </div>
                        </div>
                      </div>
                    )}
                  </div>
                )}
                {jobConfigs?.jira && (
                  <div className='space-y-2'>
                    <Label className='font-semibold'>Jira:</Label>{' '}
                    {jobConfigs.jira.jiraRestClientConfiguration && (
                      <div className='ml-2'>
                        <Label className='font-semibold'>
                          Jira REST client configuration:
                        </Label>{' '}
                        <div className='ml-2'>
                          <div>
                            <Label className='font-semibold'>
                              Server URL:{' '}
                            </Label>
                            {
                              jobConfigs.jira.jiraRestClientConfiguration
                                .serverUrl
                            }
                          </div>
                          <div>
                            <Label className='font-semibold'>Username: </Label>
                            {
                              jobConfigs.jira.jiraRestClientConfiguration
                                .username
                            }
                          </div>
                          <div>
                            <Label className='font-semibold'>Password: </Label>
                            {
                              jobConfigs.jira.jiraRestClientConfiguration
                                .password
                            }
                          </div>
                        </div>
                      </div>
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
