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

import { createFileRoute } from '@tanstack/react-router';

import {
  JobStatus,
  OrtRunStatus,
  Severity,
  VulnerabilityRating,
} from '@/api/requests';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  getRuleViolationSeverityBackgroundColor,
  getStatusBackgroundColor,
  getVulnerabilityRatingBackgroundColor,
  Status,
} from '@/helpers/get-status-class';

const ColorsComponent = () => {
  // API types like string literal unions ('HINT' | 'WARNING' | 'ERROR') exist only at compile time,
  // and do not persist into runtime JavaScript. Therefore, the values of the union type cannot be extracted
  // into a runtime array purely using the type itself, so the values of the union types need to be
  // explicitly defined here.
  const severities: Severity[] = ['HINT', 'WARNING', 'ERROR'];
  const ratings: VulnerabilityRating[] = [
    'NONE',
    'LOW',
    'MEDIUM',
    'HIGH',
    'CRITICAL',
  ];
  const statuses: (OrtRunStatus | JobStatus)[] = [
    'CREATED',
    'SCHEDULED',
    'ACTIVE',
    'RUNNING',
    'FINISHED_WITH_ISSUES',
    'FINISHED',
    'FAILED',
  ];

  return (
    <div className='flex flex-col gap-4'>
      <Card>
        <CardHeader>
          <CardTitle>ORT brand colors for the workers</CardTitle>
        </CardHeader>
        <CardContent className='flex gap-2'>
          <Badge className='bg-analyzer border'>Analyzer</Badge>
          <Badge className='bg-advisor border'>Advisor</Badge>
          <Badge className='bg-scanner border'>Scanner</Badge>
          <Badge className='bg-evaluator border'>Evaluator</Badge>
          <Badge className='bg-reporter border'>Reporter</Badge>
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>ORT run and job statuses</CardTitle>
        </CardHeader>
        <CardContent className='flex gap-2'>
          <Badge className={`border ${getStatusBackgroundColor(undefined)}`}>
            undefined
          </Badge>
          {statuses.map((status) => (
            <Badge
              key={status}
              className={`border ${getStatusBackgroundColor(status as Status)}`}
            >
              {status}
            </Badge>
          ))}
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>Vulnerability ratings</CardTitle>
        </CardHeader>
        <CardContent className='flex gap-2'>
          {ratings.map((rating) => (
            <Badge
              key={rating}
              className={`border ${getVulnerabilityRatingBackgroundColor(
                rating as VulnerabilityRating
              )}`}
            >
              {rating}
            </Badge>
          ))}
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>Rule violation and issue severities</CardTitle>
        </CardHeader>
        <CardContent className='flex gap-2'>
          {severities.map((severity) => (
            <Badge
              key={severity}
              className={`border ${getRuleViolationSeverityBackgroundColor(
                severity as Severity
              )}`}
            >
              {severity}
            </Badge>
          ))}
        </CardContent>
      </Card>
    </div>
  );
};

export const Route = createFileRoute('/admin/colors/')({
  component: ColorsComponent,
});
