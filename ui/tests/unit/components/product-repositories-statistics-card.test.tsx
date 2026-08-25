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

import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ProductRepositoriesStatisticsCard } from '@/routes/organizations/$orgId/products/$productId/-components/product-repositories-statistics-card';
import { renderStaticWithRouter } from '../fixtures/router-harness';

const mocks = vi.hoisted(() => ({
  canCreateRepository: true,
  useProductPermission: vi.fn(),
  useSuspenseQuery: vi.fn(),
}));

vi.mock('@tanstack/react-query', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@tanstack/react-query')>();

  return {
    ...actual,
    useSuspenseQuery: mocks.useSuspenseQuery,
  };
});

vi.mock('@/hooks/use-authorization', () => ({
  useProductPermission: mocks.useProductPermission,
}));

const renderCard = () =>
  renderStaticWithRouter(
    <ProductRepositoriesStatisticsCard
      orgId='42'
      productId='84'
      className='col-span-2'
    />,
    {
      path: '/organizations/42/products/84',
      routes: [
        { path: '/organizations/$orgId/products/$productId' },
        {
          path: '/organizations/$orgId/products/$productId/create-repository',
        },
      ],
    }
  );

describe('ProductRepositoriesStatisticsCard', () => {
  beforeEach(() => {
    mocks.canCreateRepository = true;
    mocks.useProductPermission.mockReset();
    mocks.useProductPermission.mockImplementation(() => ({
      isAllowed: mocks.canCreateRepository,
    }));
    mocks.useSuspenseQuery.mockReset();
    mocks.useSuspenseQuery.mockReturnValue({
      data: { pagination: { totalCount: 12 } },
    });
  });

  it('renders the repository total and add action', async () => {
    const markup = await renderCard();

    expect(markup).toContain('Repositories');
    expect(markup).toContain('>12<');
    expect(markup).toContain('Add repository');
    expect(markup).toContain(
      'href="/organizations/42/products/84/create-repository"'
    );
    expect(markup).toContain('col-span-2');
    expect(mocks.useProductPermission).toHaveBeenCalledWith(
      84,
      'CREATE_REPOSITORY'
    );
  });

  it('disables the add action without permission', async () => {
    mocks.canCreateRepository = false;

    const markup = await renderCard();

    expect(markup).toContain('aria-disabled="true"');
  });
});
