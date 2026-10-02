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

import { screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { Organization, Product, Repository } from '@/api';
import { getProductRepositoriesQueryKey } from '@/api/@tanstack/react-query.gen';
import { ProductRepositoryTable } from '@/routes/organizations/$orgId/products/$productId/-components/product-repository-table';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';

const mocks = vi.hoisted(() => ({
  organization: vi.fn(),
  product: vi.fn(),
  repositories: vi.fn(),
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  getOrganization: mocks.organization,
  getProduct: mocks.product,
  getProductRepositories: mocks.repositories,
}));

vi.mock('@/components/favorite-button', () => ({
  RepositoryFavoriteButton: ({ repository }: { repository: Repository }) => (
    <button type='button'>Add to favorites: {repository.name}</button>
  ),
}));

vi.mock(
  '@/routes/organizations/$orgId/products/$productId/-components/total-runs',
  () => ({ TotalRuns: () => null })
);

vi.mock(
  '@/routes/organizations/$orgId/products/$productId/-components/last-run-status',
  () => ({ LastRunStatus: () => null })
);

vi.mock(
  '@/routes/organizations/$orgId/products/$productId/-components/last-run-date',
  () => ({ LastRunDate: () => null })
);

vi.mock(
  '@/routes/organizations/$orgId/products/$productId/-components/last-job-status',
  () => ({ LastJobStatus: () => null })
);

vi.mock(
  '@/routes/organizations/$orgId/products/$productId/-components/repository-item-counts',
  () => ({ RepositoryItemCounts: () => null })
);

const organization: Organization = { id: 1, name: 'organization' };
const product: Product = { id: 2, organizationId: 1, name: 'product' };

const repositories = (...names: string[]) => ({
  data: {
    data: names.map((name, index): Repository => ({
      id: index + 3,
      organizationId: 1,
      productId: 2,
      type: 'GIT',
      url: `https://example.com/${name}.git`,
      name,
    })),
    pagination: { limit: 5, offset: 0, totalCount: names.length },
  },
});

// Create a promise together with the function that resolves it.
const deferred = <T,>() => {
  let resolve = (value: T) => {
    void value;
  };
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise;
  });

  return { promise, resolve };
};

const renderTable = () =>
  renderInteractiveWithRouter(<ProductRepositoryTable />, {
    path: '/organizations/1/products/2/',
    routes: [{ path: '/organizations/$orgId/products/$productId/' }],
    withQueryClient: true,
  });

describe('ProductRepositoryTable', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.organization.mockResolvedValue({ data: organization });
    mocks.product.mockResolvedValue({ data: product });
    mocks.repositories.mockResolvedValue(repositories('first', 'second'));
  });

  it('is compiled by React Compiler', () => {
    expect(ProductRepositoryTable.toString()).toContain(
      'react.memo_cache_sentinel'
    );
  });

  it('shows the favorite buttons once the organization and the product are loaded', async () => {
    const loadedOrganization = deferred<{ data: Organization }>();
    const loadedProduct = deferred<{ data: Product }>();
    mocks.organization.mockReturnValue(loadedOrganization.promise);
    mocks.product.mockReturnValue(loadedProduct.promise);

    renderTable();

    expect(await screen.findByRole('link', { name: 'first' })).toBeVisible();

    loadedOrganization.resolve({ data: organization });

    await waitFor(() => expect(mocks.organization).toHaveResolved());
    expect(
      screen.queryByRole('button', { name: 'Add to favorites: first' })
    ).not.toBeInTheDocument();

    loadedProduct.resolve({ data: product });

    expect(
      await screen.findByRole('button', { name: 'Add to favorites: first' })
    ).toBeVisible();
    expect(
      screen.getByRole('button', { name: 'Add to favorites: second' })
    ).toBeVisible();
    expect(mocks.repositories).toHaveBeenCalledOnce();
  });

  it('replaces the rows when the query data is refreshed', async () => {
    const { queryClient, router } = renderTable();

    expect(await screen.findByRole('link', { name: 'first' })).toBeVisible();
    const initialHref = router.state.location.href;

    mocks.repositories.mockResolvedValue(repositories('renamed'));
    await queryClient!.invalidateQueries({
      queryKey: getProductRepositoriesQueryKey({ path: { productId: 2 } }),
    });

    await waitFor(() =>
      expect(screen.getByRole('link', { name: 'renamed' })).toBeVisible()
    );
    expect(
      screen.queryByRole('link', { name: 'first' })
    ).not.toBeInTheDocument();
    expect(router.state.location.href).toBe(initialHref);
  });
});
