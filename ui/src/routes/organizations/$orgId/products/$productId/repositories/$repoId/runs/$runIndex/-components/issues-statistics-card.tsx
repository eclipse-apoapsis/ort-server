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

import { useIssuesServiceGetIssuesByRunId } from '@/api/queries';
import { JobStatus } from '@/api/requests';
import { LoadingIndicator } from '@/components/loading-indicator';
import { StatisticsCard } from '@/components/statistics-card';
import { ToastError } from '@/components/toast-error';
import {
  getIssueSeverityBackgroundColor,
  getStatusFontColor,
} from '@/helpers/get-status-class';
import { calcIssueSeverityCounts } from '@/helpers/item-counts';
import { ALL_ITEMS } from '@/lib/constants';
import { toast } from '@/lib/toast';

type IssuesStatisticsCardProps = {
  jobIncluded?: boolean;
  status: JobStatus | undefined;
  runId: number;
};

export const IssuesStatisticsCard = ({
  jobIncluded,
  status,
  runId,
}: IssuesStatisticsCardProps) => {
  const { data, isPending, isError, error } = useIssuesServiceGetIssuesByRunId({
    runId: runId,
    limit: ALL_ITEMS,
  });

  if (isPending) {
    return (
      <StatisticsCard
        title='Issues'
        icon={() => <Bug className={`h-4 w-4 ${getStatusFontColor(status)}`} />}
        value={<LoadingIndicator />}
        className='h-full hover:bg-muted/50'
      />
    );
  }

  if (isError) {
    toast.error('Unable to load data', {
      description: <ToastError error={error} />,
      duration: Infinity,
      cancel: {
        label: 'Dismiss',
        onClick: () => {},
      },
    });
    return;
  }

  const issuesTotal = data.pagination.totalCount;

  const value = jobIncluded
    ? status === undefined
      ? '-'
      : !['FINISHED', 'FINISHED_WITH_ISSUES', 'FAILED'].includes(status)
        ? '...'
        : issuesTotal
    : 'Skipped';
  const description = jobIncluded
    ? status === undefined
      ? 'Not started'
      : !['FINISHED', 'FINISHED_WITH_ISSUES', 'FAILED'].includes(status)
        ? 'Running'
        : ''
    : 'Enable the job for results';

  return (
    <StatisticsCard
      title='Issues'
      icon={() => <Bug className={`h-4 w-4 ${getStatusFontColor(status)}`} />}
      value={value}
      description={description}
      counts={
        issuesTotal
          ? calcIssueSeverityCounts(data.data).map(({ severity, count }) => ({
              key: severity,
              count,
              color: getIssueSeverityBackgroundColor(severity),
            }))
          : []
      }
      className='h-full hover:bg-muted/50'
    />
  );
};
