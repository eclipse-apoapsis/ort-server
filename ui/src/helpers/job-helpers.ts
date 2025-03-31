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

import { JobStatus } from '@/api/requests';

/**
 * Check if a job is finished, based on its status. The finished states are
 * 'FINISHED', 'FINISHED_WITH_ISSUES', and 'FAILED'.
 * @param status The job status.
 * @returns Whether the job is finished.
 */
export const isJobFinished = (status: JobStatus | undefined) => {
  return (
    status && ['FINISHED', 'FINISHED_WITH_ISSUES', 'FAILED'].includes(status)
  );
};

/**
 * A helper function to determine the contents of the statistics cards for the run.
 * @param status The job status.
 * @param jobIncluded Is the job included in the run?
 * @param total Total number of items (issues, vulnerabilities etc.) found by the job.
 * @returns A value and description for the value, to be consumed by the statistics cards.
 */
export const jobStatusTexts = (
  status: JobStatus | undefined,
  jobIncluded: boolean | undefined,
  total: number | null | undefined
): { value: string | number | null | undefined; description: string } => {
  const finished = isJobFinished(status);

  const { value, description } = jobIncluded
    ? status !== undefined
      ? finished
        ? { value: total, description: '' }
        : { value: '...', description: 'Running' }
      : { value: '-', description: 'Not started' }
    : { value: 'Skipped', description: 'Enable the job for results' };
  return { value, description };
};
