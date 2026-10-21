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

import { act, screen, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { GetRunIssuesData, Issue } from '@/api';
import { identifierToString } from '@/helpers/identifier-conversion';
import { Route } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/issues/index';
import { packageIdTypeSchema } from '@/schemas';
import { useUserSettingsStore } from '@/store/user-settings.store';
import { renderRunPage } from '../fixtures/finding-tables';
import { getLinkTarget, pageOf } from '../fixtures/table';

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
  mocks.issues.mockResolvedValue(pageOf([issue]));

  renderRunPage(IssuesComponent, 'issues');
};

it.each([
  {
    purl,
    linkText: purl,
    table: 'packages',
    search: {
      pkgId: id,
      pkgIdType: 'ORT_ID',
      pkgIdMatchType: 'exact',
      marked: id,
    },
  },
  {
    purl: null,
    linkText: id,
    table: 'projects',
    search: { projectId: id, projectIdMatchType: 'exact', marked: id },
  },
])(
  'links an issue with PURL $purl to $table',
  async ({ purl, linkText, table, search }) => {
    renderIssue(purl);

    expect(
      getLinkTarget(await screen.findByRole('link', { name: linkText }))
    ).toEqual({
      pathname: `/organizations/1/products/2/repositories/3/runs/4/${table}`,
      search,
    });
  }
);

describe('issues table', () => {
  type IssuesQuery = NonNullable<GetRunIssuesData['query']>;

  const purlOf = (name: string) => `pkg:maven/com.example/${name}@1.0`;

  // Serve three issues per page, named after the query and their position.
  const getIssuesPage = (query: IssuesQuery = {}) => {
    const tag = query.severity ? 'error' : query.sort ? 'sorted' : 'issue';
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

    return pageOf(data, {
      limit: query.limit,
      offset,
      totalCount: query.severity ? 3 : 25,
    });
  };

  const renderIssues = (search = '') =>
    renderRunPage(IssuesComponent, 'issues', search);

  const findIssue = (name: string) =>
    screen.findByRole('link', { name: purlOf(name) });

  // The button that expands the row of the given issue.
  const getDetailsButton = (name: string) =>
    within(
      screen.getByRole('link', { name: purlOf(name) }).closest('tr')!
    ).getAllByRole('button')[0]!;

  const lastIssuesQuery = () =>
    mocks.issues.mock.calls.at(-1)?.[0].query as IssuesQuery;

  beforeEach(() => {
    mocks.issues.mockImplementation(
      async ({ query }: { query?: IssuesQuery }) => getIssuesPage(query)
    );
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

  it('shows the details of an expanded row', async () => {
    const { user } = renderIssues();

    expect(await findIssue('issue-1')).toBeVisible();

    await user.click(getDetailsButton('issue-1'));

    expect(await screen.findByText('Details of issue-1')).toBeVisible();
  });

  it('expands the row marked in the URL', async () => {
    renderIssues('?marked=1');

    expect(await screen.findByText('Details of issue-2')).toBeVisible();
    expect(screen.queryByText('Details of issue-1')).not.toBeInTheDocument();
  });
});
