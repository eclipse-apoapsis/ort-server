/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import {
  AdvisorJob,
  AnalyzerJob,
  EvaluatorJob,
  NotifierJob,
  ReporterJob,
  ScannerJob,
} from '@/api/requests/types.gen';
import { RunDuration } from '@/components/run-duration';
import { Badge } from '@/components/ui/badge';
import { getStatusBackgroundColor } from '@/helpers/get-status-class';

type JobTitleProps = {
  title: string;
  job:
    | AnalyzerJob
    | AdvisorJob
    | ScannerJob
    | EvaluatorJob
    | ReporterJob
    | NotifierJob
    | null
    | undefined;
};

export const JobTitle = ({ title, job }: JobTitleProps) => {
  return (
    <div className='flex items-center gap-2'>
      {title}
      <Badge className={`border ${getStatusBackgroundColor(job?.status)}`}>
        {job?.status || 'NOT RUN'}
      </Badge>
      {job?.startedAt && (
        <>
          {job.finishedAt ? (
            <div className='text-muted-foreground flex items-center gap-1'>
              in
              <RunDuration
                createdAt={job.startedAt}
                finishedAt={job.finishedAt}
              />
            </div>
          ) : (
            <div className='text-muted-foreground flex items-center'>
              (<RunDuration createdAt={job.startedAt} finishedAt={undefined} />)
            </div>
          )}
        </>
      )}
    </div>
  );
};
