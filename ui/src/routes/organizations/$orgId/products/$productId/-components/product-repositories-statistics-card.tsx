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

import { Link } from '@tanstack/react-router';
import { Files, PlusIcon } from 'lucide-react';

import { useRepositoriesServiceGetRepositoriesByProductId } from '@/api/queries';
import { LoadingIndicator } from '@/components/loading-indicator';
import { StatisticsCard } from '@/components/statistics-card';
import { ToastError } from '@/components/toast-error';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { toast } from '@/lib/toast';

type ProductRepositoriesStatisticsCardProps = {
  orgId: string;
  productId: string;
  className?: string;
};

export const ProductRepositoriesStatisticsCard = ({
  orgId,
  productId,
  className,
}: ProductRepositoriesStatisticsCardProps) => {
  const { data, isPending, isError, error } =
    useRepositoriesServiceGetRepositoriesByProductId({
      productId: Number.parseInt(productId),
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

  const repositoriesTotal = data.pagination.totalCount;

  return (
    <Card className={className}>
      <CardHeader>
        <CardTitle>
          <div className='flex items-center justify-between'>
            <span className='text-sm font-semibold'>Repositories</span>
            <Files className='h-4 w-4' />
          </div>
        </CardTitle>
      </CardHeader>
      <CardContent className='text-sm'>
        <div className='flex'>
          <div className='text-2xl font-bold'>
            {repositoriesTotal !== undefined ? repositoriesTotal : 'Failed'}
          </div>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button asChild size='sm' className='ml-auto gap-1'>
                <Link
                  to='/organizations/$orgId/products/$productId/create-repository'
                  params={{
                    orgId: orgId,
                    productId: productId,
                  }}
                >
                  Add repository
                  <PlusIcon className='h-4 w-4' />
                </Link>
              </Button>
            </TooltipTrigger>
            <TooltipContent>
              Add a repository for managing compliance runs
            </TooltipContent>
          </Tooltip>
        </div>
      </CardContent>
    </Card>
  );
};
