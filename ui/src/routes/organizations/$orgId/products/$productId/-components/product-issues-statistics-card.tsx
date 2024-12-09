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

import { Bug } from 'lucide-react';

import { Issue, Severity } from '@/api/requests';
import { StatisticsCard } from '@/components/statistics-card';
import { getIssueSeverityBackgroundColor } from '@/helpers/get-status-class';
import { useIssuesByProductIdSuspense } from '@/hooks/use-issues-by-product-suspense';
import { cn } from '@/lib/utils';

type ProductIssuesStatisticsCardProps = {
  productId: number;
  className?: string;
};

/**
 * Calculate the counts of issues by their severity.
 *
 * @param issues
 * @returns Issue counts sorted in decreasing order of severity.
 */
const calcIssueSeverityCounts = (
  issues: Issue[]
): { severity: Severity; count: number }[] => {
  let errorCount = 0;
  let warningCount = 0;
  let hintCount = 0;
  for (const issue of issues) {
    switch (issue.severity) {
      case 'ERROR':
        errorCount++;
        break;
      case 'WARNING':
        warningCount++;
        break;
      case 'HINT':
        hintCount++;
        break;
    }
  }
  return [
    { severity: 'ERROR', count: errorCount },
    { severity: 'WARNING', count: warningCount },
    { severity: 'HINT', count: hintCount },
  ];
};

export const ProductIssuesStatisticsCard = ({
  productId,
  className,
}: ProductIssuesStatisticsCardProps) => {
  const data = useIssuesByProductIdSuspense({
    productId: productId,
  });

  const issuesTotal = data.length;

  return (
    <StatisticsCard
      title='Issues'
      icon={() => <Bug className='h-4 w-4 text-green-500' />}
      value={issuesTotal}
      counts={
        issuesTotal
          ? calcIssueSeverityCounts(data).map(({ severity, count }) => ({
              key: severity,
              count,
              color: getIssueSeverityBackgroundColor(severity),
            }))
          : []
      }
      className={cn('h-full hover:bg-muted/50', className)}
    />
  );
};