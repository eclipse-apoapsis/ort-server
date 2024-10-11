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

import { PagedResponse_OrtRunSummary } from '@/api/requests';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import {
  getStatusBackgroundColor,
  getStatusClass,
} from '@/helpers/get-status-class';
import { RunDuration } from './run-duration';

type OrtRunJobStatusProps = {
  jobs: PagedResponse_OrtRunSummary['data'][0]['jobs'];
  pollInterval: number;
  orgId: string;
  productId: string;
  repoId: string;
  runIndex: string;
};

export const OrtRunJobStatus = ({
  jobs,
  pollInterval,
  orgId,
  productId,
  repoId,
  runIndex,
}: OrtRunJobStatusProps) => {
  return (
    <div className='flex items-center space-x-1'>
      <Tooltip>
        <TooltipTrigger asChild>
          <Link
            to='/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/config'
            params={{ orgId, productId, repoId, runIndex }}
            hash='analyzer'
          >
            <div
              className={`${getStatusClass(jobs.analyzer?.status)} ${getStatusBackgroundColor(jobs.analyzer?.status)}`}
            >
              {' '}
            </div>
          </Link>
        </TooltipTrigger>
        <TooltipContent>
          <div className='flex flex-col'>
            <div className='flex gap-2'>
              <div>Analyzer:</div>
              <div>
                {jobs.analyzer?.status ||
                  'Not started / not included in ORT Run'}
              </div>
            </div>
            <div>
              {jobs.analyzer?.startedAt && (
                <div className='flex gap-2'>
                  <div>Duration:</div>
                  <RunDuration
                    createdAt={jobs.analyzer?.startedAt}
                    finishedAt={jobs.analyzer.finishedAt}
                    pollInterval={pollInterval}
                  />
                </div>
              )}
            </div>
          </div>
        </TooltipContent>
      </Tooltip>
      <Tooltip>
        <TooltipTrigger asChild>
          <Link
            to='/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/config'
            params={{ orgId, productId, repoId, runIndex }}
            hash='advisor'
          >
            <div
              className={`${getStatusClass(jobs.advisor?.status)} ${getStatusBackgroundColor(jobs.advisor?.status)}`}
            >
              {' '}
            </div>
          </Link>
        </TooltipTrigger>
        <TooltipContent>
          <div className='flex flex-col'>
            <div className='flex gap-2'>
              <div>Advisor:</div>
              <div>
                {jobs.advisor?.status ||
                  'Not started / not included in ORT Run'}
              </div>
            </div>
            <div>
              {jobs.advisor?.startedAt && (
                <div className='flex gap-2'>
                  <div>Duration:</div>
                  <RunDuration
                    createdAt={jobs.advisor?.startedAt}
                    finishedAt={jobs.advisor.finishedAt}
                    pollInterval={pollInterval}
                  />
                </div>
              )}
            </div>
          </div>
        </TooltipContent>
      </Tooltip>
      <Tooltip>
        <TooltipTrigger asChild>
          <Link
            to='/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/config'
            params={{ orgId, productId, repoId, runIndex }}
            hash='scanner'
          >
            <div
              className={`${getStatusClass(jobs.scanner?.status)} ${getStatusBackgroundColor(jobs.scanner?.status)}`}
            >
              {' '}
            </div>
          </Link>
        </TooltipTrigger>
        <TooltipContent>
          <div className='flex flex-col'>
            <div className='flex gap-2'>
              <div>Scanner:</div>
              <div>
                {jobs.scanner?.status ||
                  'Not started / not included in ORT Run'}
              </div>
            </div>
            <div>
              {jobs.scanner?.startedAt && (
                <div className='flex gap-2'>
                  <div>Duration:</div>
                  <RunDuration
                    createdAt={jobs.scanner?.startedAt}
                    finishedAt={jobs.scanner.finishedAt}
                    pollInterval={pollInterval}
                  />
                </div>
              )}
            </div>
          </div>
        </TooltipContent>
      </Tooltip>
      <Tooltip>
        <TooltipTrigger asChild>
          <Link
            to='/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/config'
            params={{ orgId, productId, repoId, runIndex }}
            hash='evaluator'
          >
            <div
              className={`${getStatusClass(jobs.evaluator?.status)} ${getStatusBackgroundColor(jobs.evaluator?.status)}`}
            >
              {' '}
            </div>
          </Link>
        </TooltipTrigger>
        <TooltipContent>
          <div className='flex flex-col'>
            <div className='flex gap-2'>
              <div>Evaluator:</div>
              <div>
                {jobs.evaluator?.status ||
                  'Not started / not included in ORT Run'}
              </div>
            </div>
            <div>
              {jobs.evaluator?.startedAt && (
                <div className='flex gap-2'>
                  <div>Duration:</div>
                  <RunDuration
                    createdAt={jobs.evaluator?.startedAt}
                    finishedAt={jobs.evaluator.finishedAt}
                    pollInterval={pollInterval}
                  />
                </div>
              )}
            </div>
          </div>
        </TooltipContent>
      </Tooltip>
      <Tooltip>
        <TooltipTrigger asChild>
          <Link
            to='/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/config'
            params={{ orgId, productId, repoId, runIndex }}
            hash='reporter'
          >
            <div
              className={`${getStatusClass(jobs.reporter?.status)} ${getStatusBackgroundColor(jobs.reporter?.status)}`}
            >
              {' '}
            </div>
          </Link>
        </TooltipTrigger>
        <TooltipContent>
          <div className='flex flex-col'>
            <div className='flex gap-2'>
              <div>Reporter:</div>
              <div>
                {jobs.reporter?.status ||
                  'Not started / not included in ORT Run'}
              </div>
            </div>
            <div>
              {jobs.reporter?.startedAt && (
                <div className='flex gap-2'>
                  <div>Duration:</div>
                  <RunDuration
                    createdAt={jobs.reporter?.startedAt}
                    finishedAt={jobs.reporter.finishedAt}
                    pollInterval={pollInterval}
                  />
                </div>
              )}
            </div>
          </div>
        </TooltipContent>
      </Tooltip>
    </div>
  );
};
