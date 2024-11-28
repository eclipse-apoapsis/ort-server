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

import { Files } from 'lucide-react';

import { useRepositoriesServiceGetRepositoriesByProductId } from '@/api/queries';
import { LoadingIndicator } from '@/components/loading-indicator';
import { StatisticsCard } from '@/components/statistics-card';
import { ToastError } from '@/components/toast-error';
import { toast } from '@/lib/toast';
import { cn } from '@/lib/utils';

type ProductRepositoriesStatisticsCardProps = {
  productId: number;
  className?: string;
};

export const ProductRepositoriesStatisticsCard = ({
  productId,
  className,
}: ProductRepositoriesStatisticsCardProps) => {
  const { data, isPending, isError, error } =
    useRepositoriesServiceGetRepositoriesByProductId({
      productId: productId,
      limit: 1,
    });

  if (isPending) {
    return (
      <StatisticsCard
        title='Repositories'
        icon={() => <Files className='h-4 w-4 text-orange-500' />}
        value={<LoadingIndicator />}
        className='h-full hover:bg-muted/50'
      />
    );
  }

  if (isError) {
    toast.error('Unable to load data', {
      description: <ToastError error={error} />,
      duration: Infinity,
      cancel: {
        label: 'Dismiss',
        onClick: () => {},
      },
    });
    return;
  }

  const vulnerabilitiesTotal = data.pagination.totalCount;

  return (
    <StatisticsCard
      title='Repositories'
      icon={() => <Files className='h-4 w-4 text-green-500' />}
      value={vulnerabilitiesTotal}
      className={cn('h-full hover:bg-muted/50', className)}
    />
  );
};
