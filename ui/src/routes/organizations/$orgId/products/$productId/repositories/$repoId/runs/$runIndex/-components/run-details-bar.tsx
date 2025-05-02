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

import { getRouteApi, Link } from '@tanstack/react-router';
import { ArrowBigLeft, Repeat } from 'lucide-react';

import { useRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRunsByOrtRunIndexSuspense } from '@/api/queries/suspense';
import { OrtRunJobStatus } from '@/components/ort-run-job-status';
import { RunDuration } from '@/components/run-duration';
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
  const pollInterval = config.pollInterval;

  const { data: ortRun } =
    useRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRunsByOrtRunIndexSuspense(
      {
        repositoryId: Number.parseInt(params.repoId),
        ortRunIndex: Number.parseInt(params.runIndex),
      },
      undefined,
      {
        refetchInterval: (run) => {
          if (
            run.state.data?.status === 'FINISHED' ||
            run.state.data?.status === 'FINISHED_WITH_ISSUES' ||
            run.state.data?.status === 'FAILED'
          )
            return false;
          return pollInterval;
        },
      }
    );

  return (
    <div
      className={cn('flex flex-col justify-between p-4 md:flex-row', className)}
    >
      <Link
        to='/organizations/$orgId/products/$productId/repositories/$repoId/runs'
        params={params}
      >
        <Button
          variant='ghost'
          className='flex w-full cursor-pointer items-center justify-start gap-2'
        >
          <ArrowBigLeft className='-ml-4 h-5 w-5' />
          <div className='text-muted-foreground'>All Runs</div>
        </Button>
      </Link>
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
            ortRun.revision !== ortRun.resolvedRevision &&
            ` (${ortRun.resolvedRevision})`}
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
