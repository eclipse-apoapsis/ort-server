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

import { useNavigate } from '@tanstack/react-router';
import { Sigma } from 'lucide-react';
import { useState } from 'react';
import { Bar, BarChart, CartesianGrid, XAxis } from 'recharts';

import { useRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRuns } from '@/api/queries';
import {
  DEFAULT_RUNS,
  RunsFilterForm,
  type RunsFilterValues,
} from '@/components/form/runs-filter-form';
import { LoadingIndicator } from '@/components/loading-indicator';
import { RunDuration } from '@/components/run-duration';
import { ToastError } from '@/components/toast-error';
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
} from '@/helpers/calculate-duration';
import { toast } from '@/lib/toast';

const chartConfig = {
  infrastructure: {
    label: 'Infrastructure',
    color: 'gray',
  },
  analyzer: {
    label: 'Analyzer',
    color: 'var(--analyzer)',
  },
  advisor: {
    label: 'Advisor',
    color: 'var(--advisor)',
  },
  scanner: {
    label: 'Scanner',
    color: 'var(--scanner)',
  },
  evaluator: {
    label: 'Evaluator',
    color: 'var(--evaluator)',
  },
  reporter: {
    label: 'Reporter',
    color: 'var(--reporter)',
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
  const navigate = useNavigate();

  // This state drives the query for the runs, and it is updated on form submission.
  const [applied, setApplied] = useState<RunsFilterValues>({
    fetchMode: 'VISIBLE_RUNS',
    nRuns: DEFAULT_RUNS,
  });

  // Compute query params from applied values.
  const limit = applied.fetchMode === 'VISIBLE_RUNS' ? pageSize : applied.nRuns;
  const offset =
    applied.fetchMode === 'VISIBLE_RUNS' ? pageIndex * pageSize : undefined;

  const {
    data: runs,
    error: runsError,
    isPending: runsIsPending,
    isError: runsIsError,
  } = useRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRuns(
    {
      repositoryId: Number.parseInt(repoId),
      limit,
      offset,
      sort: '-index',
    },
    undefined,
    {
      refetchInterval: pollInterval,
    }
  );

  const chartData = runs?.data?.map((run) => {
    const analyzerDuration =
      run.jobs.analyzer?.startedAt && run.jobs.analyzer?.finishedAt
        ? calculateDuration(
            run.jobs.analyzer.startedAt,
            run.jobs.analyzer.finishedAt
          ).durationMs
        : null;

    const advisorDuration =
      run.jobs.advisor?.startedAt && run.jobs.advisor?.finishedAt
        ? calculateDuration(
            run.jobs.advisor.startedAt,
            run.jobs.advisor.finishedAt
          ).durationMs
        : null;

    const scannerDuration =
      run.jobs.scanner?.startedAt && run.jobs.scanner?.finishedAt
        ? calculateDuration(
            run.jobs.scanner.startedAt,
            run.jobs.scanner.finishedAt
          ).durationMs
        : null;

    const evaluatorDuration =
      run.jobs.evaluator?.startedAt && run.jobs.evaluator?.finishedAt
        ? calculateDuration(
            run.jobs.evaluator.startedAt,
            run.jobs.evaluator.finishedAt
          ).durationMs
        : null;

    const reporterDuration =
      run.jobs.reporter?.startedAt && run.jobs.reporter?.finishedAt
        ? calculateDuration(
            run.jobs.reporter.startedAt,
            run.jobs.reporter.finishedAt
          ).durationMs
        : null;

    const finishedJobsDuration =
      (analyzerDuration ?? 0) +
      (advisorDuration ?? 0) +
      (scannerDuration ?? 0) +
      (evaluatorDuration ?? 0) +
      (reporterDuration ?? 0);

    const runDuration =
      run.createdAt && run.finishedAt
        ? calculateDuration(run.createdAt, run.finishedAt).durationMs
        : null;

    // For an unknown reason, the logic for calculating the infrastructure duration
    // based on [startedAt, finishedAt] of the individual jobs and [createdAt, finishedAt]
    // of the run produces negative values sometimes in the Docker Compose setup.
    //
    // This doesn't make sense, as the total run should always take more time than the sum
    // of the durations of the individual jobs included in the run.
    //
    // TO prevent weird results showing, negative values for the intrastructure durations
    // are filtered out.
    const infrastructureDuration =
      runDuration && runDuration - finishedJobsDuration > 0
        ? runDuration - finishedJobsDuration
        : null;

    // Calculate how many durations are non-null. This is needed for proper indexing in the tooltip,
    // to render the total duration at the end of the tooltip.
    const finishedDurations = [
      analyzerDuration,
      advisorDuration,
      scannerDuration,
      evaluatorDuration,
      reporterDuration,
      infrastructureDuration,
    ].filter((duration) => duration !== null).length;

    return {
      runId: run.index,
      finishedDurations,
      createdAt: run.createdAt,
      finishedAt: run.finishedAt,
      infrastructure: infrastructureDuration,
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

  const handleBarClick = (runIndex: string) => {
    const run = runs.data?.find((run) => run.index === Number(runIndex));
    if (run)
      navigate({
        to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex',
        params: {
          orgId: run.organizationId.toString(),
          productId: run.productId.toString(),
          repoId: run.repositoryId.toString(),
          runIndex: run.index.toString(),
        },
      });
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className='flex items-center justify-between'>
          Durations
          <div className='flex items-center space-x-2'>
            <div className='text-sm font-normal'>Show durations for</div>
            <RunsFilterForm
              initialValues={applied}
              onApply={(values) => {
                // Normalize and apply; this triggers refetch via derived params.
                setApplied({
                  fetchMode: values.fetchMode,
                  nRuns:
                    values.fetchMode === 'CUSTOM_RUNS'
                      ? (values.nRuns ?? DEFAULT_RUNS)
                      : undefined,
                });
              }}
            />
          </div>
        </CardTitle>
      </CardHeader>
      <CardContent>
        <ChartContainer
          config={chartConfig}
          className='h-[200px] min-h-[200px] w-full'
        >
          <BarChart
            accessibilityLayer
            data={chartData}
            onClick={(data) => {
              handleBarClick(data.activeLabel as string);
            }}
          >
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
                  payload={[]}
                  coordinate={{ x: 0, y: 0 }}
                  active={false}
                  accessibilityLayer={false}
                  formatter={(value, name, item, index) => {
                    return (
                      <>
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
                        {index === item.payload.finishedDurations - 1 && (
                          <div className='flex w-full flex-col items-center justify-between'>
                            <div className='flex w-full items-center justify-between'>
                              <div className='flex items-center gap-1'>
                                <Sigma className='text-muted-foreground -ml-0.5 size-4 shrink-0' />
                                <div>Total</div>
                              </div>
                              <div className='text-muted-foreground mt-0.5 flex flex-col font-mono text-xs'>
                                <RunDuration
                                  createdAt={item.payload.createdAt}
                                  finishedAt={item.payload.finishedAt}
                                />
                              </div>
                            </div>
                            <div className='text-muted-foreground mt-0.5 text-xs'>
                              Click bar to go to run
                            </div>
                          </div>
                        )}
                      </>
                    );
                  }}
                />
              }
            />
            <ChartLegend content={<ChartLegendContent />} />
            <Bar
              dataKey='infrastructure'
              stackId='a'
              fill='var(--color-infrastructure)'
              className='cursor-pointer'
            />
            <Bar
              dataKey='analyzer'
              stackId='a'
              fill='var(--color-analyzer)'
              className='cursor-pointer'
            />
            <Bar
              dataKey='advisor'
              stackId='a'
              fill='var(--color-advisor)'
              className='cursor-pointer'
            />
            <Bar
              dataKey='scanner'
              stackId='a'
              fill='var(--color-scanner)'
              className='cursor-pointer'
            />
            <Bar
              dataKey='evaluator'
              stackId='a'
              fill='var(--color-evaluator)'
              className='cursor-pointer'
            />
            <Bar
              dataKey='reporter'
              stackId='a'
              fill='var(--color-reporter)'
              className='cursor-pointer'
            />
          </BarChart>
        </ChartContainer>
      </CardContent>
    </Card>
  );
};
