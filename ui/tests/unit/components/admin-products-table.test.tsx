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

import type { Product } from '@/api';
import { Route } from '@/routes/admin/products';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';
import { getLinkTarget, pageOf } from '../fixtures/table';

const mocks = vi.hoisted(() => ({
  getProducts: vi.fn(),
  getOrganization: vi.fn(),
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  ...mocks,
}));

const product: Product = {
  id: 2,
  organizationId: 1,
  name: 'Web shop',
  description: 'A product',
};

const renderProducts = (search = '') => {
  const ProductsComponent = Route.options.component!;

  return renderInteractiveWithRouter(
    <Suspense>
      <ProductsComponent />
    </Suspense>,
    {
      path: `/admin/products${search}`,
      routes: [{ path: '/admin/products/' }],
      withQueryClient: true,
    }
  );
};

describe('admin products table', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    // All products are counted with a limit of one, the page holds the matching ones.
    mocks.getProducts.mockImplementation(({ query }) =>
      Promise.resolve(
        query.limit === 1
          ? pageOf([product], { totalCount: 42 })
          : pageOf([product], { totalCount: query.filter ? 3 : 42 })
      )
    );
    mocks.getOrganization.mockResolvedValue({
      data: { id: 1, name: 'Retail' },
    });
  });

  it('queries the page from the URL and shows the number of all products', async () => {
    renderProducts('?page=3&pageSize=20');

    expect(await screen.findByText('Products (42 in total)')).toBeVisible();
    expect(
      mocks.getProducts.mock.calls.map(([options]) => options.query)
    ).toContainEqual({ limit: 20, offset: 40 });
  });

  it('queries the products matching the filter from the URL and shows their number', async () => {
    renderProducts('?filter=shop');

    expect(
      await screen.findByText('Products (42 in total, 3 matching filters)')
    ).toBeVisible();
    expect(
      mocks.getProducts.mock.calls.map(([options]) => options.query)
    ).toContainEqual({ limit: 10, offset: 0, filter: 'shop' });
  });

  it('links a product to its overview and to its organization', async () => {
    renderProducts();

    const row = (await screen.findByText('Web shop')).closest('tr')!;

    expect(
      getLinkTarget(within(row).getByRole('link', { name: 'Web shop' }))
        .pathname
    ).toBe('/organizations/1/products/2');
    expect(
      getLinkTarget(await within(row).findByRole('link', { name: 'Retail' }))
        .pathname
    ).toBe('/organizations/1');
    expect(within(row).getByText('A product')).toBeVisible();
  });
});
