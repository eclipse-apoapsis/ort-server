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

import { Bar, BarChart, CartesianGrid, XAxis } from 'recharts';

import { useRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRuns } from '@/api/queries';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  ChartConfig,
  ChartContainer,
  ChartLegend,
  ChartLegendContent,
  ChartTooltip,
  ChartTooltipContent,
} from '@/components/ui/chart';
import { config } from '@/config';
import {
  calculateDuration,
  convertDurationToHms,
} from '@/helpers/get-run-duration';
import { toast } from '@/lib/toast';
import { LoadingIndicator } from '../loading-indicator';
import { ToastError } from '../toast-error';

const chartConfig = {
  analyzer: {
    label: 'Analyzer',
    color: 'hsl(var(--chart-1))',
  },
  advisor: {
    label: 'Advisor',
    color: 'hsl(var(--chart-2))',
  },
  scanner: {
    label: 'Scanner',
    color: 'hsl(var(--chart-3))',
  },
  evaluator: {
    label: 'Evaluator',
    color: 'hsl(var(--chart-4))',
  },
  reporter: {
    label: 'Reporter',
    color: 'hsl(var(--chart-5))',
  },
} satisfies ChartConfig;

type JobDurationsProps = {
  repoId: string;
  pageIndex: number;
  pageSize: number;
};

const pollInterval = config.pollInterval;

export const JobDurations = ({
  repoId,
  pageIndex,
  pageSize,
}: JobDurationsProps) => {
  const {
    data: runs,
    error: runsError,
    isPending: runsIsPending,
    isError: runsIsError,
  } = useRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRuns(
    {
      repositoryId: Number.parseInt(repoId),
      limit: pageSize,
      offset: pageIndex * pageSize,
      sort: '-index',
    },
    undefined,
    {
      refetchInterval: pollInterval,
    }
  );

  const chartData = runs?.data?.map((run) => {
    const analyzerDuration =
      run.jobs.analyzer?.createdAt && run.jobs.analyzer?.finishedAt
        ? calculateDuration(
            run.jobs.analyzer.createdAt,
            run.jobs.analyzer.finishedAt
          ).durationMs
        : null;

    const advisorDuration =
      run.jobs.advisor?.createdAt && run.jobs.advisor?.finishedAt
        ? calculateDuration(
            run.jobs.advisor.createdAt,
            run.jobs.advisor.finishedAt
          ).durationMs
        : null;

    const scannerDuration =
      run.jobs.scanner?.createdAt && run.jobs.scanner?.finishedAt
        ? calculateDuration(
            run.jobs.scanner.createdAt,
            run.jobs.scanner.finishedAt
          ).durationMs
        : null;

    const evaluatorDuration =
      run.jobs.evaluator?.createdAt && run.jobs.evaluator?.finishedAt
        ? calculateDuration(
            run.jobs.evaluator.createdAt,
            run.jobs.evaluator.finishedAt
          ).durationMs
        : null;

    const reporterDuration =
      run.jobs.reporter?.createdAt && run.jobs.reporter?.finishedAt
        ? calculateDuration(
            run.jobs.reporter.createdAt,
            run.jobs.reporter.finishedAt
          ).durationMs
        : null;

    return {
      runId: run.index,
      analyzer: analyzerDuration,
      advisor: advisorDuration,
      scanner: scannerDuration,
      evaluator: evaluatorDuration,
      reporter: reporterDuration,
    };
  });

  if (runsIsPending) {
    return <LoadingIndicator />;
  }

  if (runsIsError) {
    toast.error('Unable to load data', {
      description: <ToastError error={runsError} />,
      duration: Infinity,
      cancel: {
        label: 'Dismiss',
        onClick: () => {},
      },
    });
    return;
  }

  if (runs.pagination.totalCount === 0) {
    return null;
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Durations</CardTitle>
      </CardHeader>
      <CardContent>
        <ChartContainer
          config={chartConfig}
          className='h-[200px] min-h-[200px] w-full'
        >
          <BarChart accessibilityLayer data={chartData}>
            <CartesianGrid vertical={false} />
            <XAxis
              dataKey='runId'
              tickLine={false}
              tickMargin={10}
              axisLine={false}
              reversed
            />
            <ChartTooltip
              content={
                <ChartTooltipContent
                  hideLabel
                  className='w-[180px]'
                  formatter={(value, name) => (
                    <div className='flex w-full items-baseline justify-between'>
                      <div className='flex items-baseline gap-2'>
                        <div
                          className='size-2.5 shrink-0 rounded-[2px]'
                          style={{
                            backgroundColor: `var(--color-${name})`,
                          }}
                        />
                        <div>
                          {chartConfig[name as keyof typeof chartConfig]
                            ?.label || name}
                        </div>
                      </div>
                      <div className='text-muted-foreground font-mono text-xs'>
                        {convertDurationToHms(Number(value))}
                      </div>
                    </div>
                  )}
                />
              }
            />
            <ChartLegend content={<ChartLegendContent />} />
            <Bar dataKey='analyzer' stackId='a' fill='var(--color-analyzer)' />
            <Bar dataKey='advisor' stackId='a' fill='var(--color-advisor)' />
            <Bar dataKey='scanner' stackId='a' fill='var(--color-scanner)' />
            <Bar
              dataKey='evaluator'
              stackId='a'
              fill='var(--color-evaluator)'
            />
            <Bar dataKey='reporter' stackId='a' fill='var(--color-reporter)' />
          </BarChart>
        </ChartContainer>
      </CardContent>
    </Card>
  );
};
