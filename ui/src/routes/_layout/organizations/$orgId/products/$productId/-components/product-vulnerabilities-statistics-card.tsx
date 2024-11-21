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

import { ShieldQuestion } from 'lucide-react';

import { StatisticsCard } from '@/components/statistics-card';
import { calcOverallVulnerability } from '@/helpers/calc-overall-vulnerability';
import {
  getVulnerabilityRatingBackgroundColor,
  VulnerabilityRating,
} from '@/helpers/get-status-class';
import {
  useVulnerabilitiesByProductIdSuspense,
  VulnerabilityWithRepositoryCount,
} from '@/hooks/use-vulnerabilities-by-product-suspense';
import { cn } from '@/lib/utils';

type ProductVulnerabilitiesStatisticsCardProps = {
  productId: number;
  className?: string;
};

/**
 * Calculate the counts of vulnerabilities by their overall rating.
 *
 * @param vulnerabilities
 * @returns Vulnerability counts sorted in decreasing order of rating.
 */
const calcVulnerabilityRatingCounts = (
  vulnerabilities: VulnerabilityWithRepositoryCount[]
): { rating: VulnerabilityRating; count: number }[] => {
  let criticalCount = 0;
  let highCount = 0;
  let mediumCount = 0;
  let lowCount = 0;
  let noneCount = 0;
  for (const vulnerability of vulnerabilities) {
    const ratings = vulnerability.vulnerability.references.map(
      (reference) => reference.severity
    );
    const overallRating = calcOverallVulnerability(ratings);
    switch (overallRating) {
      case 'CRITICAL':
        criticalCount++;
        break;
      case 'HIGH':
        highCount++;
        break;
      case 'MEDIUM':
        mediumCount++;
        break;
      case 'LOW':
        lowCount++;
        break;
      case 'NONE':
        noneCount++;
        break;
    }
  }
  return [
    { rating: 'CRITICAL', count: criticalCount },
    { rating: 'HIGH', count: highCount },
    { rating: 'MEDIUM', count: mediumCount },
    { rating: 'LOW', count: lowCount },
    { rating: 'NONE', count: noneCount },
  ];
};

export const ProductVulnerabilitiesStatisticsCard = ({
  productId,
  className,
}: ProductVulnerabilitiesStatisticsCardProps) => {
  const data = useVulnerabilitiesByProductIdSuspense({
    productId: productId,
  });

  const vulnerabilitiesTotal = data.length;

  return (
    <StatisticsCard
      title='Vulnerabilities'
      icon={() => <ShieldQuestion className='h-4 w-4 text-green-500' />}
      value={vulnerabilitiesTotal}
      counts={
        vulnerabilitiesTotal
          ? calcVulnerabilityRatingCounts(data).map(({ rating, count }) => ({
              key: rating,
              count,
              color: getVulnerabilityRatingBackgroundColor(rating),
            }))
          : []
      }
      className={cn('h-full hover:bg-muted/50', className)}
    />
  );
};
