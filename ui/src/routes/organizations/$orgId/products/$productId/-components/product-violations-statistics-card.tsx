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

import { StatisticsCard } from '@/components/statistics-card';
import { getRuleViolationSeverityBackgroundColor } from '@/helpers/get-status-class';
import { calcRuleViolationSeverityCounts } from '@/helpers/item-counts';
import { useViolationsByProductIdSuspense } from '@/hooks/use-violations-by-product-suspense';
import { cn } from '@/lib/utils';

type ProductViolationsStatisticsCardProps = {
  productId: number;
  className?: string;
};

export const ProductViolationsStatisticsCard = ({
  productId,
  className,
}: ProductViolationsStatisticsCardProps) => {
  const data = useViolationsByProductIdSuspense({
    productId: productId,
  });

  const violationsTotal = data.length;

  return (
    <StatisticsCard
      title='Rule Violations'
      icon={() => <Scale className='h-4 w-4 text-green-500' />}
      value={violationsTotal}
      counts={
        violationsTotal
          ? calcRuleViolationSeverityCounts(data).map(
              ({ severity, count }) => ({
                key: severity,
                count,
                color: getRuleViolationSeverityBackgroundColor(severity),
              })
            )
          : []
      }
      className={cn('h-full hover:bg-muted/50', className)}
    />
  );
};
