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

type AdvisorJobDetailsProps = {
  run: OrtRun;
};

export const AdvisorJobDetails = ({ run }: AdvisorJobDetailsProps) => {
  const job = run.jobs.advisor;
  const jobConfigs = run.resolvedJobConfigs?.advisor;

  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <JobTitle title='Advisor' job={job} />
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
                {jobConfigs?.skipExcluded && (
                  <div>
                    <Label className='font-semibold'>Skip excluded: </Label>
                    {jobConfigs.skipExcluded.toString()}
                  </div>
                )}
                {jobConfigs?.advisors && (
                  <div>
                    <Label className='font-semibold'>Advisors:</Label>{' '}
                    {jobConfigs.advisors.join(', ')}
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
