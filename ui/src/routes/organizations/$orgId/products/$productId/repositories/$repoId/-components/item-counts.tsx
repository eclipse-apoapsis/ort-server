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
import { Link } from '@tanstack/react-router';
import { Bug, Scale, ShieldQuestion } from 'lucide-react';

import { JobSummary, OrtRunSummary } from '@/api';
import { getRunStatisticsOptions } from '@/api/@tanstack/react-query.gen';
import { Button } from '@/components/ui/button';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { config } from '@/config';

type ItemCountsProps = {
  summary: OrtRunSummary;
};

const pollInterval = config.pollInterval;

export const ItemCounts = ({ summary }: ItemCountsProps) => {
  const showBadge = (jobSummary: JobSummary | null | undefined) => {
    return (
      jobSummary !== undefined &&
      jobSummary !== null &&
      ['FINISHED', 'FINISHED_WITH_ISSUES', 'FAILED'].includes(jobSummary.status)
    );
  };

  const statistics = useQuery({
    ...getRunStatisticsOptions({
      path: { runId: summary.id },
    }),
    refetchInterval: pollInterval,
  });

  return (
    <div className='grid grid-cols-3 gap-1'>
      {showBadge(summary.jobs.analyzer) && (
        <Tooltip>
          <TooltipTrigger asChild className='col-start-1 w-14'>
            <Button
              variant='outline'
              size='xs'
              asChild
              className='flex justify-start'
            >
              <Link
                to={
                  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/issues'
                }
                params={{
                  orgId: summary.organizationId.toString(),
                  productId: summary.productId.toString(),
                  repoId: summary.repositoryId.toString(),
                  runIndex: summary.index.toString(),
                }}
                search={{ itemResolved: ['Unresolved'] }}
              >
                <Bug className='size-3' />
                <div className='text-xs'>{statistics.data?.issuesCount}</div>
              </Link>
            </Button>
          </TooltipTrigger>
          <TooltipContent>View issues</TooltipContent>
        </Tooltip>
      )}
      {showBadge(summary.jobs.advisor) && (
        <Tooltip>
          <TooltipTrigger asChild className='col-start-2 w-14'>
            <Button
              variant='outline'
              size='xs'
              asChild
              className='flex justify-start'
            >
              <Link
                to={
                  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/vulnerabilities'
                }
                params={{
                  orgId: summary.organizationId.toString(),
                  productId: summary.productId.toString(),
                  repoId: summary.repositoryId.toString(),
                  runIndex: summary.index.toString(),
                }}
                search={{ itemResolved: ['Unresolved'] }}
              >
                <ShieldQuestion className='size-3' />

                <div className='text-xs'>
                  {statistics.data?.vulnerabilitiesCount}
                </div>
              </Link>
            </Button>
          </TooltipTrigger>
          <TooltipContent>View vulnerabilities</TooltipContent>
        </Tooltip>
      )}
      {showBadge(summary.jobs.evaluator) && (
        <Tooltip>
          <TooltipTrigger asChild className='col-start-3 w-14'>
            <Button
              variant='outline'
              size='xs'
              asChild
              className='flex justify-start'
            >
              <Link
                to={
                  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/rule-violations'
                }
                params={{
                  orgId: summary.organizationId.toString(),
                  productId: summary.productId.toString(),
                  repoId: summary.repositoryId.toString(),
                  runIndex: summary.index.toString(),
                }}
                search={{ itemResolved: ['Unresolved'] }}
              >
                <Scale className='size-3' />
                <div className='text-xs'>
                  {statistics.data?.ruleViolationsCount}
                </div>
              </Link>
            </Button>
          </TooltipTrigger>
          <TooltipContent>View rule violations</TooltipContent>
        </Tooltip>
      )}
    </div>
  );
};
