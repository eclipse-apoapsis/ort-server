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

import { useOrganizationsServiceGetApiV1OrganizationsByOrganizationIdStatisticsRunsSuspense } from '@/api/queries/suspense';
import { StatisticsCard } from '@/components/statistics-card';
import { getEcosystemBackgroundColor } from '@/helpers/get-status-class';
import { cn } from '@/lib/utils';

type OrganizationPackagesStatisticsCardProps = {
  organizationId: number;
  className?: string;
};

export const OrganizationPackagesStatisticsCard = ({
  organizationId,
  className,
}: OrganizationPackagesStatisticsCardProps) => {
  const data =
    useOrganizationsServiceGetApiV1OrganizationsByOrganizationIdStatisticsRunsSuspense(
      {
        organizationId: organizationId,
      }
    );

  const total = data.data.packagesCount;
  const counts = data.data.ecosystems;

  return (
    <StatisticsCard
      title='Packages'
      icon={() => <Boxes className='h-4 w-4 text-green-500' />}
      value={total || '-'}
      counts={counts?.map(({ name, count }) => ({
        key: name,
        count: count,
        color: getEcosystemBackgroundColor(name),
      }))}
      className={cn('h-full hover:bg-muted/50', className)}
    />
  );
};
