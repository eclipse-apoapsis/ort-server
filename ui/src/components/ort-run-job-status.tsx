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

import { org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse2_org_eclipse_apoapsis_ortserver_api_v1_model_OrtRunSummary_ } from '@/api/requests';
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import {
  getStatusBackgroundColor,
  getStatusClass,
} from '@/helpers/get-status-colors';

type OrtRunJobStatusProps = {
  jobs: org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse2_org_eclipse_apoapsis_ortserver_api_v1_model_OrtRunSummary_['data'][0]['jobs'];
};

export const OrtRunJobStatus = ({ jobs }: OrtRunJobStatusProps) => {
  return (
    <TooltipProvider>
      <div className='flex items-center space-x-1'>
        <Tooltip>
          <TooltipTrigger asChild>
            <div
              className={`${getStatusClass(jobs.analyzer?.status)} ${getStatusBackgroundColor(jobs.analyzer?.status)}`}
            ></div>
          </TooltipTrigger>
          <TooltipContent>
            <span>
              Analyzer:{' '}
              {jobs.analyzer?.status || 'Not started / not included in ORT Run'}
            </span>
          </TooltipContent>
        </Tooltip>
        <Tooltip>
          <TooltipTrigger asChild>
            <div
              className={`${getStatusClass(jobs.advisor?.status)} ${getStatusBackgroundColor(jobs.advisor?.status)}`}
            ></div>
          </TooltipTrigger>
          <TooltipContent>
            <span>
              Advisor:{' '}
              {jobs.advisor?.status || 'Not started / not included in ORT Run'}
            </span>
          </TooltipContent>
        </Tooltip>
        <Tooltip>
          <TooltipTrigger asChild>
            <div
              className={`${getStatusClass(jobs.scanner?.status)} ${getStatusBackgroundColor(jobs.scanner?.status)}`}
            ></div>
          </TooltipTrigger>
          <TooltipContent>
            <span>
              Scanner:{' '}
              {jobs.scanner?.status || 'Not started / not included in ORT Run'}
            </span>
          </TooltipContent>
        </Tooltip>
        <Tooltip>
          <TooltipTrigger asChild>
            <div
              className={`${getStatusClass(jobs.evaluator?.status)} ${getStatusBackgroundColor(jobs.evaluator?.status)}`}
            ></div>
          </TooltipTrigger>
          <TooltipContent>
            <span>
              Evaluator:{' '}
              {jobs.evaluator?.status ||
                'Not started / not included in ORT Run'}
            </span>
          </TooltipContent>
        </Tooltip>
        <Tooltip>
          <TooltipTrigger asChild>
            <div
              className={`${getStatusClass(jobs.reporter?.status)} ${getStatusBackgroundColor(jobs.reporter?.status)}`}
            ></div>
          </TooltipTrigger>
          <TooltipContent>
            <span>
              Reporter:{' '}
              {jobs.reporter?.status || 'Not started / not included in ORT Run'}
            </span>
          </TooltipContent>
        </Tooltip>
      </div>
    </TooltipProvider>
  );
};
