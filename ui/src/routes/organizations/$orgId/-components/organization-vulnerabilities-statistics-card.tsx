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

import { VulnerabilityRating } from '@/api';
import { getOrganizationRunStatisticsOptions } from '@/api/@tanstack/react-query.gen';
import { StatisticsCard } from '@/components/statistics-card';
import { getVulnerabilityRatingBackgroundColor } from '@/helpers/get-status-class';

type OrganizationVulnerabilitiesStatisticsCardProps = {
  organizationId: number;
  className?: string;
};

export const OrganizationVulnerabilitiesStatisticsCard = ({
  organizationId,
  className,
}: OrganizationVulnerabilitiesStatisticsCardProps) => {
  const data = useSuspenseQuery({
    ...getOrganizationRunStatisticsOptions({
      path: { organizationId: organizationId },
    }),
  });

  const unresolved = data.data.vulnerabilitiesCount;
  const total = data.data.vulnerabilitiesCountTotal;
  const counts = data.data.vulnerabilitiesCountByRating;

  return (
    <StatisticsCard
      title='Vulnerabilities'
      icon={() => <ShieldQuestion className='h-4 w-4 text-green-500' />}
      value={unresolved || '-'}
      total={total || undefined}
      counts={
        counts
          ? Object.entries(counts).map(([rating, count]) => ({
              key: rating,
              count: count,
              color: getVulnerabilityRatingBackgroundColor(
                rating as VulnerabilityRating
              ),
            }))
          : []
      }
      className={className}
    />
  );
};
