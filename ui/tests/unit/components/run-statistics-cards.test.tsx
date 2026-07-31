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

import type { ComponentType, ReactNode } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { JobStatus, OrtRunStatistics } from '@/api';
import { QueryBoundary } from '@/components/query-boundary';
import { Skeleton } from '@/components/ui/skeleton';
import {
  getEcosystemBackgroundColor,
  getIssueSeverityBackgroundColor,
  getRuleViolationSeverityBackgroundColor,
  getVulnerabilityRatingBackgroundColor,
} from '@/helpers/get-status-class';
import { IssuesStatisticsCard } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/-components/issues-statistics-card';
import { PackagesStatisticsCard } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/-components/packages-statistics-card';
import { RuleViolationsStatisticsCard } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/-components/rule-violations-statistics-card';
import { VulnerabilitiesStatisticsCard } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/-components/vulnerabilities-statistics-card';
import { RunStatisticsFallback } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/index';

type StatisticsCardProps = {
  title: string;
  value?: ReactNode;
  total?: number;
  counts?: Array<{
    key: string;
    count: number;
    color: string;
  }>;
  description?: string;
};

type RunStatisticsCardProps = {
  jobIncluded?: boolean;
  status: JobStatus | undefined;
  runId: number;
};

const mocks = vi.hoisted(() => ({
  statisticsCardProps: [] as StatisticsCardProps[],
  suspend: false,
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

vi.mock('@/components/statistics-card', () => ({
  StatisticsCard: (props: StatisticsCardProps) => {
    mocks.statisticsCardProps.push(props);

    return (
      <section data-title={props.title}>
        <h3>{props.title}</h3>
        <div>{props.value}</div>
        <div>{props.description}</div>
      </section>
    );
  },
}));

const statistics: OrtRunStatistics = {
  issuesCount: 3,
  issuesCountTotal: 8,
  issuesCountBySeverity: { ERROR: 2, WARNING: 1 },
  vulnerabilitiesCount: 5,
  vulnerabilitiesCountTotal: 9,
  vulnerabilitiesCountByRating: { CRITICAL: 4, HIGH: 1 },
  ruleViolationsCount: 7,
  ruleViolationsCountTotal: 10,
  ruleViolationsCountBySeverity: { ERROR: 6, WARNING: 1 },
  packagesCount: 12,
  ecosystems: [
    { name: 'npm', count: 8 },
    { name: 'Maven', count: 4 },
  ],
};

const cards: Array<{
  title: string;
  Component: ComponentType<RunStatisticsCardProps>;
  expected: Partial<StatisticsCardProps>;
}> = [
  {
    title: 'Issues',
    Component: IssuesStatisticsCard,
    expected: {
      value: 3,
      total: 8,
      counts: [
        {
          key: 'ERROR',
          count: 2,
          color: getIssueSeverityBackgroundColor('ERROR'),
        },
        {
          key: 'WARNING',
          count: 1,
          color: getIssueSeverityBackgroundColor('WARNING'),
        },
      ],
    },
  },
  {
    title: 'Vulnerabilities',
    Component: VulnerabilitiesStatisticsCard,
    expected: {
      value: 5,
      total: 9,
      counts: [
        {
          key: 'CRITICAL',
          count: 4,
          color: getVulnerabilityRatingBackgroundColor('CRITICAL'),
        },
        {
          key: 'HIGH',
          count: 1,
          color: getVulnerabilityRatingBackgroundColor('HIGH'),
        },
      ],
    },
  },
  {
    title: 'Rule Violations',
    Component: RuleViolationsStatisticsCard,
    expected: {
      value: 7,
      total: 10,
      counts: [
        {
          key: 'ERROR',
          count: 6,
          color: getRuleViolationSeverityBackgroundColor('ERROR'),
        },
        {
          key: 'WARNING',
          count: 1,
          color: getRuleViolationSeverityBackgroundColor('WARNING'),
        },
      ],
    },
  },
  {
    title: 'Packages',
    Component: PackagesStatisticsCard,
    expected: {
      value: 12,
      counts: [
        {
          key: 'npm',
          count: 8,
          color: getEcosystemBackgroundColor('npm'),
        },
        {
          key: 'Maven',
          count: 4,
          color: getEcosystemBackgroundColor('Maven'),
        },
      ],
    },
  },
];

const renderCard = (
  Component: ComponentType<RunStatisticsCardProps>,
  props: Partial<RunStatisticsCardProps> = {}
) =>
  renderToStaticMarkup(
    <Component jobIncluded runId={42} status='FINISHED' {...props} />
  );

describe.each(cards)(
  '$title statistics card',
  ({ title, Component, expected }) => {
    beforeEach(() => {
      mocks.statisticsCardProps = [];
      mocks.suspend = false;
      mocks.useSuspenseQuery.mockReset();
      mocks.useSuspenseQuery.mockImplementation(() => {
        if (mocks.suspend) throw mocks.pendingPromise;

        return { data: statistics };
      });
    });

    it('renders job status without querying while the job is running', () => {
      const markup = renderCard(Component, { status: 'RUNNING' });

      expect(markup).toContain(title);
      expect(mocks.useSuspenseQuery).not.toHaveBeenCalled();
      expect(mocks.statisticsCardProps).toHaveLength(1);
      expect(mocks.statisticsCardProps[0]).toMatchObject({
        title,
        value: '...',
        description: 'Running',
      });
    });

    it('renders skipped status without querying when the job is not included', () => {
      const markup = renderCard(Component, {
        jobIncluded: false,
        status: undefined,
      });

      expect(markup).toContain(title);
      expect(mocks.useSuspenseQuery).not.toHaveBeenCalled();
      expect(mocks.statisticsCardProps).toHaveLength(1);
      expect(mocks.statisticsCardProps[0]).toMatchObject({
        title,
        value: 'Skipped',
        description: 'Enable the job for results',
      });
    });

    it('renders fetched statistics after the job finishes', () => {
      const markup = renderCard(Component);

      expect(markup).toContain(title);
      expect(mocks.useSuspenseQuery).toHaveBeenCalledOnce();
      expect(mocks.statisticsCardProps).toHaveLength(1);
      expect(mocks.statisticsCardProps[0]).toMatchObject({
        title,
        description: '',
        ...expected,
      });
    });
  }
);

describe('run statistics boundary', () => {
  beforeEach(() => {
    mocks.statisticsCardProps = [];
    mocks.suspend = true;
    mocks.useSuspenseQuery.mockReset();
    mocks.useSuspenseQuery.mockImplementation(() => {
      throw mocks.pendingPromise;
    });
  });

  it('renders the complete grouped skeleton when a finished-job query suspends', () => {
    const markup = renderToStaticMarkup(
      <QueryBoundary
        fallback={
          <RunStatisticsFallback
            analyzerStatus='FINISHED'
            advisorStatus='FINISHED'
            evaluatorStatus='FINISHED'
            value={<Skeleton className='h-8 w-12' />}
          />
        }
        resetKey={42}
      >
        <div>
          <RuleViolationsStatisticsCard
            jobIncluded
            runId={42}
            status='FINISHED'
          />
          <VulnerabilitiesStatisticsCard
            jobIncluded
            runId={42}
            status='FINISHED'
          />
          <IssuesStatisticsCard jobIncluded runId={42} status='FINISHED' />
          <PackagesStatisticsCard jobIncluded runId={42} status='FINISHED' />
        </div>
      </QueryBoundary>
    );

    expect(markup).toContain('Loading data...');
    expect(markup).toContain('Status');
    expect(markup).toContain('Statistics');
    expect(markup).toContain('Rule Violations');
    expect(markup).toContain('Vulnerabilities');
    expect(markup).toContain('Issues');
    expect(markup).toContain('Packages');
    expect(markup.match(/data-slot="skeleton"/g)).toHaveLength(4);
    expect(mocks.statisticsCardProps.map(({ title }) => title)).toEqual([
      'Rule Violations',
      'Vulnerabilities',
      'Issues',
      'Packages',
    ]);
  });
});
