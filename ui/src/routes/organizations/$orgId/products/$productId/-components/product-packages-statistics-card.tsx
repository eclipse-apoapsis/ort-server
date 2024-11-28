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

import { Boxes } from 'lucide-react';

import { Package } from '@/api/requests';
import { StatisticsCard } from '@/components/statistics-card';
import { getEcosystemBackgroundColor } from '@/helpers/get-status-class';
import { usePackagesByProductIdSuspense } from '@/hooks/use-packages-by-product-suspense';
import { cn } from '@/lib/utils';

type ProductPackagesStatisticsCardProps = {
  productId: number;
  className?: string;
};

/**
 * Calculate the counts of packages by their ecosystem.
 *
 * @param packages
 * @returns Package counts sorted by ecosystem.
 */
const calcPackageEcosystemCounts = (
  packages: Package[]
): { ecosystem: string; count: number }[] => {
  const ecosystemCounts = new Map<string, number>();
  for (const pkg of packages) {
    const ecosystem = pkg.identifier.type;
    ecosystemCounts.set(ecosystem, (ecosystemCounts.get(ecosystem) || 0) + 1);
  }
  return Array.from(ecosystemCounts.entries())
    .map(([ecosystem, count]) => ({
      ecosystem: ecosystem,
      count,
    }))
    .sort((a, b) => a.ecosystem.localeCompare(b.ecosystem));
};

export const ProductPackagesStatisticsCard = ({
  productId,
  className,
}: ProductPackagesStatisticsCardProps) => {
  const data = usePackagesByProductIdSuspense({
    productId: productId,
  });

  const packagesTotal = data.length;

  return (
    <StatisticsCard
      title='Packages'
      icon={() => <Boxes className='h-4 w-4 text-green-500' />}
      value={packagesTotal}
      counts={
        packagesTotal
          ? calcPackageEcosystemCounts(data).map(({ ecosystem, count }) => ({
              key: ecosystem,
              count,
              color: getEcosystemBackgroundColor(ecosystem),
            }))
          : []
      }
      className={cn('h-full hover:bg-muted/50', className)}
    />
  );
};
