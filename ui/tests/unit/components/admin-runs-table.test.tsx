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

import { screen, waitFor, within } from '@testing-library/react';
import { Suspense } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { OrtRunSummary } from '@/api';
import { Route } from '@/routes/admin/runs';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';
import { getLinkTarget, pageOf } from '../fixtures/table';

const mocks = vi.hoisted(() => ({
  getRuns: vi.fn(),
  getOrganization: vi.fn(),
  getProduct: vi.fn(),
  getRepository: vi.fn(),
  deleteRepositoryRun: vi.fn(),
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  ...mocks,
}));

// Poll rarely, so that the runs are fetched again only after deleting a run.
vi.mock('@/config', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/config')>();

  return { config: { ...original.config, pollInterval: 600_000 } };
});

vi.mock('@/components/ort-run-job-status', () => ({
  OrtRunJobStatus: () => null,
}));

vi.mock('@/components/run-duration', () => ({
  RunDuration: () => null,
}));

const run: OrtRunSummary = {
  id: 104,
  index: 4,
  organizationId: 1,
  productId: 2,
  repositoryId: 3,
  revision: 'main',
  createdAt: '2026-01-01T00:00:00Z',
  jobs: {},
  labels: {},
  status: 'FAILED',
};

// The server knows 12 runs, of which `matching` match the status filter.
const serveRuns = (matching: number) =>
  mocks.getRuns.mockImplementation(({ query }) =>
    Promise.resolve(
      pageOf([run], { totalCount: query.limit === 1 ? 12 : matching })
    )
  );

// The queries for the page of runs, without the query for the total count.
const pageQueries = () =>
  mocks.getRuns.mock.calls
    .map(([options]) => options.query)
    .filter((query) => query.limit !== 1);

const renderRuns = (search = '') => {
  const RunsComponent = Route.options.component!;

  return renderInteractiveWithRouter(
    <Suspense>
      <RunsComponent />
    </Suspense>,
    {
      path: `/admin/runs${search}`,
      routes: [{ path: '/admin/runs/' }],
      withQueryClient: true,
    }
  );
};

const getRow = () =>
  screen.getByText('https://example.com/repo.git').closest('tr')!;

const openStatusFilter = async (
  user: ReturnType<typeof renderRuns>['user']
) => {
  const header = screen.getByRole('columnheader', { name: /Status/ });
  await user.click(within(header).getByRole('button'));
};

describe('admin runs table', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    serveRuns(12);
    mocks.getOrganization.mockResolvedValue({
      data: { id: 1, name: 'Organization' },
    });
    mocks.getProduct.mockResolvedValue({
      data: { id: 2, organizationId: 1, name: 'Product' },
    });
    mocks.getRepository.mockResolvedValue({
      data: {
        id: 3,
        organizationId: 1,
        productId: 2,
        type: 'GIT',
        url: 'https://example.com/repo.git',
      },
    });
    mocks.deleteRepositoryRun.mockResolvedValue({ data: undefined });
  });

  it('queries the page and the status filter from the URL and shows how many runs match', async () => {
    serveRuns(3);
    renderRuns('?page=2&status=["FAILED","ACTIVE"]');

    expect(
      await screen.findByText('Runs (12 in total, 3 matching filters)')
    ).toBeVisible();
    expect(pageQueries()).toEqual([
      { limit: 10, offset: 10, status: 'FAILED,ACTIVE' },
    ]);
  });

  it('shows only the total count without a filter', async () => {
    renderRuns();

    expect(await screen.findByText('Runs (12 in total)')).toBeVisible();
    expect(pageQueries()).toEqual([
      { limit: 10, offset: 0, status: undefined },
    ]);
  });

  it('returns to the first page when selecting a status', async () => {
    const { router, user } = renderRuns('?page=2');

    await screen.findByText('https://example.com/repo.git');
    await openStatusFilter(user);
    await user.click(await screen.findByRole('option', { name: 'FAILED' }));

    expect(router.state.location.search).toEqual({
      page: 1,
      status: ['FAILED'],
    });
  });

  it('removes the status from the URL when clearing the last status', async () => {
    const { router, user } = renderRuns('?page=2&status=["FAILED"]');

    await screen.findByText('https://example.com/repo.git');
    await openStatusFilter(user);
    await user.click(await screen.findByRole('option', { name: 'FAILED' }));

    expect(router.state.location.search).toEqual({ page: 1 });
  });

  it('links a run to its repository, organization, product, details and rerun', async () => {
    renderRuns();

    await screen.findByText('Organization');
    const links = within(getRow()).getAllByRole('link').map(getLinkTarget);
    const repositoryPath = '/organizations/1/products/2/repositories/3';

    expect(links).toEqual([
      { pathname: repositoryPath, search: {} },
      { pathname: '/organizations/1', search: {} },
      { pathname: '/organizations/1/products/2', search: {} },
      { pathname: `${repositoryPath}/runs/4`, search: {} },
      { pathname: `${repositoryPath}/create-run`, search: { rerunIndex: 4 } },
    ]);
  });

  it('deletes a run after confirmation and refreshes the runs', async () => {
    const { user } = renderRuns();

    await screen.findByText('https://example.com/repo.git');
    await user.click(within(getRow()).getByRole('button', { name: 'Delete' }));
    await user.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', {
        name: 'Delete',
      })
    );

    expect(mocks.deleteRepositoryRun).toHaveBeenCalledWith(
      expect.objectContaining({ path: { ortRunIndex: 4, repositoryId: 3 } })
    );
    await waitFor(() => expect(pageQueries()).toHaveLength(2));
  });
});
