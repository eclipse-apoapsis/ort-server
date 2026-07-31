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

import { useSuspenseQuery } from '@tanstack/react-query';
import { Scale } from 'lucide-react';

import { JobStatus, Severity, type OrtRunStatistics } from '@/api';
import { getRunStatisticsOptions } from '@/api/@tanstack/react-query.gen';
import { StatisticsCard } from '@/components/statistics-card';
import {
  getRuleViolationSeverityBackgroundColor,
  getStatusFontColor,
} from '@/helpers/get-status-class';
import { isJobFinished, jobStatusTexts } from '@/helpers/job-helpers';

type RuleViolationsStatisticsCardProps = {
  jobIncluded?: boolean;
  status: JobStatus | undefined;
  runId: number;
};

type RuleViolationsStatisticsCardContentProps = Omit<
  RuleViolationsStatisticsCardProps,
  'runId'
> & {
  statistics?: OrtRunStatistics;
};

const RuleViolationsStatisticsCardContent = ({
  jobIncluded,
  status,
  statistics,
}: RuleViolationsStatisticsCardContentProps) => {
  const { value, description } = jobStatusTexts(
    status,
    jobIncluded,
    statistics?.ruleViolationsCount
  );

  return (
    <StatisticsCard
      title='Rule Violations'
      icon={() => <Scale className={`h-4 w-4 ${getStatusFontColor(status)}`} />}
      value={value}
      total={statistics?.ruleViolationsCountTotal ?? undefined}
      description={description}
      counts={
        statistics?.ruleViolationsCountBySeverity
          ? Object.entries(statistics.ruleViolationsCountBySeverity).map(
              ([severity, count]) => ({
                key: severity,
                count,
                color: getRuleViolationSeverityBackgroundColor(
                  severity as Severity
                ),
              })
            )
          : []
      }
      className='hover:bg-muted/50 h-full'
    />
  );
};

const RuleViolationsStatisticsCardInner = ({
  jobIncluded,
  status,
  runId,
}: RuleViolationsStatisticsCardProps) => {
  const { data: statistics } = useSuspenseQuery({
    ...getRunStatisticsOptions({
      path: { runId },
    }),
  });

  return (
    <RuleViolationsStatisticsCardContent
      jobIncluded={jobIncluded}
      status={status}
      statistics={statistics}
    />
  );
};

export const RuleViolationsStatisticsCard = (
  props: RuleViolationsStatisticsCardProps
) => {
  if (!isJobFinished(props.status)) {
    return <RuleViolationsStatisticsCardContent {...props} />;
  }

  return <RuleViolationsStatisticsCardInner {...props} />;
};
