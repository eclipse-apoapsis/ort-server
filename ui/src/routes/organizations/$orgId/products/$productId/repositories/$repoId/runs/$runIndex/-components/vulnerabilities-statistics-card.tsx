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
import { ShieldQuestion } from 'lucide-react';

import { JobStatus, VulnerabilityRating, type OrtRunStatistics } from '@/api';
import { getRunStatisticsOptions } from '@/api/@tanstack/react-query.gen';
import { StatisticsCard } from '@/components/statistics-card';
import {
  getStatusFontColor,
  getVulnerabilityRatingBackgroundColor,
} from '@/helpers/get-status-class';
import { isJobFinished, jobStatusTexts } from '@/helpers/job-helpers';

type VulnerabilitiesStatisticsCardProps = {
  jobIncluded?: boolean;
  status: JobStatus | undefined;
  runId: number;
};

type VulnerabilitiesStatisticsCardContentProps = Omit<
  VulnerabilitiesStatisticsCardProps,
  'runId'
> & {
  statistics?: OrtRunStatistics;
};

const VulnerabilitiesStatisticsCardContent = ({
  jobIncluded,
  status,
  statistics,
}: VulnerabilitiesStatisticsCardContentProps) => {
  const { value, description } = jobStatusTexts(
    status,
    jobIncluded,
    statistics?.vulnerabilitiesCount
  );

  return (
    <StatisticsCard
      title='Vulnerabilities'
      icon={() => (
        <ShieldQuestion className={`h-4 w-4 ${getStatusFontColor(status)}`} />
      )}
      value={value}
      total={statistics?.vulnerabilitiesCountTotal ?? undefined}
      description={description}
      counts={
        statistics?.vulnerabilitiesCountByRating
          ? Object.entries(statistics.vulnerabilitiesCountByRating).map(
              ([rating, count]) => ({
                key: rating,
                count,
                color: getVulnerabilityRatingBackgroundColor(
                  rating as VulnerabilityRating
                ),
              })
            )
          : []
      }
      className='hover:bg-muted/50 h-full'
    />
  );
};

const VulnerabilitiesStatisticsCardInner = ({
  jobIncluded,
  status,
  runId,
}: VulnerabilitiesStatisticsCardProps) => {
  const { data: statistics } = useSuspenseQuery({
    ...getRunStatisticsOptions({
      path: { runId },
    }),
  });

  return (
    <VulnerabilitiesStatisticsCardContent
      jobIncluded={jobIncluded}
      status={status}
      statistics={statistics}
    />
  );
};

export const VulnerabilitiesStatisticsCard = (
  props: VulnerabilitiesStatisticsCardProps
) => {
  if (!isJobFinished(props.status)) {
    return <VulnerabilitiesStatisticsCardContent {...props} />;
  }

  return <VulnerabilitiesStatisticsCardInner {...props} />;
};
