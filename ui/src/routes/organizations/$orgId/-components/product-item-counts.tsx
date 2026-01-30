/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import { useQuery } from '@tanstack/react-query';
import { Bug, Scale, ShieldQuestion } from 'lucide-react';

import { getProductRunStatisticsOptions } from '@/api/@tanstack/react-query.gen';
import { Button } from '@/components/ui/button';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { config } from '@/config';
import { cn } from '@/lib/utils';

type ProductItemCountsProps = {
  productId: number;
};

const pollInterval = config.pollInterval;

export const ProductItemCounts = ({ productId }: ProductItemCountsProps) => {
  const statistics = useQuery({
    ...getProductRunStatisticsOptions({
      path: { productId },
    }),
    refetchInterval: pollInterval,
  });

  return (
    <div className='grid grid-cols-3 gap-1'>
      <CountBadge
        count={statistics.data?.issuesCount}
        icon={Bug}
        label='issues'
        colStart='col-start-1'
      />
      <CountBadge
        count={statistics.data?.vulnerabilitiesCount}
        icon={ShieldQuestion}
        label='vulnerabilities'
        colStart='col-start-2'
      />
      <CountBadge
        count={statistics.data?.ruleViolationsCount}
        icon={Scale}
        label='rule violations'
        colStart='col-start-3'
      />
    </div>
  );
};

type CountBadgeProps = {
  count: number | null | undefined;
  icon: typeof Bug;
  label: string;
  colStart: string;
};

const CountBadge = ({
  count,
  icon: Icon,
  label,
  colStart,
}: CountBadgeProps) => {
  if (count == null) return null;

  const button = (
    <Button
      variant='outline'
      size='xs'
      className={cn('flex w-12 cursor-default justify-start', colStart)}
    >
      <Icon className='size-3' />
      <div className='text-xs'>{count > 99 ? '99+' : count}</div>
    </Button>
  );

  if (count > 99) {
    return (
      <Tooltip>
        <TooltipTrigger asChild className={cn(colStart, 'w-12')}>
          {button}
        </TooltipTrigger>
        <TooltipContent>
          {count} {label} in total
        </TooltipContent>
      </Tooltip>
    );
  }

  return button;
};
