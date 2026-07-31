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
import { ListTree } from 'lucide-react';

import { JobStatus, type OrtRunStatistics } from '@/api';
import { getRunStatisticsOptions } from '@/api/@tanstack/react-query.gen';
import { StatisticsCard } from '@/components/statistics-card';
import {
  getEcosystemBackgroundColor,
  getStatusFontColor,
} from '@/helpers/get-status-class';
import { isJobFinished, jobStatusTexts } from '@/helpers/job-helpers';

type PackagesStatisticsCardProps = {
  jobIncluded?: boolean;
  status: JobStatus | undefined;
  runId: number;
};

type PackagesStatisticsCardContentProps = Omit<
  PackagesStatisticsCardProps,
  'runId'
> & {
  statistics?: OrtRunStatistics;
};

const PackagesStatisticsCardContent = ({
  jobIncluded,
  status,
  statistics,
}: PackagesStatisticsCardContentProps) => {
  const { value, description } = jobStatusTexts(
    status,
    jobIncluded,
    statistics?.packagesCount
  );

  return (
    <StatisticsCard
      title='Packages'
      icon={() => (
        <ListTree className={`h-4 w-4 ${getStatusFontColor(status)}`} />
      )}
      value={value}
      description={description}
      counts={statistics?.ecosystems?.map(({ name, count }) => ({
        key: name,
        count,
        color: getEcosystemBackgroundColor(name),
      }))}
      className='hover:bg-muted/50 h-full'
    />
  );
};

const PackagesStatisticsCardInner = ({
  jobIncluded,
  status,
  runId,
}: PackagesStatisticsCardProps) => {
  const { data: statistics } = useSuspenseQuery({
    ...getRunStatisticsOptions({
      path: { runId },
    }),
  });

  return (
    <PackagesStatisticsCardContent
      jobIncluded={jobIncluded}
      status={status}
      statistics={statistics}
    />
  );
};

export const PackagesStatisticsCard = (props: PackagesStatisticsCardProps) => {
  if (!isJobFinished(props.status)) {
    return <PackagesStatisticsCardContent {...props} />;
  }

  return <PackagesStatisticsCardInner {...props} />;
};
