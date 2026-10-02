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

import { defaultParseSearch } from '@tanstack/react-router';
import { act, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { GetRunIssuesData, Issue } from '@/api';
import { TooltipProvider } from '@/components/ui/tooltip';
import { identifierToString } from '@/helpers/identifier-conversion';
import { Route } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/issues/index';
import { packageIdTypeSchema } from '@/schemas';
import { useUserSettingsStore } from '@/store/user-settings.store';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';

const mocks = vi.hoisted(() => ({
  run: vi.fn(),
  issues: vi.fn(),
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  getRepositoryRun: mocks.run,
  getRunIssues: mocks.issues,
}));

vi.mock('@/components/resolutions', () => ({
  Resolutions: () => null,
}));

vi.mock(
  '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/issues/-components/issue-details',
  () => ({
    IssueDetails: ({ issue }: { issue: Issue }) => (
      <div>Details of {issue.message}</div>
    ),
  })
);

const IssuesComponent = Route.options.component!;
const identifier = {
  type: 'Maven',
  namespace: 'com.example',
  name: 'library',
  version: '1.0',
};
const id = identifierToString(identifier);
const purl = 'pkg:maven/com.example/library@1.0';

beforeEach(() => {
  vi.clearAllMocks();
  useUserSettingsStore.setState({
    packageIdType: packageIdTypeSchema.enum.PURL,
  });
  mocks.run.mockResolvedValue({ data: { id: 42, jobs: {} } });
});

const renderIssue = (issuePurl: string | null) => {
  const issue: Issue = {
    identifier,
    purl: issuePurl,
    message: 'An issue',
    severity: 'WARNING',
    source: 'Analyzer',
    timestamp: '2026-01-01T00:00:00Z',
  };
  mocks.issues.mockResolvedValue({
    data: {
      data: [issue],
      pagination: { totalCount: 1, limit: 10, offset: 0 },
    },
  });

  renderInteractiveWithRouter(
    <TooltipProvider>
      <IssuesComponent />
    </TooltipProvider>,
    {
      path: '/organizations/1/products/2/repositories/3/runs/4/issues',
      routes: [
        {
          path: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/issues/',
        },
      ],
      withQueryClient: true,
    }
  );
};

it.each([
  {
    purl: '',
    linkText: id,
    table: 'packages',
    search: { pkgId: id, pkgIdType: 'ORT_ID', marked: '0' },
  },
  {
    purl,
    linkText: purl,
    table: 'packages',
    search: { pkgId: purl, pkgIdType: 'PURL', marked: '0' },
  },
  {
    purl: null,
    linkText: id,
    table: 'projects',
    search: { projectId: id, marked: id },
  },
])(
  'links an issue with PURL $purl to $table',
  async ({ purl, linkText, table, search }) => {
    renderIssue(purl);

    const link = await screen.findByRole('link', { name: linkText });
    const target = new URL(link.getAttribute('href')!, 'http://localhost');

    expect(target.pathname).toBe(
      `/organizations/1/products/2/repositories/3/runs/4/${table}`
    );
    expect(defaultParseSearch(target.search)).toEqual(search);
  }
);

describe('issues table', () => {
  type IssuesQuery = NonNullable<GetRunIssuesData['query']>;

  // The first part of the issue names, which tells the query the issues come from.
  let nameTag = 'issue';

  const purlOf = (name: string) => `pkg:maven/com.example/${name}@1.0`;

  // Serve three issues per page, named after the query and their position.
  const getIssuesPage = (query: IssuesQuery = {}) => {
    const tag = query.severity ? 'error' : query.sort ? 'sorted' : nameTag;
    const offset = query.offset ?? 0;
    const data: Issue[] = [1, 2, 3].map((position) => {
      const name = `${tag}-${offset + position}`;

      return {
        identifier: { ...identifier, name },
        purl: purlOf(name),
        message: name,
        severity: 'WARNING',
        source: 'Analyzer',
        timestamp: '2026-01-01T00:00:00Z',
      };
    });

    return {
      data,
      pagination: {
        limit: query.limit ?? 10,
        offset,
        totalCount: query.severity ? 3 : 25,
      },
    };
  };

  const renderIssues = (search = '') =>
    renderInteractiveWithRouter(
      <TooltipProvider>
        <IssuesComponent />
      </TooltipProvider>,
      {
        path: `/organizations/1/products/2/repositories/3/runs/4/issues${search}`,
        routes: [
          {
            path: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/issues/',
          },
        ],
        withQueryClient: true,
      }
    );

  const findIssue = (name: string) =>
    screen.findByRole('link', { name: purlOf(name) });

  // The button that expands and collapses the row of the given issue.
  const getDetailsButton = (name: string) =>
    within(
      screen.getByRole('link', { name: purlOf(name) }).closest('tr')!
    ).getAllByRole('button')[0]!;

  const lastIssuesQuery = () =>
    mocks.issues.mock.calls.at(-1)?.[0].query as IssuesQuery;

  beforeEach(() => {
    nameTag = 'issue';
    mocks.issues.mockImplementation(
      async ({ query }: { query?: IssuesQuery }) => ({
        data: getIssuesPage(query),
      })
    );
  });

  it('is compiled by React Compiler', () => {
    expect(IssuesComponent.toString()).toContain('react.memo_cache_sentinel');
  });

  it('applies filters from the URL', async () => {
    const { container, router } = renderIssues();

    expect(await findIssue('issue-1')).toBeVisible();

    await act(() =>
      router.navigate({
        to: '.',
        search: { severity: ['ERROR'], itemResolved: ['Resolved'] },
      })
    );

    expect(await findIssue('error-1')).toBeVisible();
    expect(
      screen.queryByRole('link', { name: purlOf('issue-1') })
    ).not.toBeInTheDocument();
    expect(lastIssuesQuery()).toMatchObject({
      severity: 'ERROR',
      resolved: true,
    });
    expect(container).toHaveTextContent(
      'Issues (25 in total, 3 matching filters)'
    );
  });

  it('applies sorting and the page from the URL', async () => {
    const { router } = renderIssues();

    expect(await findIssue('issue-1')).toBeVisible();

    await act(() =>
      router.navigate({
        to: '.',
        search: { sortBy: [{ id: 'severity', desc: true }], page: 2 },
      })
    );

    expect(await findIssue('sorted-11')).toBeVisible();
    expect(lastIssuesQuery()).toMatchObject({
      sort: '-severity',
      limit: 10,
      offset: 10,
    });
  });

  it('expands and collapses a row', async () => {
    const { user } = renderIssues();

    expect(await findIssue('issue-1')).toBeVisible();
    expect(screen.queryByText(/^Details of/)).not.toBeInTheDocument();

    await user.click(getDetailsButton('issue-1'));

    expect(await screen.findByText('Details of issue-1')).toBeVisible();
    expect(
      getDetailsButton('issue-1').querySelector('.lucide-chevron-up')
    ).toBeInTheDocument();
    expect(screen.queryByText('Details of issue-2')).not.toBeInTheDocument();
    expect(
      getDetailsButton('issue-2').querySelector('.lucide-chevron-down')
    ).toBeInTheDocument();

    await user.click(getDetailsButton('issue-1'));

    await waitFor(() =>
      expect(screen.queryByText('Details of issue-1')).not.toBeInTheDocument()
    );
    expect(
      getDetailsButton('issue-1').querySelector('.lucide-chevron-down')
    ).toBeInTheDocument();
  });

  it('expands the row marked in the URL', async () => {
    renderIssues('?marked=1');

    expect(await screen.findByText('Details of issue-2')).toBeVisible();
    expect(screen.queryByText('Details of issue-1')).not.toBeInTheDocument();
  });

  it('replaces the rows and collapses them when the query data is refreshed', async () => {
    const { queryClient, user } = renderIssues();

    expect(await findIssue('issue-1')).toBeVisible();
    await user.click(getDetailsButton('issue-1'));
    expect(await screen.findByText('Details of issue-1')).toBeVisible();

    nameTag = 'renamed';
    await queryClient!.invalidateQueries();

    await waitFor(() =>
      expect(
        screen.getByRole('link', { name: purlOf('renamed-1') })
      ).toBeVisible()
    );
    expect(
      screen.queryByRole('link', { name: purlOf('issue-1') })
    ).not.toBeInTheDocument();
    // The table collapses expanded rows when it receives new data.
    expect(screen.queryByText(/^Details of/)).not.toBeInTheDocument();
  });
});
