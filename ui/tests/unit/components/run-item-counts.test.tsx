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
import { RunItemCounts } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/-components/run-item-counts';

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
  wide: boolean;
  link: {
    params: Record<string, string>;
    issuesSearch: unknown;
    vulnerabilitiesSearch: unknown;
    ruleViolationsSearch: unknown;
  };
};

const mocks = vi.hoisted(() => ({
  statistics: {
    issuesCount: 3,
    vulnerabilitiesCount: 5,
    ruleViolationsCount: 7,
  } as Statistics,
  suspend: false,
  itemCountsProps: undefined as ItemCountsProps | undefined,
  skeletonWide: undefined as boolean | undefined,
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

vi.mock('@/components/item-counts', () => ({
  ItemCounts: (props: ItemCountsProps) => {
    mocks.itemCountsProps = props;
    return <div>Item counts</div>;
  },
  ItemCountsSkeleton: ({ wide }: { wide?: boolean }) => {
    mocks.skeletonWide = wide;
    return <div data-slot='item-counts-skeleton' />;
  },
}));

const runSummary = {
  id: 11,
  index: 7,
  organizationId: 1,
  productId: 2,
  repositoryId: 3,
  jobs: {
    analyzer: { status: 'FINISHED' },
    advisor: { status: 'FINISHED' },
    evaluator: { status: 'FINISHED' },
  },
} as OrtRunSummary;

const renderItemCounts = (summary: OrtRunSummary = runSummary) =>
  renderToStaticMarkup(<RunItemCounts summary={summary} />);

describe('RunItemCounts', () => {
  beforeEach(() => {
    mocks.suspend = false;
    mocks.itemCountsProps = undefined;
    mocks.skeletonWide = undefined;
    mocks.useSuspenseQuery.mockReset();
    mocks.useSuspenseQuery.mockImplementation(() => {
      if (mocks.suspend) throw mocks.pendingPromise;

      return { data: mocks.statistics };
    });
  });

  it('renders wide item-count badges linked to the run findings', () => {
    const markup = renderItemCounts();

    expect(markup).toContain('Item counts');
    expect(mocks.itemCountsProps).toMatchObject({
      statistics: mocks.statistics,
      showIssues: true,
      showVulnerabilities: true,
      showRuleViolations: true,
      wide: true,
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
  });

  it('renders a wide skeleton while the statistics are loading', () => {
    mocks.suspend = true;

    const markup = renderItemCounts();

    expect(markup).toContain('data-slot="item-counts-skeleton"');
    expect(markup).toContain('Loading data...');
    expect(mocks.skeletonWide).toBe(true);
    expect(mocks.itemCountsProps).toBeUndefined();
  });

  it('hides badges for jobs that have not finished', () => {
    renderItemCounts({
      ...runSummary,
      jobs: {
        ...runSummary.jobs,
        advisor: { ...runSummary.jobs.advisor!, status: 'RUNNING' },
        evaluator: null,
      },
    });

    expect(mocks.itemCountsProps).toMatchObject({
      showIssues: true,
      showVulnerabilities: false,
      showRuleViolations: false,
    });
  });
});
