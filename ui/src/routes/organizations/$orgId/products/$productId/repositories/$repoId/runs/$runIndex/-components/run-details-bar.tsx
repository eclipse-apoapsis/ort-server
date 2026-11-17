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

import { useQuery, useSuspenseQuery } from '@tanstack/react-query';
import { getRouteApi, Link, useLocation } from '@tanstack/react-router';
import { ArrowBigLeft, ArrowBigRight, ArrowBigUp, Repeat } from 'lucide-react';

import { getRepositoryRunOptions } from '@/api/@tanstack/react-query.gen';
import { ActionTooltipTrigger } from '@/components/action-tooltip-trigger';
import { RunFavoriteButton } from '@/components/favorite-button';
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
import { useRepositoryPermission } from '@/hooks/use-authorization';
import { cn } from '@/lib/utils';
import { runNeighboursOptions } from './run-neighbours';

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

  const currentIndex = Number.parseInt(params.runIndex);

  // Run indexes can have gaps where runs were deleted, so the previous and next runs are looked up
  // instead of counted. They are not waited for, so that moving to another run does not suspend the
  // page, and the links stay disabled until they are known.
  const { data: neighbours } = useQuery(
    runNeighboursOptions(Number.parseInt(params.repoId), currentIndex)
  );

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

  const { isAllowed: canTriggerRun } = useRepositoryPermission(
    Number.parseInt(params.repoId),
    'TRIGGER_ORT_RUN'
  );

  const previousIndex = neighbours?.previous ?? null;
  const nextIndex = neighbours?.next ?? null;

  const hasPrevious = previousIndex !== null;
  const hasNext = nextIndex !== null;

  // Build a path to another run index while preserving the current sub-route.
  // E.g., if on .../runs/5/config, navigating to run 4 should go to
  // .../runs/4/config instead of /runs/4.
  const buildRunPath = (runIndex: number) => {
    const pathname = location.pathname.replace(
      `/runs/${params.runIndex}`,
      `/runs/${runIndex}`
    );
    return location.searchStr ? `${pathname}${location.searchStr}` : pathname;
  };

  return (
    <div
      className={cn('flex flex-col justify-between p-4 md:flex-row', className)}
    >
      <div className='flex items-start'>
        <Button asChild variant='ghost'>
          <Link
            to={hasPrevious ? buildRunPath(previousIndex!) : '#'}
            disabled={!hasPrevious}
          >
            <ArrowBigLeft className='h-5 w-5' />
            <div className='text-muted-foreground text-xs'>Previous</div>
          </Link>
        </Button>
        <Button
          asChild
          variant='ghost'
          className='flex flex-col items-center gap-0.5'
        >
          <Link
            to='/organizations/$orgId/products/$productId/repositories/$repoId/runs'
            params={params}
          >
            <ArrowBigUp className='h-5 w-5' />
            <div className='text-muted-foreground text-xs'>All</div>
          </Link>
        </Button>
        <Button asChild variant='ghost'>
          <Link
            to={hasNext ? buildRunPath(nextIndex!) : '#'}
            disabled={!hasNext}
          >
            <div className='text-muted-foreground text-xs'>Next</div>
            <ArrowBigRight className='h-5 w-5' />
          </Link>
        </Button>
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
        <div className='flex items-start gap-2'>
          <Tooltip>
            <ActionTooltipTrigger disabled={canTriggerRun === false}>
              <Button
                variant='outline'
                asChild
                size='sm'
                disabled={canTriggerRun === false}
              >
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
            </ActionTooltipTrigger>
            {canTriggerRun === false && (
              <TooltipContent>Insufficient permissions.</TooltipContent>
            )}
          </Tooltip>
        </div>
      </div>
      <div className='flex flex-col'>
        <div className='flex items-center gap-2 text-sm'>
          <Label className='font-semibold'>Run ID:</Label>
          <div>{ortRun.id}</div>
          <RunFavoriteButton
            organizationId={params.orgId}
            productId={params.productId}
            repositoryId={params.repoId}
            run={ortRun}
            size='xs'
            variant='ghost'
            className='size-6 p-0'
          />
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
