/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import { useQuery } from '@tanstack/react-query';
import { Loader2 } from 'lucide-react';

import { JobSummary, OrtRunSummary } from '@/api';
import {
  getRepositoryRunsOptions,
  getRunStatisticsOptions,
} from '@/api/@tanstack/react-query.gen';
import { ItemCounts } from '@/components/item-counts';
import { config } from '@/config';
import { isJobFinished } from '@/helpers/job-helpers';

type LastRunItemCountsProps = {
  repoId: number;
};

export const LastRunItemCounts = ({ repoId }: LastRunItemCountsProps) => {
  const {
    data: runs,
    isPending: runsIsPending,
    isError: runsIsError,
  } = useQuery({
    ...getRepositoryRunsOptions({
      path: { repositoryId: repoId },
      query: { limit: 1, sort: '-index' },
    }),
    refetchInterval: (query) => {
      const curData = query.state.data?.data;
      if (curData && curData[0] && curData[0].finishedAt) {
        return undefined;
      }
      return config.pollInterval;
    },
  });

  if (runsIsPending) {
    return (
      <>
        <span className='sr-only'>Loading...</span>
        <Loader2 size={16} className='mx-3 animate-spin' />
      </>
    );
  }

  if (runsIsError) {
    return <span>Error loading run.</span>;
  }

  if (!runs.data[0]) return null;

  const run = runs.data[0];

  return <LastRunItemCountsInner summary={run} />;
};

const showBadge = (jobSummary: JobSummary | null | undefined) => {
  return jobSummary != null && isJobFinished(jobSummary.status);
};

const LastRunItemCountsInner = ({ summary }: { summary: OrtRunSummary }) => {
  const statistics = useQuery({
    ...getRunStatisticsOptions({
      path: { runId: summary.id },
    }),
    refetchInterval: config.pollInterval,
  });

  return (
    <ItemCounts
      statistics={statistics.data}
      showIssues={showBadge(summary.jobs.analyzer)}
      showVulnerabilities={showBadge(summary.jobs.advisor)}
      showRuleViolations={showBadge(summary.jobs.evaluator)}
      compact
      link={{
        params: {
          orgId: summary.organizationId.toString(),
          productId: summary.productId.toString(),
          repoId: summary.repositoryId.toString(),
          runIndex: summary.index.toString(),
        },
        issuesSearch: {
          sortBy: [{ id: 'severity', desc: true }],
          itemResolved: ['Unresolved'],
        },
        vulnerabilitiesSearch: {
          sortBy: [{ id: 'rating', desc: true }],
          itemResolved: ['Unresolved'],
        },
        ruleViolationsSearch: {
          sortBy: [{ id: 'severity', desc: true }],
          itemResolved: ['Unresolved'],
        },
      }}
      tooltip={(label, count) =>
        count > 99
          ? `View all ${count} ${label} of run ${summary.index}`
          : `View ${label} of run ${summary.index}`
      }
    />
  );
};
