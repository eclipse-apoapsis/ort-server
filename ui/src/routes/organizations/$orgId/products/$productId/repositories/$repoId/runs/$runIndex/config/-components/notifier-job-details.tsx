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
                {jobConfigs?.recipientAddresses && (
                  <div>
                    <Label className='font-semibold'>
                      Recipient addresses:
                    </Label>{' '}
                    {jobConfigs.recipientAddresses.join(', ')}
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
