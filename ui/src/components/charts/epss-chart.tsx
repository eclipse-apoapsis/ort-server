/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import { Bar, BarChart, XAxis, YAxis } from 'recharts';

import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import {
  ChartConfig,
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
} from '@/components/ui/chart';
import { EpssData } from '@/helpers/vulnerability-statistics';

type EpssChartProps = {
  epssData: EpssData;
};

export const EpssChart = ({ epssData }: EpssChartProps) => {
  const epssScoreData = [{ component: 'Score', score: epssData.score * 100 }];
  const epssPercentileData = [
    { component: 'Percentile', percentile: epssData.percentile * 100 },
  ];
  const chartConfig = {
    score: {
      label: 'Score',
      color: 'hsl(var(--chart-1))',
    },
    percentile: {
      label: 'Percentile',
      color: 'hsl(var(--chart-1))',
    },
  } satisfies ChartConfig;

  return (
    <div className='flex h-full flex-col gap-2'>
      <Card className='h-full'>
        <CardHeader>
          <CardTitle>
            EPSS Score (
            <a
              className='font-normal text-blue-400 hover:underline'
              href='https://www.first.org/epss/articles/prob_percentile_bins'
              target='_blank'
            >
              details
            </a>
            )
          </CardTitle>
          <CardDescription>
            Probability of observing exploitation activity in the wild in the
            next 30 days.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <ChartContainer
            config={chartConfig}
            className='mx-auto max-h-[50px] w-full'
          >
            <BarChart
              accessibilityLayer
              data={epssScoreData}
              layout='vertical'
              margin={{
                left: 10,
                right: 10,
              }}
              maxBarSize={100}
            >
              <XAxis
                type='number'
                dataKey='score'
                domain={[0, 100]}
                tickFormatter={(value) => `${value}%`}
              />
              <YAxis
                dataKey='component'
                type='category'
                tickLine={false}
                tickMargin={10}
                axisLine={false}
                hide
              />
              <ChartTooltip
                cursor={false}
                content={
                  <ChartTooltipContent
                    hideLabel
                    formatter={(value, name) => (
                      <div className='flex w-full items-baseline justify-between gap-4'>
                        <div className='flex items-baseline gap-2'>
                          <div
                            className='size-2.5 shrink-0 rounded-[2px]'
                            style={{
                              backgroundColor: `var(--color-${name})`,
                            }}
                          />
                          <div className='text-muted-foreground'>
                            {chartConfig[name as keyof typeof chartConfig]
                              ?.label || name}
                          </div>
                        </div>
                        <div className='flex items-baseline gap-0.5'>
                          <div className='font-mono text-xs'>{value}</div>
                          <span>%</span>
                        </div>
                      </div>
                    )}
                  />
                }
              />
              <Bar
                dataKey='score'
                minPointSize={5}
                fill='var(--color-score)'
                radius={5}
              />
            </BarChart>
          </ChartContainer>
        </CardContent>
      </Card>
      <Card className='h-full'>
        <CardHeader>
          <CardTitle>
            EPSS Percentile (
            <a
              className='font-normal text-blue-400 hover:underline'
              href='https://www.first.org/epss/articles/prob_percentile_bins'
              target='_blank'
            >
              details
            </a>
            )
          </CardTitle>
          <CardDescription>
            Relative ranking of exploit probability compared to all scored
            vulnerabilities.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <ChartContainer
            config={chartConfig}
            className='mx-auto max-h-[50px] w-full'
          >
            <BarChart
              accessibilityLayer
              data={epssPercentileData}
              layout='vertical'
              margin={{
                left: 10,
                right: 10,
              }}
            >
              <XAxis
                type='number'
                dataKey='percentile'
                domain={[0, 100]}
                tickFormatter={(value) => `${value}%`}
              />
              <YAxis
                dataKey='component'
                type='category'
                tickLine={false}
                tickMargin={10}
                axisLine={false}
                hide
              />
              <ChartTooltip
                cursor={false}
                content={
                  <ChartTooltipContent
                    className='w-[170px]'
                    hideLabel
                    formatter={(value, name) => (
                      <div className='flex w-full items-baseline justify-between gap-4'>
                        <div className='flex items-baseline gap-2'>
                          <div
                            className='size-2.5 shrink-0 rounded-[2px]'
                            style={{
                              backgroundColor: `var(--color-${name})`,
                            }}
                          />
                          <div className='text-muted-foreground'>
                            {chartConfig[name as keyof typeof chartConfig]
                              ?.label || name}
                          </div>
                        </div>
                        <div className='flex items-baseline gap-0.5'>
                          <div className='font-mono text-xs'>{value}</div>
                          <span>%</span>
                        </div>
                      </div>
                    )}
                  />
                }
              />
              <Bar dataKey='percentile' fill='var(--color-score)' radius={5} />
            </BarChart>
          </ChartContainer>
        </CardContent>
      </Card>
    </div>
  );
};
