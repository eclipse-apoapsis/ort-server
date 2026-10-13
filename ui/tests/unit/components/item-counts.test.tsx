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

// @vitest-environment jsdom

import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ComponentProps } from 'react';
import { describe, expect, it } from 'vitest';

import { ItemCounts } from '@/components/item-counts';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';

const statistics = {
  issuesCount: 12,
  vulnerabilitiesCount: 34,
  ruleViolationsCount: 56,
};
const categories = [
  {
    flag: 'showIssues',
    count: 12,
    label: 'issues',
    route: 'issues',
    sort: 'severity',
  },
  {
    flag: 'showVulnerabilities',
    count: 34,
    label: 'vulnerabilities',
    route: 'vulnerabilities',
    sort: 'rating',
  },
  {
    flag: 'showRuleViolations',
    count: 56,
    label: 'rule violations',
    route: 'rule-violations',
    sort: 'severity',
  },
] as const;
const runPath = '/organizations/42/products/84/repositories/126/runs/3';
const routePath =
  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex';
const link = {
  params: { orgId: '42', productId: '84', repoId: '126', runIndex: '3' },
  issuesSearch: {
    sortBy: [{ id: 'severity', desc: true }],
    itemResolved: ['Unresolved'],
  },
  vulnerabilitiesSearch: {
    sortBy: [{ id: 'rating', desc: true }],
    itemResolved: ['Resolved'],
  },
  ruleViolationsSearch: {
    sortBy: [{ id: 'severity', desc: true }],
    itemResolved: ['Unresolved', 'Resolved'],
  },
} satisfies NonNullable<ComponentProps<typeof ItemCounts>['link']>;

const renderLinkedCounts = () =>
  renderInteractiveWithRouter(
    <ItemCounts statistics={statistics} link={link} />,
    {
      path: '/',
      routes: [
        { path: '/' },
        ...categories.map(({ route }) => ({ path: `${routePath}/${route}` })),
      ],
    }
  );

describe('ItemCounts', () => {
  it('renders each count without links when no link is provided', () => {
    render(<ItemCounts statistics={statistics} />);

    expect(
      screen.getAllByRole('button').map((button) => button.textContent)
    ).toEqual(['12', '34', '56']);
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  it.each(categories)(
    'hides only $label when $flag is false',
    ({ flag, count }) => {
      render(<ItemCounts statistics={statistics} {...{ [flag]: false }} />);

      expect(
        screen.queryByRole('button', { name: String(count) })
      ).not.toBeInTheDocument();
      for (const category of categories.filter(
        (category) => category.flag !== flag
      )) {
        expect(
          screen.getByRole('button', { name: String(category.count) })
        ).toBeInTheDocument();
      }
    }
  );

  it('renders zero but omits null and undefined counts', () => {
    render(
      <ItemCounts statistics={{ issuesCount: 0, vulnerabilitiesCount: null }} />
    );

    expect(screen.getAllByRole('button')).toHaveLength(1);
    expect(screen.getByRole('button', { name: '0' })).toBeInTheDocument();
  });

  it('renders no badges without statistics', () => {
    render(<ItemCounts statistics={undefined} />);

    expect(screen.queryByRole('button')).not.toBeInTheDocument();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  it.each([
    { compact: true, count: 99, expected: '99' },
    { compact: true, count: 100, expected: '99+' },
    { compact: false, count: 100, expected: '100' },
  ])(
    'displays $count as $expected with compact=$compact',
    ({ compact, count, expected }) => {
      render(
        <ItemCounts statistics={{ issuesCount: count }} compact={compact} />
      );

      expect(
        screen.getByRole('button', { name: expected })
      ).toBeInTheDocument();
    }
  );

  it.each([
    { tooltip: undefined, expected: '123 issues in total' },
    {
      tooltip: (label: string, count: number) => `${count} unresolved ${label}`,
      expected: '123 unresolved issues',
    },
  ])(
    'shows "$expected" for a compact overflow count',
    async ({ tooltip, expected }) => {
      const user = userEvent.setup();
      render(
        <ItemCounts
          statistics={{ issuesCount: 123 }}
          compact
          tooltip={tooltip}
        />
      );

      await user.hover(screen.getByRole('button', { name: '99+' }));

      expect(await screen.findByRole('tooltip')).toHaveTextContent(expected);
    }
  );

  it.each(categories)(
    'links $label to its run page with its search parameters',
    async ({ count, route, sort }) => {
      renderLinkedCounts();

      const badge = await screen.findByRole('link', { name: String(count) });
      const url = new URL(badge.getAttribute('href')!, 'http://localhost');
      expect(url.pathname).toBe(`${runPath}/${route}`);
      expect(JSON.parse(url.searchParams.get('sortBy')!)).toEqual([
        { id: sort, desc: true },
      ]);
      const expectedSearch =
        route === 'issues'
          ? link.issuesSearch
          : route === 'vulnerabilities'
            ? link.vulnerabilitiesSearch
            : link.ruleViolationsSearch;
      expect(JSON.parse(url.searchParams.get('itemResolved')!)).toEqual(
        expectedSearch.itemResolved
      );
    }
  );

  it.each(categories)(
    'shows the destination tooltip for $label',
    async ({ count, label }) => {
      const { user } = renderLinkedCounts();

      await user.hover(
        await screen.findByRole('link', { name: String(count) })
      );

      expect(await screen.findByRole('tooltip')).toHaveTextContent(
        `View ${label}`
      );
    }
  );
});
