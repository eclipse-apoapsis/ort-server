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

import type {
  OrganizationPermission,
  ProductPermission,
  RepositoryPermission,
  UserInfo,
} from '@/api';
import { Route as RepositoryLayoutRoute } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/route';
import { Route as RepositoryUsersRoute } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/users/route';
import { Route as RepositoryRoute } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/route';
import { Route as ProductRoute } from '@/routes/organizations/$orgId/products/$productId/route';
import { Route as ProductUsersRoute } from '@/routes/organizations/$orgId/products/$productId/users/route';
import { Route as OrganizationRoute } from '@/routes/organizations/$orgId/route';
import { Route as OrganizationUsersRoute } from '@/routes/organizations/$orgId/users/route';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';
import type { TestRouteDefinition } from '../fixtures/router-harness';

// A permission that exists on every level of the hierarchy, such as READ.
type LevelPermission = OrganizationPermission &
  ProductPermission &
  RepositoryPermission;

const mocks = vi.hoisted(() => ({
  getOrganization: vi.fn(),
  getProduct: vi.fn(),
  getRepository: vi.fn(),
  getSuperuser: vi.fn(),
  getUserInfo: vi.fn(),
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  ...mocks,
}));

vi.mock('@/lib/toast', () => ({
  toast: { info: vi.fn() },
  toastError: vi.fn(),
}));

// The page under a guarded route, which the tests only need to recognize.
const UsersList = () => <div>Users list</div>;
const OtherPage = () => <div>Other page</div>;
const Forbidden = () => <div>Forbidden</div>;

// The guarded users route with a page under it, without the loader that fetches the users, and
// another page under the same layout, which shows its menu without passing the guard.
const usersRoute = (
  path: string,
  route: typeof OrganizationUsersRoute | typeof ProductUsersRoute
): TestRouteDefinition => ({
  path,
  component: route.options.component,
  children: [{ path: '/', component: UsersList }],
});

const otherRoute: TestRouteDefinition = { path: 'other', component: OtherPage };

const levels = [
  {
    level: 'organization',
    path: '/organizations/1/users',
    routes: [
      {
        path: '/organizations/$orgId',
        loader: OrganizationRoute.options.loader,
        component: OrganizationRoute.options.component,
        children: [usersRoute('users', OrganizationUsersRoute), otherRoute],
      },
    ],
    permissionsOf: (permissions: LevelPermission[]) => ({
      organizationPermissions: permissions,
    }),
  },
  {
    level: 'product',
    path: '/organizations/1/products/2/users',
    routes: [
      {
        path: '/organizations/$orgId/products/$productId',
        loader: ProductRoute.options.loader,
        component: ProductRoute.options.component,
        children: [usersRoute('users', ProductUsersRoute), otherRoute],
      },
    ],
    permissionsOf: (permissions: LevelPermission[]) => ({
      productPermissions: permissions,
    }),
  },
  {
    level: 'repository',
    path: '/organizations/1/products/2/repositories/3/users',
    routes: [
      {
        path: '/organizations/$orgId/products/$productId/repositories/$repoId',
        loader: RepositoryRoute.options.loader,
        component: RepositoryRoute.options.component,
        children: [
          {
            id: '_repo-layout',
            component: RepositoryLayoutRoute.options.component,
            children: [
              {
                path: 'users',
                component: RepositoryUsersRoute.options.component,
                children: [{ path: '/', component: UsersList }],
              },
              otherRoute,
            ],
          },
        ],
      },
    ],
    permissionsOf: (permissions: LevelPermission[]) => ({
      repositoryPermissions: permissions,
    }),
  },
] as const;

const renderLevel = (
  level: (typeof levels)[number],
  permissions: LevelPermission[],
  page = 'users'
) => {
  const userInfo: UserInfo = {
    fullName: 'Me',
    username: 'me',
    isSuperuser: false,
    ...level.permissionsOf(permissions),
  };
  mocks.getUserInfo.mockResolvedValue({ data: userInfo });

  return renderInteractiveWithRouter(null, {
    path: level.path.replace(/users$/, page),
    routes: [...level.routes, { path: '/403', component: Forbidden }],
    withQueryClient: true,
    context: { breadcrumbs: {}, permissions: {} },
  });
};

describe('hierarchy permission access', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    mocks.getOrganization.mockResolvedValue({ data: { id: 1, name: 'Org' } });
    mocks.getProduct.mockResolvedValue({
      data: { id: 2, organizationId: 1, name: 'Product' },
    });
    mocks.getRepository.mockResolvedValue({
      data: { id: 3, organizationId: 1, productId: 2, name: 'Repository' },
    });
    mocks.getSuperuser.mockResolvedValue({ data: false });
  });

  it.each(levels)(
    'shows the users of the $level level to users with read permission',
    async (level) => {
      renderLevel(level, ['READ']);

      expect(await screen.findByText('Users list')).toBeInTheDocument();
      expect(screen.getByRole('link', { name: 'Users' })).toBeInTheDocument();
    }
  );

  it.each(levels)(
    'denies the users of the $level level to users without read permission',
    async (level) => {
      renderLevel(level, []);

      expect(await screen.findByText('Forbidden')).toBeInTheDocument();
      await waitFor(() =>
        expect(screen.queryByText('Users list')).not.toBeInTheDocument()
      );
    }
  );

  it.each(levels)(
    'shows no users menu entry on the $level level without read permission',
    async (level) => {
      renderLevel(level, [], 'other');

      expect(await screen.findByText('Other page')).toBeInTheDocument();
      expect(
        screen.getByRole('link', { name: 'Packages' })
      ).toBeInTheDocument();
      expect(
        screen.queryByRole('link', { name: 'Users' })
      ).not.toBeInTheDocument();
    }
  );
});
