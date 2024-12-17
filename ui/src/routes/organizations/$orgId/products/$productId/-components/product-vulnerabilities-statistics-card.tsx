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

import { useVulnerabilitiesServiceGetVulnerabilitiesAcrossRepositoriesByProductIdSuspense } from '@/api/queries/suspense';
import { StatisticsCard } from '@/components/statistics-card';
import { getVulnerabilityRatingBackgroundColor } from '@/helpers/get-status-class';
import { calcVulnerabilityRatingCounts } from '@/helpers/item-counts';
import { ALL_ITEMS } from '@/lib/constants';
import { cn } from '@/lib/utils';

type ProductVulnerabilitiesStatisticsCardProps = {
  productId: number;
  className?: string;
};

export const ProductVulnerabilitiesStatisticsCard = ({
  productId,
  className,
}: ProductVulnerabilitiesStatisticsCardProps) => {
  const { data: vulnerabilities } =
    useVulnerabilitiesServiceGetVulnerabilitiesAcrossRepositoriesByProductIdSuspense(
      {
        productId: productId,
        limit: ALL_ITEMS,
      }
    );

  const total = vulnerabilities.pagination.totalCount;

  return (
    <StatisticsCard
      title='Vulnerabilities'
      icon={() => <ShieldQuestion className='h-4 w-4 text-green-500' />}
      value={total}
      counts={
        total
          ? calcVulnerabilityRatingCounts(vulnerabilities.data).map(
              ({ rating, count }) => ({
                key: rating,
                count,
                color: getVulnerabilityRatingBackgroundColor(rating),
              })
            )
          : []
      }
      className={cn('h-full hover:bg-muted/50', className)}
    />
  );
};
