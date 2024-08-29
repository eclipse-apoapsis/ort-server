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
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { calculateDuration } from '@/helpers/get-run-duration';
import { getStatusBackgroundColor } from '@/helpers/get-status-colors';

type ReporterJobDetailsProps = {
  run: OrtRun;
};

export const ReporterJobDetails = ({ run }: ReporterJobDetailsProps) => {
  const job = run.jobs.reporter;
  const jobConfigs = run.resolvedJobConfigs?.reporter;

  return (
    <Card>
      <CardHeader>
        <CardTitle className='flex gap-2'>
          Reporter
          <Badge className={`border ${getStatusBackgroundColor(job?.status)}`}>
            {run.jobs.reporter?.status || 'NOT RUN'}
          </Badge>
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className='space-y-2 text-sm'>
          <div>
            <Label className='font-semibold'>Duration: </Label>
            {job?.startedAt && job?.finishedAt
              ? calculateDuration(job.startedAt, job.finishedAt)
              : 'N/A'}
          </div>
          {jobConfigs && (
            <div className='space-y-2'>
              <Label className='font-semibold'>
                Resolved job configuration:
              </Label>
              <div className='ml-2'>
                {jobConfigs?.formats && (
                  <div>
                    <Label className='font-semibold'>Report formats:</Label>{' '}
                    {jobConfigs.formats.join(', ')}
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
