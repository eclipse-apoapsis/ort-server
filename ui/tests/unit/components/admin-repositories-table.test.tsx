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
import { Suspense } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { Repository } from '@/api';
import { Route } from '@/routes/admin/repositories';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';
import { getLinkTarget, pageOf } from '../fixtures/table';

const mocks = vi.hoisted(() => ({
  getRepositories: vi.fn(),
  getProduct: vi.fn(),
  getOrganization: vi.fn(),
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  ...mocks,
}));

const repository: Repository = {
  id: 3,
  organizationId: 1,
  productId: 2,
  type: 'GIT',
  url: 'https://example.com/shop.git',
  name: 'shop',
  description: 'The shop front end',
};

const renderRepositories = (search = '') => {
  const RepositoriesComponent = Route.options.component!;

  return renderInteractiveWithRouter(
    <Suspense>
      <RepositoriesComponent />
    </Suspense>,
    {
      path: `/admin/repositories${search}`,
      routes: [{ path: '/admin/repositories/' }],
      withQueryClient: true,
    }
  );
};

describe('admin repositories table', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    // All repositories are counted with a limit of one, the page holds the matching ones.
    mocks.getRepositories.mockImplementation(({ query }) =>
      Promise.resolve(
        query.limit === 1
          ? pageOf([repository], { totalCount: 57 })
          : pageOf([repository], { totalCount: query.filter ? 4 : 57 })
      )
    );
    mocks.getProduct.mockResolvedValue({
      data: { id: 2, organizationId: 1, name: 'Web shop' },
    });
    mocks.getOrganization.mockResolvedValue({
      data: { id: 1, name: 'Retail' },
    });
  });

  it('queries the page from the URL and shows the number of all repositories', async () => {
    renderRepositories('?page=2&pageSize=20');

    expect(await screen.findByText('Repositories (57 in total)')).toBeVisible();
    expect(
      mocks.getRepositories.mock.calls.map(([options]) => options.query)
    ).toContainEqual({ limit: 20, offset: 20 });
  });

  it('queries the repositories matching the filter from the URL and shows their number', async () => {
    renderRepositories('?filter=shop');

    expect(
      await screen.findByText('Repositories (57 in total, 4 matching filters)')
    ).toBeVisible();
    expect(
      mocks.getRepositories.mock.calls.map(([options]) => options.query)
    ).toContainEqual({ limit: 10, offset: 0, filter: 'shop' });
  });

  it('links a repository to its overview, its product and its organization', async () => {
    renderRepositories();

    const link = await screen.findByRole('link', {
      name: 'https://example.com/shop.git',
    });
    const row = link.closest('tr')!;

    expect(getLinkTarget(link).pathname).toBe(
      '/organizations/1/products/2/repositories/3'
    );
    expect(
      getLinkTarget(await within(row).findByRole('link', { name: 'Web shop' }))
        .pathname
    ).toBe('/organizations/1/products/2');
    expect(
      getLinkTarget(await within(row).findByRole('link', { name: 'Retail' }))
        .pathname
    ).toBe('/organizations/1');
    expect(within(row).getByText('shop')).toBeVisible();
    expect(within(row).getByText('The shop front end')).toBeVisible();
  });
});
