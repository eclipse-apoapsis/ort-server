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

import { Fragment } from 'react';

import {
  AdvisorJob,
  AnalyzerJob,
  EvaluatorJob,
  NotifierJob,
  ReporterJob,
  ScannerJob,
} from '@/api';
import { RunDuration } from '@/components/run-duration';
import { Badge } from '@/components/ui/badge';
import { getStatusBackgroundColor } from '@/helpers/get-status-class';
import { JobState } from '@/helpers/job-helpers';

const stateTexts: Record<Exclude<JobState, 'started'>, string> = {
  notStarted: 'Not started',
  notEnabled: 'Not enabled for this run',
  notRequested: 'Not requested',
};

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
  state: JobState;
  /** The parts of the one-line summary of the job's configuration. */
  summary: string[];
};

/** A comma that only screen readers announce, so the parts of the title are read apart. */
const Pause = () => <span className='sr-only'>, </span>;

export const JobTitle = ({ title, job, state, summary }: JobTitleProps) => {
  const stateText = state === 'started' ? undefined : stateTexts[state];

  return (
    <div className='grid w-full grid-cols-[6rem_1fr] items-center gap-x-2 gap-y-1 sm:grid-cols-[6rem_auto_1fr]'>
      <span>{title}</span>
      {stateText || !job ? (
        <span className='text-muted-foreground font-normal'>
          <Pause />
          {stateText}
        </span>
      ) : (
        <span className='flex flex-wrap items-center gap-2'>
          <Pause />
          <Badge className={`border ${getStatusBackgroundColor(job.status)}`}>
            {job.status}
          </Badge>
          {job.startedAt && (
            <span className='text-muted-foreground flex items-center gap-1 font-normal'>
              <Pause />
              {job.finishedAt ? 'in' : 'running for'}
              <RunDuration
                createdAt={job.startedAt}
                finishedAt={job.finishedAt ?? undefined}
              />
            </span>
          )}
        </span>
      )}
      {summary.length > 0 && (
        <span className='text-muted-foreground col-span-full font-normal sm:col-span-1 sm:border-l sm:pl-3'>
          <Pause />
          {summary.map((part, index) => (
            <Fragment key={part}>
              {index > 0 && (
                <>
                  <span aria-hidden> · </span>
                  <span className='sr-only'>, </span>
                </>
              )}
              {part}
            </Fragment>
          ))}
        </span>
      )}
    </div>
  );
};
