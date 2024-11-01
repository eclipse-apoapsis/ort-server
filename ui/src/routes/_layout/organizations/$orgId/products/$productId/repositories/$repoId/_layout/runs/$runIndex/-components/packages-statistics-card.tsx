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

import { ListTree } from 'lucide-react';
import { useMemo } from 'react';

import { usePackagesServiceGetPackagesByRunId } from '@/api/queries';
import { JobStatus } from '@/api/requests';
import { LoadingIndicator } from '@/components/loading-indicator';
import { StatisticsCard } from '@/components/statistics-card';
import { ToastError } from '@/components/toast-error';
import { getStatusFontColor } from '@/helpers/get-status-class';
import { toast } from '@/lib/toast';

type PackagesStatisticsCardProps = {
  status: JobStatus | undefined;
  runId: number;
};

export const PackagesStatisticsCard = ({
  status,
  runId,
}: PackagesStatisticsCardProps) => {
  const {
    data: packages,
    isPending: packagesIsPending,
    isError: packagesIsError,
    error: packagesError,
  } = usePackagesServiceGetPackagesByRunId({
    runId: runId,
    // Use a large page size for packages request to try to use all packages
    // for finding de-duplicated package types.
    // Another option would be to do two packages queries: first to find the
    // total number of packages, second to fetch using the total as the page
    // size.
    // This can be simplified once the ORT Run statistics query is implemented.
    limit: 100000,
  });

  // Find de-duplicated package types in packages data and sort alphabetically.
  // Packages data can be quite large, so use memoization to avoid unnecessary
  // component rerenders.
  const ecoSystems = useMemo(
    () =>
      [
        ...new Set(
          packages?.data.map((item) => item.identifier?.type).filter(Boolean)
        ),
      ].sort(),
    [packages?.data]
  );

  if (packagesIsPending) {
    return (
      <StatisticsCard
        title='Packages'
        icon={() => (
          <ListTree className={`h-4 w-4 ${getStatusFontColor(status)}`} />
        )}
        value={<LoadingIndicator />}
        className='h-full hover:bg-muted/50'
      />
    );
  }

  if (packagesIsError) {
    toast.error('Unable to load data', {
      description: <ToastError error={packagesError} />,
      duration: Infinity,
      cancel: {
        label: 'Dismiss',
        onClick: () => {},
      },
    });
    return;
  }

  const packagesTotal = packages.pagination.totalCount;

  return (
    <StatisticsCard
      title='Packages'
      icon={() => (
        <ListTree className={`h-4 w-4 ${getStatusFontColor(status)}`} />
      )}
      value={status ? packagesTotal : 'Skipped'}
      description={
        ecoSystems.length
          ? ecoSystems.length > 1
            ? `from ${ecoSystems.length} ecosystems (${ecoSystems.join(', ')})`
            : `from 1 ecosystem (${ecoSystems})`
          : status
            ? ''
            : 'Enable the job for results'
      }
      className='h-full hover:bg-muted/50'
    />
  );
};
