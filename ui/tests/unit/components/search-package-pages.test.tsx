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

import { act, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { RunWithPackage } from '@/api/types.gen';
import { Route as RepositoryRoute } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/search-package';
import { Route as ProductRoute } from '@/routes/organizations/$orgId/products/$productId/search-package';
import { Route as OrganizationRoute } from '@/routes/organizations/$orgId/search-package';
import { packageIdTypeSchema } from '@/schemas';
import { useUserSettingsStore } from '@/store/user-settings.store';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';

const mocks = vi.hoisted(() => ({
  runs: vi.fn(),
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  getRunsWithPackage: mocks.runs,
}));

vi.mock(
  '@/routes/organizations/$orgId/-components/search-result-cells',
  () => ({
    SearchResultProductCell: () => null,
    SearchResultRepositoryCell: () => null,
  })
);

const createRuns = (count: number): RunWithPackage[] =>
  Array.from({ length: count }, (_, index) => {
    const ortRunIndex = index + 1;

    return {
      createdAt: '2026-01-01T00:00:00Z',
      organizationId: 1,
      ortRunId: ortRunIndex,
      ortRunIndex,
      packageId: {
        type: 'Maven',
        namespace: 'org.example',
        name: `library-${ortRunIndex}`,
        version: '1.0',
      },
      productId: 2,
      purl: `pkg:maven/org.example/library-${ortRunIndex}@1.0`,
      repositoryId: 3,
      revision: `revision-${ortRunIndex}`,
    };
  });

const SearchPackageComponent = OrganizationRoute.options.component!;

const renderSearch = (search = '') =>
  renderInteractiveWithRouter(<SearchPackageComponent />, {
    path: `/organizations/1/search-package${search}`,
    routes: [{ path: '/organizations/$orgId/search-package/' }],
    withQueryClient: true,
  });

// The texts of the cells in the column with the given header, from top to bottom.
const getColumnTexts = (header: string) => {
  const columnIndex = screen
    .getAllByRole('columnheader')
    .findIndex((columnHeader) => columnHeader.textContent.includes(header));
  expect(columnIndex).toBeGreaterThanOrEqual(0);

  return screen
    .getAllByRole('row')
    .slice(1)
    .map(
      (row) => within(row).getAllByRole('cell').at(columnIndex)?.textContent
    );
};

const revisions = (first: number, last: number) =>
  Array.from(
    { length: Math.abs(last - first) + 1 },
    (_, index) => `revision-${first < last ? first + index : first - index}`
  );

describe('package search pages', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.runs.mockResolvedValue({ data: createRuns(3) });
  });

  afterEach(() => {
    useUserSettingsStore.setState({
      packageIdType: packageIdTypeSchema.enum.ORT_ID,
    });
  });

  it.each([
    { level: 'organization', route: OrganizationRoute },
    { level: 'product', route: ProductRoute },
    { level: 'repository', route: RepositoryRoute },
  ])('compiles the $level level page with React Compiler', ({ route }) => {
    expect(route.options.component!.toString()).toContain(
      'react.memo_cache_sentinel'
    );
  });

  it('searches for the submitted term, starting from the first page', async () => {
    const { router, user } = renderSearch('?page=2');

    const input = await screen.findByPlaceholderText('(regular expression)');
    expect(screen.queryByText('revision-1')).not.toBeInTheDocument();
    expect(mocks.runs).not.toHaveBeenCalled();

    await user.type(input, 'example{Enter}');

    expect(await screen.findByText('revision-1')).toBeVisible();
    expect(router.state.location.search).toMatchObject({
      pkgId: 'example',
      page: 1,
    });
    expect(mocks.runs.mock.calls.at(-1)?.[0]).toMatchObject({
      query: { identifier: 'example', organizationId: 1 },
    });
  });

  it('pages and sorts the result in the browser', async () => {
    mocks.runs.mockResolvedValue({ data: createRuns(25).reverse() });

    const { router, user } = renderSearch('?pkgId=example');

    expect(await screen.findByText('revision-25')).toBeVisible();
    expect(getColumnTexts('Revision')).toEqual(revisions(25, 16));

    await user.click(screen.getByRole('link', { name: 'Go to next page' }));

    await waitFor(() =>
      expect(getColumnTexts('Revision')).toEqual(revisions(15, 6))
    );

    await user.click(
      within(screen.getByText('Run Index').closest('th')!).getByRole('link')
    );

    await waitFor(() =>
      expect(getColumnTexts('Revision')).toEqual(revisions(11, 20))
    );
    expect(router.state.location.search).toMatchObject({
      page: 2,
      sortBy: [{ id: 'ortRunIndex', desc: false }],
    });
    expect(mocks.runs).toHaveBeenCalledOnce();
  });

  it('shows the other package identifier when the identifier type changes', async () => {
    renderSearch('?pkgId=example');

    expect(await screen.findByText('revision-1')).toBeVisible();
    expect(getColumnTexts('Matching Package')[0]).toBe(
      'Maven:org.example:library-1:1.0'
    );

    act(() =>
      useUserSettingsStore.setState({
        packageIdType: packageIdTypeSchema.enum.PURL,
      })
    );

    await waitFor(() =>
      expect(getColumnTexts('Matching Package')[0]).toBe(
        'pkg:maven/org.example/library-1@1.0'
      )
    );
    expect(mocks.runs.mock.calls.at(-1)?.[0]).toMatchObject({
      query: { purl: 'example', organizationId: 1 },
    });
  });
});
