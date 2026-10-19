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
import { Outlet } from '@tanstack/react-router';
import { screen, waitFor, within } from '@testing-library/react';
import type { ComponentType } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { Organization, Product, Repository } from '@/api';
import {
  getOrganizationProductsQueryKey,
  getOrganizationQueryKey,
  getOrganizationsQueryKey,
  getProductQueryKey,
} from '@/api/@tanstack/react-query.gen';
import { TooltipProvider } from '@/components/ui/tooltip';
import { Route as CreateOrganizationRoute } from '@/routes/create-organization/index';
import { Route as CreateProductRoute } from '@/routes/organizations/$orgId/create-product/index';
import { Route as RepositorySettingsRoute } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/settings/index';
import { Route as RepositoryLayoutRoute } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/route';
import { Route as ProductLayoutRoute } from '@/routes/organizations/$orgId/products/$productId/route';
import { Route as ProductSettingsRoute } from '@/routes/organizations/$orgId/products/$productId/settings/index';
import { Route as OrganizationLayoutRoute } from '@/routes/organizations/$orgId/route';
import { Route as OrganizationSettingsRoute } from '@/routes/organizations/$orgId/settings/index';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';
import type { TestRouteDefinition } from '../fixtures/router-harness';

const mocks = vi.hoisted(() => ({
  getOrganization: vi.fn(),
  postOrganization: vi.fn(),
  patchOrganization: vi.fn(),
  deleteOrganization: vi.fn(),
  getProduct: vi.fn(),
  postProduct: vi.fn(),
  patchProduct: vi.fn(),
  deleteProduct: vi.fn(),
  getRepository: vi.fn(),
  patchRepository: vi.fn(),
  getSuperuser: vi.fn(),
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  ...mocks,
}));

vi.mock('@/lib/toast', () => ({
  toast: { info: vi.fn() },
  toastError: vi.fn(),
}));

// Creating a product refreshes the roles of the user, which needs a signed-in user.
vi.mock('@/hooks/use-user', () => ({
  useUser: () => ({ refreshUser: vi.fn() }),
}));

// The repository settings page deletes a repository through this module, which these tests do
// not exercise.
vi.mock('@/lib/repository-deletion', () => ({
  deleteRepositoryWithContents: vi.fn(),
}));

const organization: Organization = {
  id: 1,
  name: 'Organization',
  description: 'The organization',
};

const product: Product = {
  id: 2,
  organizationId: 1,
  name: 'Product',
  description: 'The product',
};

const repository: Repository = {
  id: 3,
  organizationId: 1,
  productId: 2,
  type: 'GIT',
  url: 'https://example.com/repository.git',
  name: 'Repository',
};

// The page a mutation navigates to, which the tests only need to exist.
const Destination = () => <div>Destination</div>;

const withTooltips = (Page: ComponentType) => () => (
  <TooltipProvider>
    <Page />
  </TooltipProvider>
);

// The layout route of an entity, with the loader of the application that writes the breadcrumb,
// and the given pages under it. Its own page is the one a rename navigates to.
const layoutRoute = (
  path: string,
  loader: TestRouteDefinition['loader'],
  children: TestRouteDefinition[]
): TestRouteDefinition => ({
  path,
  loader,
  component: Outlet,
  children: [...children, { path: '/', component: Destination }],
});

const levels = [
  {
    level: 'organization',
    path: '/organizations/1/settings',
    routes: [
      layoutRoute(
        '/organizations/$orgId',
        OrganizationLayoutRoute.options.loader,
        [
          {
            path: 'settings/',
            component: withTooltips(
              OrganizationSettingsRoute.options.component!
            ),
          },
        ]
      ),
      { path: '/organizations/', component: Destination },
    ],
    breadcrumb: 'organization',
    entity: organization,
    entityKey: getOrganizationQueryKey({ path: { organizationId: 1 } }),
    getEntity: mocks.getOrganization,
    patchEntity: mocks.patchOrganization,
    deleteEntity: mocks.deleteOrganization,
    requestPath: { organizationId: 1 },
    entityPath: '/organizations/1',
    parentPath: '/organizations',
    listKey: getOrganizationsQueryKey(),
  },
  {
    level: 'product',
    path: '/organizations/1/products/2/settings',
    routes: [
      layoutRoute(
        '/organizations/$orgId/products/$productId',
        ProductLayoutRoute.options.loader,
        [
          {
            path: 'settings/',
            component: withTooltips(ProductSettingsRoute.options.component!),
          },
        ]
      ),
      { path: '/organizations/$orgId', component: Destination },
    ],
    breadcrumb: 'product',
    entity: product,
    entityKey: getProductQueryKey({ path: { productId: 2 } }),
    getEntity: mocks.getProduct,
    patchEntity: mocks.patchProduct,
    deleteEntity: mocks.deleteProduct,
    requestPath: { productId: 2 },
    entityPath: '/organizations/1/products/2',
    parentPath: '/organizations/1',
    listKey: getOrganizationProductsQueryKey({ path: { organizationId: 1 } }),
  },
] as const;

// Render the routes with the given pages. The layout loaders write the breadcrumbs into the
// router context, as in the application, where the tests read them.
const renderRoutes = ({
  path,
  routes,
}: {
  path: string;
  routes: ReadonlyArray<TestRouteDefinition>;
}) => {
  const breadcrumbs: Record<string, string | undefined> = {};
  const result = renderInteractiveWithRouter(null, {
    path,
    routes,
    withQueryClient: true,
    context: { breadcrumbs, permissions: {} },
  });

  return { ...result, queryClient: result.queryClient!, breadcrumbs };
};

// Render a page that is not under a layout route.
const renderPage = (Page: ComponentType, path: string, pagePath: string) =>
  renderRoutes({
    path,
    routes: [
      { path: pagePath, component: withTooltips(Page) },
      { path: '/organizations/$orgId', component: Destination },
      {
        path: '/organizations/$orgId/products/$productId',
        component: Destination,
      },
    ],
  });

// Put a list in the cache, so that there is a query to invalidate.
const seed = (queryClient: QueryClient, key: QueryKey) =>
  queryClient.setQueryData(key, { seeded: true });

const isInvalidated = (queryClient: QueryClient, key: QueryKey) =>
  queryClient.getQueryState(key)?.isInvalidated;

const rename = async (
  user: ReturnType<typeof renderRoutes>['user'],
  name: string
) => {
  const nameInput = await screen.findByLabelText('Name');
  await user.clear(nameInput);
  if (name) await user.type(nameInput, name);
  await user.click(screen.getByRole('button', { name: 'Submit' }));
};

describe('hierarchy entity pages', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    mocks.getOrganization.mockResolvedValue({ data: organization });
    mocks.getProduct.mockResolvedValue({ data: product });
    mocks.getRepository.mockResolvedValue({ data: repository });
    mocks.deleteOrganization.mockResolvedValue({ data: undefined });
    mocks.deleteProduct.mockResolvedValue({ data: undefined });
    // The layout loaders read the permissions of the user, which a superuser has without asking
    // for them.
    mocks.getSuperuser.mockResolvedValue({ data: true });

    // A renamed entity is what the server answers with from then on.
    for (const [patch, get] of [
      [mocks.patchOrganization, mocks.getOrganization],
      [mocks.patchProduct, mocks.getProduct],
      [mocks.patchRepository, mocks.getRepository],
    ] as const) {
      patch.mockImplementation(async ({ body }: { body: { name: string } }) => {
        const { data } = await get();
        const renamed = { ...data, ...body };
        get.mockResolvedValue({ data: renamed });
        return { data: renamed };
      });
    }
  });

  describe.each(levels)('$level settings', (level) => {
    it('renames the entity and shows the new name in the breadcrumb', async () => {
      const { router, breadcrumbs, user } = renderRoutes(level);
      await waitFor(() =>
        expect(breadcrumbs[level.breadcrumb]).toBe(level.entity.name)
      );

      await rename(user, 'Renamed');

      await waitFor(() =>
        expect(router.state.location.pathname).toBe(level.entityPath)
      );
      expect(level.patchEntity).toHaveBeenCalledWith(
        expect.objectContaining({
          path: level.requestPath,
          body: { name: 'Renamed', description: level.entity.description },
        })
      );
      expect(breadcrumbs[level.breadcrumb]).toBe('Renamed');
    });

    it('does not save an empty name', async () => {
      const { user } = renderRoutes(level);

      await rename(user, '');

      await waitFor(() =>
        expect(screen.getByLabelText('Name')).toHaveAttribute(
          'aria-invalid',
          'true'
        )
      );
      expect(level.patchEntity).not.toHaveBeenCalled();
    });

    it('deletes the entity and refreshes the list it was shown in', async () => {
      const { router, queryClient, user } = renderRoutes(level);
      seed(queryClient, level.listKey);

      await user.click(
        await screen.findByRole('button', { name: `Delete ${level.level}` })
      );
      const dialog = await screen.findByRole('alertdialog');
      await user.type(within(dialog).getByRole('textbox'), level.entity.name);
      await user.click(within(dialog).getByRole('button', { name: 'Delete' }));

      await waitFor(() =>
        expect(router.state.location.pathname).toBe(level.parentPath)
      );
      expect(level.deleteEntity).toHaveBeenCalledWith(
        expect.objectContaining({ path: level.requestPath })
      );
      expect(isInvalidated(queryClient, level.listKey)).toBe(true);
      expect(isInvalidated(queryClient, level.entityKey)).toBe(true);
      expect(level.getEntity).toHaveBeenCalledOnce();
    });
  });

  describe('repository settings', () => {
    it('renames the repository and shows the new name in the breadcrumb', async () => {
      const { router, breadcrumbs, user } = renderRoutes({
        path: '/organizations/1/products/2/repositories/3/settings',
        routes: [
          layoutRoute(
            '/organizations/$orgId/products/$productId/repositories/$repoId',
            RepositoryLayoutRoute.options.loader,
            [
              {
                id: '_repo-layout',
                children: [
                  {
                    path: 'settings/',
                    component: withTooltips(
                      RepositorySettingsRoute.options.component!
                    ),
                  },
                ],
              },
            ]
          ),
        ],
      });
      await waitFor(() => expect(breadcrumbs.repo).toBe(repository.name));

      await rename(user, 'Renamed');

      await waitFor(() =>
        expect(router.state.location.pathname).toBe(
          '/organizations/1/products/2/repositories/3'
        )
      );
      expect(mocks.patchRepository).toHaveBeenCalledWith(
        expect.objectContaining({
          path: { repositoryId: 3 },
          body: expect.objectContaining({ name: 'Renamed' }),
        })
      );
      expect(breadcrumbs.repo).toBe('Renamed');
    });
  });

  describe('creating', () => {
    it('creates an organization and refreshes the list of organizations', async () => {
      mocks.postOrganization.mockResolvedValue({
        data: { id: 7, name: 'New' },
      });
      const { router, queryClient, user } = renderPage(
        CreateOrganizationRoute.options.component!,
        '/create-organization',
        '/create-organization/'
      );
      seed(queryClient, getOrganizationsQueryKey());

      await user.type(await screen.findByLabelText('Name'), 'New');
      await user.click(screen.getByRole('button', { name: 'Add' }));

      await waitFor(() =>
        expect(router.state.location.pathname).toBe('/organizations/7')
      );
      expect(mocks.postOrganization).toHaveBeenCalledWith(
        expect.objectContaining({ body: { name: 'New' } })
      );
      expect(isInvalidated(queryClient, getOrganizationsQueryKey())).toBe(true);
    });

    it('creates a product and refreshes the products of its organization', async () => {
      // The server answers with the organization the product belongs to, which is the one
      // whose products are refreshed.
      mocks.postProduct.mockResolvedValue({
        data: { id: 5, organizationId: 7, name: 'New' },
      });
      const productsOf = (organizationId: number) =>
        getOrganizationProductsQueryKey({ path: { organizationId } });
      const { router, queryClient, user } = renderPage(
        CreateProductRoute.options.component!,
        '/organizations/1/create-product',
        '/organizations/$orgId/create-product/'
      );
      seed(queryClient, productsOf(1));
      seed(queryClient, productsOf(7));

      await user.type(await screen.findByLabelText('Name'), 'New');
      await user.click(screen.getByRole('button', { name: 'Add' }));

      await waitFor(() =>
        expect(router.state.location.pathname).toBe(
          '/organizations/1/products/5'
        )
      );
      expect(mocks.postProduct).toHaveBeenCalledWith(
        expect.objectContaining({
          path: { organizationId: 1 },
          body: { name: 'New' },
        })
      );
      expect(isInvalidated(queryClient, productsOf(7))).toBe(true);
      expect(isInvalidated(queryClient, productsOf(1))).toBe(false);
    });
  });
});
