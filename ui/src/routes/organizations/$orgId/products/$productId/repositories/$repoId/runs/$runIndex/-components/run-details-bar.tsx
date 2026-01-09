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

import { useSuspenseQuery } from '@tanstack/react-query';
import { getRouteApi, Link, useLocation } from '@tanstack/react-router';
import { ArrowBigLeft, ArrowBigRight, ArrowBigUp, Repeat } from 'lucide-react';

import {
  getRepositoryRunOptions,
  getRepositoryRunsOptions,
} from '@/api/@tanstack/react-query.gen';
import { OrtRunJobStatus } from '@/components/ort-run-job-status';
import { RunDuration } from '@/components/run-duration';
import { Sha1Component } from '@/components/sha1-component';
import { TimestampWithUTC } from '@/components/timestamp-with-utc';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { config } from '@/config';
import { getStatusBackgroundColor } from '@/helpers/get-status-class';
import { cn } from '@/lib/utils';

type RunDetailsBarProps = {
  className?: string;
};

const routeApi = getRouteApi(
  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex'
);

export const RunDetailsBar = ({ className }: RunDetailsBarProps) => {
  const params = routeApi.useParams();
  const location = useLocation();
  const pollInterval = config.pollInterval;

  // Because the list of run indexes for a repository can be discontinuous
  // (due to run deletions), fetch and sort all run indexes to determine
  // the previous and next runs.
  const { data: allRunIndexes } = useSuspenseQuery({
    ...getRepositoryRunsOptions({
      path: {
        repositoryId: Number.parseInt(params.repoId),
      },
      query: {
        limit: 100,
        sort: '-index',
      },
    }),
    select: (data) => data.data.map((run) => run.index).sort((a, b) => a - b),
  });

  const { data: ortRun } = useSuspenseQuery({
    ...getRepositoryRunOptions({
      path: {
        repositoryId: Number.parseInt(params.repoId),
        ortRunIndex: Number.parseInt(params.runIndex),
      },
    }),
    refetchInterval: (run) => {
      if (
        run.state.data?.status === 'FINISHED' ||
        run.state.data?.status === 'FINISHED_WITH_ISSUES' ||
        run.state.data?.status === 'FAILED'
      )
        return false;
      return pollInterval;
    },
  });

  const currentIndex = Number.parseInt(params.runIndex);
  const currentPosition = allRunIndexes.indexOf(currentIndex);

  const previousIndex =
    currentPosition > 0 ? allRunIndexes[currentPosition - 1] : null;
  const nextIndex =
    currentPosition < allRunIndexes.length - 1
      ? allRunIndexes[currentPosition + 1]
      : null;

  const hasPrevious = previousIndex !== null;
  const hasNext = nextIndex !== null;

  // Build a path to another run index while preserving the current sub-route.
  // E.g., if on .../runs/5/config, navigating to run 4 should go to
  // .../runs/4/config instead of /runs/4.
  const buildRunPath = (runIndex: number) => {
    return location.pathname.replace(
      `/runs/${params.runIndex}`,
      `/runs/${runIndex}`
    );
  };

  return (
    <div
      className={cn('flex flex-col justify-between p-4 md:flex-row', className)}
    >
      <div className='flex items-start'>
        <Link
          to={hasPrevious ? buildRunPath(previousIndex!) : '#'}
          disabled={!hasPrevious}
        >
          <Button variant='ghost' disabled={!hasPrevious}>
            <ArrowBigLeft className='h-5 w-5' />
            <div className='text-muted-foreground text-xs'>Previous</div>
          </Button>
        </Link>
        <Link
          to='/organizations/$orgId/products/$productId/repositories/$repoId/runs'
          params={params}
        >
          <Button
            variant='ghost'
            className='flex flex-col items-center gap-0.5'
          >
            <ArrowBigUp className='h-5 w-5' />
            <div className='text-muted-foreground text-xs'>All</div>
          </Button>
        </Link>
        <Link to={hasNext ? buildRunPath(nextIndex!) : '#'} disabled={!hasNext}>
          <Button variant='ghost' disabled={!hasNext}>
            <div className='text-muted-foreground text-xs'>Next</div>
            <ArrowBigRight className='h-5 w-5' />
          </Button>
        </Link>
      </div>
      <div className='flex gap-4'>
        <div className='flex flex-col gap-2'>
          <Badge
            className={`border ${getStatusBackgroundColor(ortRun.status)}`}
          >
            {ortRun.status}
          </Badge>
          <OrtRunJobStatus
            jobs={ortRun.jobs}
            orgId={params.orgId}
            productId={params.productId}
            repoId={params.repoId}
            runIndex={params.runIndex}
          />
        </div>
        <div className='flex flex-col justify-start'>
          <Button variant='outline' asChild size='sm'>
            <Link
              to='/organizations/$orgId/products/$productId/repositories/$repoId/create-run'
              params={{
                orgId: params.orgId,
                productId: params.productId,
                repoId: params.repoId,
              }}
              search={{
                rerunIndex: ortRun.index,
              }}
            >
              <Repeat className='mr-1 h-4 w-4' />
              Rerun
            </Link>
          </Button>
        </div>
      </div>
      <div className='flex flex-col'>
        <div className='flex items-center gap-2 text-sm'>
          <Label className='font-semibold'>Run ID:</Label>
          <div>{ortRun.id}</div>
        </div>
        <div className='flex gap-2 text-sm'>
          <Label className='font-semibold'>Revision:</Label> {ortRun.revision}
          {ortRun.resolvedRevision &&
            ortRun.revision !== ortRun.resolvedRevision && (
              <Sha1Component sha1={ortRun.resolvedRevision} />
            )}
        </div>
      </div>
      <div className='flex flex-col'>
        {ortRun.userDisplayName && (
          <div className='flex items-center gap-2 text-sm'>
            <Label className='font-semibold'>Created by:</Label>
            {ortRun.userDisplayName.username ? (
              <Tooltip>
                <TooltipTrigger className='cursor-pointer'>
                  {ortRun.userDisplayName.fullName ||
                    ortRun.userDisplayName.username}
                </TooltipTrigger>
                <TooltipContent>
                  {ortRun.userDisplayName.username}
                </TooltipContent>
              </Tooltip>
            ) : (
              <span>{ortRun.userDisplayName.fullName}</span>
            )}
          </div>
        )}
        <div className='flex items-center gap-2 text-sm'>
          <Label className='font-semibold'>Created at:</Label>
          <TimestampWithUTC timestamp={ortRun.createdAt} />
        </div>
        <div className='flex items-center gap-2 text-sm'>
          <Label className='font-semibold'>Duration:</Label>
          <RunDuration
            createdAt={ortRun.createdAt}
            finishedAt={ortRun.finishedAt ?? undefined}
          />
        </div>
      </div>
    </div>
  );
};
