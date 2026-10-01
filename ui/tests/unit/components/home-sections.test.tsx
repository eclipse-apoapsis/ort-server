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

import { screen, within } from '@testing-library/react';
import { useState } from 'react';
import { expect, it, vi } from 'vitest';

import type { RecentRunItem } from '@/providers/home-data';
import { HomeEmptyState } from '@/routes/-components/home-empty-state';
import { HomeOrganizationsSection } from '@/routes/-components/home-organizations-section';
import { HomeRecentRunsSection } from '@/routes/-components/home-recent-runs-section';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';

vi.mock('@tanstack/react-query', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@tanstack/react-query')>();

  return {
    ...actual,
    useQuery: () => ({ data: undefined, error: null, isSuccess: false }),
  };
});

const createRecentRun = (id: string, runIndex: number): RecentRunItem => ({
  id,
  runId: runIndex,
  runIndex,
  organizationId: 1,
  organizationName: 'Example org',
  productId: 2,
  productName: 'Example product',
  repositoryId: 3,
  repositoryName: 'Example repo',
  to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex',
  params: {
    orgId: '1',
    productId: '2',
    repoId: '3',
    runIndex: String(runIndex),
  },
  recordedAt: '2026-01-01T00:00:00Z',
});

const runsRoute =
  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex';

const renderRecentRuns = (recentRuns: RecentRunItem[]) =>
  renderInteractiveWithRouter(
    <HomeRecentRunsSection recentRuns={recentRuns} />,
    {
      path: '/',
      routes: [{ path: '/' }, { path: runsRoute }],
    }
  );

it('renders children in the home empty state', async () => {
  renderInteractiveWithRouter(
    <HomeEmptyState>No favorites yet</HomeEmptyState>,
    {
      path: '/',
    }
  );

  expect(await screen.findByText('No favorites yet')).toBeInTheDocument();
});

it('links to the organizations page', async () => {
  renderInteractiveWithRouter(<HomeOrganizationsSection />, {
    path: '/',
    routes: [{ path: '/' }, { path: '/organizations' }],
  });

  expect(await screen.findByText('Organizations')).toBeInTheDocument();
  expect(
    screen.getByRole('link', { name: 'Browse organizations' })
  ).toHaveAttribute('href', '/organizations');
});

it('renders the empty recent runs state', async () => {
  renderRecentRuns([]);

  expect(await screen.findByText('Recently started runs')).toBeInTheDocument();
  expect(
    screen.getByText(
      'Runs you start from the UI in this browser will appear here.'
    )
  ).toBeInTheDocument();
  expect(screen.queryByRole('listitem')).not.toBeInTheDocument();
});

it('renders one list item per recent run', async () => {
  renderRecentRuns([createRecentRun('first', 1), createRecentRun('second', 2)]);

  expect(await screen.findAllByRole('listitem')).toHaveLength(2);
  expect(screen.getByText('Run 1')).toBeInTheDocument();
  expect(screen.getByText('Run 2')).toBeInTheDocument();
});

it('shows new runs after the recentRuns prop changes', async () => {
  const first = createRecentRun('first', 1);
  const second = createRecentRun('second', 2);
  const Harness = () => {
    const [recentRuns, setRecentRuns] = useState([first]);

    return (
      <>
        <button type='button' onClick={() => setRecentRuns([second])}>
          Show second run
        </button>
        <HomeRecentRunsSection recentRuns={recentRuns} />
      </>
    );
  };

  const { user } = renderInteractiveWithRouter(<Harness />, {
    path: '/',
    routes: [{ path: '/' }, { path: runsRoute }],
  });

  const list = await screen.findByRole('list');
  expect(within(list).getByText('Run 1')).toBeInTheDocument();

  await user.click(screen.getByRole('button', { name: 'Show second run' }));

  expect(within(list).getByText('Run 2')).toBeInTheDocument();
  expect(within(list).queryByText('Run 1')).not.toBeInTheDocument();
});
