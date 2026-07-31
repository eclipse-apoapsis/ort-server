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

import { useSuspenseQuery } from '@tanstack/react-query';

import type { JobSummary, OrtRunSummary } from '@/api';
import { getRunStatisticsOptions } from '@/api/@tanstack/react-query.gen';
import { ItemCounts, ItemCountsSkeleton } from '@/components/item-counts';
import { QueryBoundary } from '@/components/query-boundary';
import { config } from '@/config';
import { isJobFinished } from '@/helpers/job-helpers';

const showBadge = (jobSummary: JobSummary | null | undefined) => {
  return jobSummary != null && isJobFinished(jobSummary.status);
};

export const RunItemCounts = ({ summary }: { summary: OrtRunSummary }) => (
  <QueryBoundary fallback={<ItemCountsSkeleton wide />} resetKey={summary.id}>
    <RunItemCountsInner summary={summary} />
  </QueryBoundary>
);

const RunItemCountsInner = ({ summary }: { summary: OrtRunSummary }) => {
  const statistics = useSuspenseQuery({
    ...getRunStatisticsOptions({
      path: { runId: summary.id },
    }),
    refetchInterval: config.pollInterval,
  });

  return (
    <ItemCounts
      statistics={statistics.data}
      wide
      showIssues={showBadge(summary.jobs.analyzer)}
      showVulnerabilities={showBadge(summary.jobs.advisor)}
      showRuleViolations={showBadge(summary.jobs.evaluator)}
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
    />
  );
};
