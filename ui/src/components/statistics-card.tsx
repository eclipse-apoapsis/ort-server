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

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';

type StatisticsCardProps = {
  title: string;
  icon?: React.ComponentType<{ className?: string }>;
  value?: React.ReactNode;
  counts?: {
    key: string;
    count: number;
    color: string;
  }[];
  description?: string;
  className?: string;
};

export const StatisticsCard = ({
  title,
  icon: Icon,
  value,
  counts = [],
  description,
  className,
}: StatisticsCardProps) => {
  const calcPercentages = (
    counts: { key: string; count: number; color: string }[]
  ) => {
    const total = counts.reduce((acc, { count }) => acc + count, 0);
    return counts.map(({ key, count, color }) => ({
      key,
      count,
      color,
      percentage: total > 0 ? (count / total) * 100 : 0,
    }));
  };

  const percentages = calcPercentages(counts);

  return (
    <Card className={className}>
      <CardHeader>
        <CardTitle>
          <div className='flex items-center justify-between'>
            <span className='text-sm font-semibold'>{title}</span>
            {Icon && <Icon />}
          </div>
        </CardTitle>
      </CardHeader>
      <CardContent className='text-sm'>
        <div className='flex flex-col'>
          {counts.length > 0 && counts.some(({ count }) => count > 0) ? (
            <div className='relative mb-2 h-3 w-full overflow-hidden rounded-sm bg-gray-100'>
              <Tooltip>
                <TooltipTrigger asChild>
                  <div className='relative h-full w-full cursor-default'>
                    {percentages.map(({ key, color, percentage }, index) => {
                      const left = percentages
                        .slice(0, index)
                        .reduce((sum, { percentage }) => sum + percentage, 0);
                      return (
                        <div
                          key={key}
                          className={`absolute top-0 h-full ${color}`}
                          style={{
                            left: `${left}%`,
                            width: `${percentage}%`,
                          }}
                        />
                      );
                    })}
                  </div>
                </TooltipTrigger>
                <TooltipContent>
                  {percentages
                    .filter(({ count }) => count > 0)
                    .map(({ key, count }) => `${key}: ${count}`)
                    .join(' | ')}
                </TooltipContent>
              </Tooltip>
            </div>
          ) : (
            <div className='relative mb-2 h-3 w-full overflow-hidden rounded-sm' />
          )}
          <div className='text-2xl font-bold'>
            {value !== undefined ? value : 'Failed'}
          </div>
          <div className='text-xs'>{description}</div>
        </div>
      </CardContent>
    </Card>
  );
};
