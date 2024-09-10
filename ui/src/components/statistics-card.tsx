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

import { Link, useParams } from '@tanstack/react-router';

import {
  AdvisorJob,
  AnalyzerJob,
  EvaluatorJob,
  ReporterJob,
  ScannerJob,
} from '@/api/requests';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';

type StatisticsCardProps = {
  job?:
    | AnalyzerJob
    | AdvisorJob
    | ScannerJob
    | EvaluatorJob
    | ReporterJob
    | undefined;
  title: string;
  icon?: React.ComponentType<{ className?: string }>;
  value?: number | string;
  resultsPath: 'issues' | 'packages' | 'vulnerabilities' | 'rule-violations';
  pollInterval: number;
};

export const StatisticsCard = ({
  title,
  icon: Icon,
  value,
  resultsPath,
}: StatisticsCardProps) => {
  const { orgId, productId, repoId, runIndex } = useParams({ strict: false });
  return (
    <Link
      from='/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex'
      to={resultsPath}
      params={{
        orgId: orgId || '',
        productId: productId || '',
        repoId: repoId || '',
        runIndex: runIndex || '',
      }}
    >
      <Card className='hover:bg-muted/50'>
        <CardHeader>
          <CardTitle>
            <div className='flex items-center justify-between'>
              <span className='text-sm font-semibold'>{title}</span>
              {Icon && <Icon />}
            </div>
          </CardTitle>
        </CardHeader>
        <CardContent className='text-sm'>
          <div className='flex items-center justify-between'>
            <div className='text-2xl font-bold'>{value ? value : '-'}</div>
          </div>
        </CardContent>
      </Card>
    </Link>
  );
};
