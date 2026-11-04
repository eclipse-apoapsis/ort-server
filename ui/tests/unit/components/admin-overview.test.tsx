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
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { Route } from '@/routes/admin/index';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';
import { getLinkTarget, pageOf } from '../fixtures/table';

const mocks = vi.hoisted(() => ({
  getOrganizations: vi.fn(),
  getProducts: vi.fn(),
  getRepositories: vi.fn(),
  getUsers: vi.fn(),
  getInstalledPlugins: vi.fn(),
  getGlobalDefaultPlugins: vi.fn(),
  getRuns: vi.fn(),
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  ...mocks,
}));

// Serve the number of runs per status.
const serveRuns = () =>
  mocks.getRuns.mockImplementation(({ query }) => {
    const counts: Record<string, number> = {
      finished: 90,
      finished_with_issues: 7,
      failed: 3,
      active: 0,
      created: 0,
    };

    return Promise.resolve(
      pageOf([], { totalCount: query.status ? counts[query.status] : 100 })
    );
  });

const renderOverview = () => {
  const OverviewComponent = Route.options.component!;

  return renderInteractiveWithRouter(<OverviewComponent />, {
    path: '/admin',
    routes: [{ path: '/admin/' }],
    withQueryClient: true,
  });
};

const findArea = (title: string) =>
  screen.findByRole('region', { name: title });

describe('admin overview', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    mocks.getOrganizations.mockResolvedValue(pageOf([], { totalCount: 4 }));
    mocks.getProducts.mockResolvedValue(pageOf([], { totalCount: 10 }));
    mocks.getRepositories.mockResolvedValue(pageOf([], { totalCount: 25 }));
    mocks.getUsers.mockResolvedValue(pageOf([], { totalCount: 6 }));
    mocks.getInstalledPlugins.mockResolvedValue({
      data: [
        { availability: 'ENABLED' },
        { availability: 'ENABLED' },
        { availability: 'DISABLED' },
      ],
    });
    mocks.getGlobalDefaultPlugins.mockResolvedValue({
      data: [
        {
          category: 'ADVISOR',
          pluginIds: ['OSV', 'VulnerableCode'],
          source: 'GLOBAL',
        },
        { category: 'SCANNER', pluginIds: ['ScanCode'], source: 'BUILT_IN' },
        { category: 'REPORTER', pluginIds: [], source: 'BUILT_IN' },
      ],
    });
    serveRuns();
  });

  it('shows each number as soon as its own query has loaded', async () => {
    // The runs never load.
    mocks.getRuns.mockReturnValue(new Promise(() => {}));

    renderOverview();

    const hierarchy = await findArea('Hierarchy');
    const repositories = await within(hierarchy).findByRole('link', {
      name: /25/,
    });

    expect(getLinkTarget(repositories).pathname).toBe('/admin/repositories');
    expect(
      await within(hierarchy).findByText('2.5 per organization')
    ).toBeVisible();
    expect(
      within(await findArea('Runs')).getByText(
        'Loading the number of runs in total...'
      )
    ).toBeInTheDocument();
  });

  it('shows a retry button for a failed number and the other numbers with their links', async () => {
    mocks.getUsers.mockRejectedValue(new Error('Server error'));

    renderOverview();

    const users = await findArea('Users');
    expect(
      await within(users).findByRole('button', { name: /retry/i })
    ).toBeVisible();
    expect(within(users).queryByRole('link', { name: /^6/ })).toBeNull();
    expect(
      getLinkTarget(
        await within(await findArea('Plugins')).findByRole('link', {
          name: /^3\s*Installed plugins/,
        })
      ).pathname
    ).toBe('/admin/plugins');
  });

  it('says how many of the installed plugins are enabled, restricted and disabled', async () => {
    renderOverview();

    expect(
      await within(await findArea('Plugins')).findByText(
        '2 enabled, 1 disabled'
      )
    ).toBeVisible();
  });

  it('counts the default plugins of all categories and says how many are built-in and set on this server', async () => {
    renderOverview();

    const plugins = await findArea('Plugins');
    const defaults = await within(plugins).findByRole('link', {
      name: /^3\s*Default plugins/,
    });

    expect(getLinkTarget(defaults).pathname).toBe('/admin/plugins/defaults');
    expect(
      within(plugins).getByText('1 built-in, 2 set on this server')
    ).toBeVisible();
  });

  it('links the number of runs per status to the runs with that status', async () => {
    renderOverview();

    const statuses = within(await findArea('Runs')).getByRole('list', {
      name: 'Finished runs by status',
    });
    const failed = await within(statuses).findByRole('link', {
      name: /Failed\s*3/,
    });

    expect(getLinkTarget(failed)).toEqual({
      pathname: '/admin/runs',
      search: { status: ['FAILED'] },
    });
  });
});
