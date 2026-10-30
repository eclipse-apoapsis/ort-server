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

import { JobConfigurations } from '@/api';

const count = (
  items: unknown[] | null | undefined,
  singular: string,
  plural: string
) => {
  const size = items?.length ?? 0;

  if (size === 0) return [];

  return [`${size} ${size === 1 ? singular : plural}`];
};

/**
 * Summarize what a job is configured to run, for example "25 package managers" and
 * "1 curation provider" for the analyzer. Lists that are empty are left out.
 */
export const summarizeJob = (
  jobConfigs: JobConfigurations,
  job: keyof Omit<JobConfigurations, 'parameters' | 'ruleSet'>
): string[] => {
  switch (job) {
    case 'analyzer':
      return [
        ...count(
          jobConfigs.analyzer?.enabledPackageManagers,
          'package manager',
          'package managers'
        ),
        ...count(
          jobConfigs.analyzer?.packageCurationProviders,
          'curation provider',
          'curation providers'
        ),
      ];
    case 'advisor':
      return count(jobConfigs.advisor?.advisors, 'advisor', 'advisors');
    case 'scanner':
      return count(
        [
          ...new Set([
            ...(jobConfigs.scanner?.scanners ?? []),
            ...(jobConfigs.scanner?.projectScanners ?? []),
          ]),
        ],
        'scanner',
        'scanners'
      );
    case 'evaluator':
      return count(
        jobConfigs.evaluator?.packageConfigurationProviders,
        'package configuration provider',
        'package configuration providers'
      );
    case 'reporter':
      return count(
        jobConfigs.reporter?.formats,
        'report format',
        'report formats'
      );
    case 'notifier':
      return count(
        jobConfigs.notifier?.recipientAddresses,
        'recipient',
        'recipients'
      );
  }
};
