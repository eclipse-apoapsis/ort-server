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
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import {
  getEcosystemBackgroundColor,
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
      <Card>
        <CardHeader>
          <CardTitle>Ecosystem colors</CardTitle>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Language</TableHead>
                <TableHead>Ecosystems</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow>
                <TableCell>C/C++</TableCell>
                <TableCell className='flex gap-2'>
                  {['Bazel', 'Conan', 'SpdxDocumentFile'].map((p) => (
                    <Badge
                      className={`border ${getEcosystemBackgroundColor(p)}`}
                    >
                      {p}
                    </Badge>
                  ))}
                </TableCell>
              </TableRow>
              <TableRow>
                <TableCell>Dart/Flutter</TableCell>
                <TableCell className='flex gap-2'>
                  {['Pub'].map((p) => (
                    <Badge
                      className={`border ${getEcosystemBackgroundColor(p)}`}
                    >
                      {p}
                    </Badge>
                  ))}
                </TableCell>
              </TableRow>
              <TableRow>
                <TableCell>Go</TableCell>
                <TableCell className='flex gap-2'>
                  {['GoMod'].map((p) => (
                    <Badge
                      className={`border ${getEcosystemBackgroundColor(p)}`}
                    >
                      {p}
                    </Badge>
                  ))}
                </TableCell>
              </TableRow>
              <TableRow>
                <TableCell>Haskell</TableCell>
                <TableCell className='flex gap-2'>
                  {['Stack'].map((p) => (
                    <Badge
                      className={`border ${getEcosystemBackgroundColor(p)}`}
                    >
                      {p}
                    </Badge>
                  ))}
                </TableCell>
              </TableRow>
              <TableRow>
                <TableCell>Java/Kotlin</TableCell>
                <TableCell className='flex gap-2'>
                  {['Gradle', 'GradleInspector', 'Maven'].map((p) => (
                    <Badge
                      className={`border ${getEcosystemBackgroundColor(p)}`}
                    >
                      {p}
                    </Badge>
                  ))}
                </TableCell>
              </TableRow>
              <TableRow>
                <TableCell>JavaScript/Node.js</TableCell>
                <TableCell className='flex gap-2'>
                  {['Bower', 'NPM', 'PNPM', 'Yarn', 'Yarn2'].map((p) => (
                    <Badge
                      className={`border ${getEcosystemBackgroundColor(p)}`}
                    >
                      {p}
                    </Badge>
                  ))}
                </TableCell>
              </TableRow>
              <TableRow>
                <TableCell>.NET</TableCell>
                <TableCell className='flex gap-2'>
                  {['NuGet'].map((p) => (
                    <Badge
                      className={`border ${getEcosystemBackgroundColor(p)}`}
                    >
                      {p}
                    </Badge>
                  ))}
                </TableCell>
              </TableRow>
              <TableRow>
                <TableCell>Objective-C</TableCell>
                <TableCell className='flex gap-2'>
                  {['Carthage', 'CocoaPods'].map((p) => (
                    <Badge
                      className={`border ${getEcosystemBackgroundColor(p)}`}
                    >
                      {p}
                    </Badge>
                  ))}
                </TableCell>
              </TableRow>
              <TableRow>
                <TableCell>PHP</TableCell>
                <TableCell className='flex gap-2'>
                  {['Composer'].map((p) => (
                    <Badge
                      className={`border ${getEcosystemBackgroundColor(p)}`}
                    >
                      {p}
                    </Badge>
                  ))}
                </TableCell>
              </TableRow>
              <TableRow>
                <TableCell>Python</TableCell>
                <TableCell className='flex gap-2'>
                  {['PIP', 'Pipenv', 'Poetry'].map((p) => (
                    <Badge
                      className={`border ${getEcosystemBackgroundColor(p)}`}
                    >
                      {p}
                    </Badge>
                  ))}
                </TableCell>
              </TableRow>
              <TableRow>
                <TableCell>Ruby</TableCell>
                <TableCell className='flex gap-2'>
                  {['Bundler'].map((p) => (
                    <Badge
                      className={`border ${getEcosystemBackgroundColor(p)}`}
                    >
                      {p}
                    </Badge>
                  ))}
                </TableCell>
              </TableRow>
              <TableRow>
                <TableCell>Rust</TableCell>
                <TableCell className='flex gap-2'>
                  {['Cargo'].map((p) => (
                    <Badge
                      className={`border ${getEcosystemBackgroundColor(p)}`}
                    >
                      {p}
                    </Badge>
                  ))}
                </TableCell>
              </TableRow>
              <TableRow>
                <TableCell>Scala</TableCell>
                <TableCell className='flex gap-2'>
                  {['SBT'].map((p) => (
                    <Badge
                      className={`border ${getEcosystemBackgroundColor(p)}`}
                    >
                      {p}
                    </Badge>
                  ))}
                </TableCell>
              </TableRow>
              <TableRow>
                <TableCell>Swift</TableCell>
                <TableCell className='flex gap-2'>
                  {['SwiftPM'].map((p) => (
                    <Badge
                      className={`border ${getEcosystemBackgroundColor(p)}`}
                    >
                      {p}
                    </Badge>
                  ))}
                </TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  );
};

export const Route = createFileRoute('/admin/colors/')({
  component: ColorsComponent,
});
