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

import { Scale } from 'lucide-react';

import { useRunsServiceGetApiV1RunsByRunIdStatistics } from '@/api/queries';
import { JobStatus, Severity } from '@/api/requests';
import { LoadingIndicator } from '@/components/loading-indicator';
import { StatisticsCard } from '@/components/statistics-card';
import { ToastError } from '@/components/toast-error';
import {
  getRuleViolationSeverityBackgroundColor,
  getStatusFontColor,
} from '@/helpers/get-status-class';
import { toast } from '@/lib/toast';

type RuleViolationsStatisticsCardProps = {
  jobIncluded?: boolean;
  status: JobStatus | undefined;
  runId: number;
};

export const RuleViolationsStatisticsCard = ({
  jobIncluded,
  status,
  runId,
}: RuleViolationsStatisticsCardProps) => {
  const { data, isPending, isError, error } =
    useRunsServiceGetApiV1RunsByRunIdStatistics({
      runId: runId,
    });

  if (isPending) {
    return (
      <StatisticsCard
        title='Rule Violations'
        icon={() => (
          <Scale className={`h-4 w-4 ${getStatusFontColor(status)}`} />
        )}
        value={<LoadingIndicator />}
        className='h-full hover:bg-muted/50'
      />
    );
  }

  if (isError) {
    toast.error('Unable to load data', {
      description: <ToastError error={error} />,
      duration: Infinity,
      cancel: {
        label: 'Dismiss',
        onClick: () => {},
      },
    });
    return;
  }

  const total = data.ruleViolationsCount;
  const counts = data.ruleViolationsCountBySeverity;

  const jobIsScheduled = status !== undefined;
  const jobIsFinished =
    jobIsScheduled &&
    ['FINISHED', 'FINISHED_WITH_ISSUES', 'FAILED'].includes(status);
  const { value, description } = jobIncluded
    ? jobIsScheduled
      ? jobIsFinished
        ? { value: total, description: '' }
        : { value: '...', description: 'Running' }
      : { value: '-', description: 'Not started' }
    : { value: 'Skipped', description: 'Enable the job for results' };

  return (
    <StatisticsCard
      title='Rule Violations'
      icon={() => <Scale className={`h-4 w-4 ${getStatusFontColor(status)}`} />}
      value={value}
      description={description}
      counts={
        counts
          ? Object.entries(counts).map(([severity, count]) => ({
              key: severity,
              count: count,
              color: getRuleViolationSeverityBackgroundColor(
                severity as Severity
              ),
            }))
          : []
      }
      className='h-full hover:bg-muted/50'
    />
  );
};
