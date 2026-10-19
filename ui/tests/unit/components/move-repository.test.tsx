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

import type { QueryClient, QueryKey } from '@tanstack/react-query';
import { screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { Repository } from '@/api';
import {
  getProductRepositoriesQueryKey,
  getUserInfoQueryKey,
} from '@/api/@tanstack/react-query.gen';
import { TooltipProvider } from '@/components/ui/tooltip';
import { MoveRepository } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/-components';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';
import { pageOf } from '../fixtures/table';

const mocks = vi.hoisted(() => ({
  getOrganizations: vi.fn(),
  getOrganizationProducts: vi.fn(),
  patchRepository: vi.fn(),
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  ...mocks,
}));

vi.mock('@/lib/toast', () => ({
  toast: { info: vi.fn() },
  toastError: vi.fn(),
}));

const repository: Repository = {
  id: 3,
  organizationId: 1,
  productId: 2,
  type: 'GIT',
  url: 'https://example.com/repository.git',
};

const productsByOrganization: Record<number, { id: number; name: string }[]> = {
  1: [{ id: 2, name: 'Product' }],
  4: [{ id: 5, name: 'Other product' }],
};

const repositoriesOf = (productId: number) =>
  getProductRepositoriesQueryKey({ path: { productId } });
const userInfoKey = getUserInfoQueryKey({ query: { repositoryId: 3 } });

// The page the move navigates to, which the tests only need to exist.
const Destination = () => <div>Destination</div>;

const renderMoveRepository = async () => {
  const result = renderInteractiveWithRouter(
    <TooltipProvider>
      <MoveRepository repoUrl={repository.url} />
    </TooltipProvider>,
    {
      path: '/organizations/1/products/2/repositories/3/settings',
      routes: [
        {
          path: '/organizations/$orgId/products/$productId/repositories/$repoId/settings',
        },
        {
          path: '/organizations/$orgId/products/$productId/repositories/$repoId',
          component: Destination,
        },
      ],
      withQueryClient: true,
    }
  );

  await screen.findByText('Select organization');

  return { ...result, queryClient: result.queryClient! };
};

// Put something in the cache, so that there is a query to invalidate.
const seed = (queryClient: QueryClient, ...keys: QueryKey[]) => {
  for (const key of keys) queryClient.setQueryData(key, { seeded: true });
};

const isInvalidated = (queryClient: QueryClient, key: QueryKey) =>
  queryClient.getQueryState(key)?.isInvalidated;

// A combobox takes no name from the text it shows, so the dropdowns are told apart by their
// position.
const organizationList = () => screen.getAllByRole('combobox')[0]!;
const productList = () => screen.getAllByRole('combobox')[1]!;

const choose = async (
  user: Awaited<ReturnType<typeof renderMoveRepository>>['user'],
  list: HTMLElement,
  option: string
) => {
  await user.click(list);
  await user.click(await screen.findByRole('option', { name: option }));
};

const moveButton = () =>
  screen.getByRole('button', { name: 'Move repository' });

describe('MoveRepository', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // The dropdowns ask for their next page when their end scrolls into view.
    vi.stubGlobal(
      'IntersectionObserver',
      class {
        observe() {}
        disconnect() {}
      }
    );

    mocks.getOrganizations.mockResolvedValue(
      pageOf([
        { id: 1, name: 'Organization' },
        { id: 4, name: 'Other organization' },
      ])
    );
    mocks.getOrganizationProducts.mockImplementation(
      async ({ path }: { path: { organizationId: number } }) =>
        pageOf(productsByOrganization[path.organizationId] ?? [])
    );
    mocks.patchRepository.mockImplementation(
      async ({ body }: { body: { productId: number } }) => ({
        data: { ...repository, organizationId: 4, productId: body.productId },
      })
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('can move the repository only once an organization and a product are chosen', async () => {
    const { user } = await renderMoveRepository();

    expect(moveButton()).toBeDisabled();
    expect(productList()).toBeDisabled();

    await choose(user, organizationList(), 'Other organization');
    expect(moveButton()).toBeDisabled();

    await choose(user, productList(), 'Other product');
    expect(productList()).toHaveTextContent('Other product');
    expect(moveButton()).toBeEnabled();

    // The products of another organization are different ones.
    await choose(user, organizationList(), 'Organization');
    expect(productList()).toHaveTextContent('Select product');
    expect(productList()).toBeEnabled();
    expect(moveButton()).toBeDisabled();
  });

  it('moves the repository and refreshes the repositories of both products', async () => {
    const { router, queryClient, user } = await renderMoveRepository();
    seed(queryClient, repositoriesOf(2), repositoriesOf(5), userInfoKey);

    await choose(user, organizationList(), 'Other organization');
    await choose(user, productList(), 'Other product');
    await user.click(moveButton());
    const dialog = await screen.findByRole('alertdialog');
    await user.type(within(dialog).getByRole('textbox'), repository.url);
    await user.click(within(dialog).getByRole('button', { name: 'Move' }));

    await waitFor(() =>
      expect(router.state.location.pathname).toBe(
        '/organizations/4/products/5/repositories/3'
      )
    );
    expect(mocks.patchRepository).toHaveBeenCalledWith(
      expect.objectContaining({
        path: { repositoryId: 3 },
        body: { productId: 5 },
      })
    );
    expect(isInvalidated(queryClient, repositoriesOf(2))).toBe(true);
    expect(isInvalidated(queryClient, repositoriesOf(5))).toBe(true);
    expect(isInvalidated(queryClient, userInfoKey)).toBe(true);
  });
});
