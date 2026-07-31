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

import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { OrtRunSummary } from '@/api';
import { RepositoryItemCounts } from '@/routes/organizations/$orgId/products/$productId/-components/repository-item-counts';

type Statistics = {
  issuesCount: number;
  vulnerabilitiesCount: number;
  ruleViolationsCount: number;
};

type ItemCountsProps = {
  statistics: Statistics;
  showIssues: boolean;
  showVulnerabilities: boolean;
  showRuleViolations: boolean;
  compact: boolean;
  link: {
    params: Record<string, string>;
    issuesSearch: unknown;
    vulnerabilitiesSearch: unknown;
    ruleViolationsSearch: unknown;
  };
  tooltip: (label: string, count: number) => string;
};

const mocks = vi.hoisted(() => ({
  latestRun: undefined as OrtRunSummary | undefined,
  latestRunSuspends: false,
  statistics: {
    issuesCount: 3,
    vulnerabilitiesCount: 5,
    ruleViolationsCount: 7,
  } as Statistics,
  statisticsSuspend: false,
  itemCountsProps: undefined as ItemCountsProps | undefined,
  pendingPromise: new Promise<never>(() => undefined),
  useSuspenseQuery: vi.fn(),
}));

vi.mock('@tanstack/react-query', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@tanstack/react-query')>();

  return {
    ...actual,
    useSuspenseQuery: mocks.useSuspenseQuery,
  };
});

vi.mock('@/hooks/use-latest-repository-run', () => ({
  useLatestRepositoryRun: () => {
    if (mocks.latestRunSuspends) throw mocks.pendingPromise;

    return mocks.latestRun;
  },
}));

vi.mock('@/components/item-counts', () => ({
  ItemCounts: (props: ItemCountsProps) => {
    mocks.itemCountsProps = props;
    return <div>Item counts</div>;
  },
  ItemCountsSkeleton: () => <div data-slot='item-counts-skeleton' />,
}));

const runSummary = {
  id: 11,
  index: 7,
  organizationId: 1,
  productId: 2,
  repositoryId: 3,
  jobs: {
    analyzer: { status: 'FINISHED' },
    advisor: { status: 'RUNNING' },
    evaluator: { status: 'FAILED' },
  },
} as OrtRunSummary;

const renderItemCounts = () =>
  renderToStaticMarkup(<RepositoryItemCounts repoId={3} />);

describe('RepositoryItemCounts', () => {
  beforeEach(() => {
    mocks.latestRun = runSummary;
    mocks.latestRunSuspends = false;
    mocks.statisticsSuspend = false;
    mocks.itemCountsProps = undefined;
    mocks.useSuspenseQuery.mockReset();
    mocks.useSuspenseQuery.mockImplementation(() => {
      if (mocks.statisticsSuspend) throw mocks.pendingPromise;

      return { data: mocks.statistics };
    });
  });

  it('renders item counts for jobs that have finished', () => {
    const markup = renderItemCounts();

    expect(markup).toContain('Item counts');
    expect(mocks.itemCountsProps).toMatchObject({
      statistics: mocks.statistics,
      showIssues: true,
      showVulnerabilities: false,
      showRuleViolations: true,
      compact: true,
      link: {
        params: {
          orgId: '1',
          productId: '2',
          repoId: '3',
          runIndex: '7',
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
      },
    });
    expect(mocks.itemCountsProps?.tooltip('issues', 99)).toBe(
      'View issues of run 7'
    );
    expect(mocks.itemCountsProps?.tooltip('issues', 100)).toBe(
      'View all 100 issues of run 7'
    );
  });

  it('renders a skeleton while the latest run is loading', () => {
    mocks.latestRunSuspends = true;

    const markup = renderItemCounts();

    expect(markup).toContain('data-slot="item-counts-skeleton"');
    expect(markup).toContain('Loading data...');
    expect(mocks.itemCountsProps).toBeUndefined();
  });

  it('renders a skeleton while the run statistics are loading', () => {
    mocks.statisticsSuspend = true;

    const markup = renderItemCounts();

    expect(markup).toContain('data-slot="item-counts-skeleton"');
    expect(markup).toContain('Loading data...');
    expect(mocks.itemCountsProps).toBeUndefined();
  });

  it('reserves space when the repository has no runs', () => {
    mocks.latestRun = undefined;

    const markup = renderItemCounts();

    expect(markup).toContain('class="min-h-6"');
    expect(markup).not.toContain('item-counts-skeleton');
    expect(mocks.useSuspenseQuery).not.toHaveBeenCalled();
    expect(mocks.itemCountsProps).toBeUndefined();
  });
});
