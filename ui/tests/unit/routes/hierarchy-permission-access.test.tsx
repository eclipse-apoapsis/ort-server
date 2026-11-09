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
import { Route as OrganizationServiceEditRoute } from '@/routes/organizations/$orgId/infrastructure-services/$serviceName/edit/index';
import { Route as OrganizationServiceCreateRoute } from '@/routes/organizations/$orgId/infrastructure-services/create/index';
import { Route as OrganizationServicesRoute } from '@/routes/organizations/$orgId/infrastructure-services/route';
import { Route as ProductServiceEditRoute } from '@/routes/organizations/$orgId/products/$productId/infrastructure-services/$serviceName/edit/index';
import { Route as ProductServiceCreateRoute } from '@/routes/organizations/$orgId/products/$productId/infrastructure-services/create/index';
import { Route as ProductServicesRoute } from '@/routes/organizations/$orgId/products/$productId/infrastructure-services/route';
import { Route as RepositoryServiceEditRoute } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/infrastructure-services/$serviceName/edit/index';
import { Route as RepositoryServiceCreateRoute } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/infrastructure-services/create/index';
import { Route as RepositoryServicesRoute } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/infrastructure-services/route';
import { Route as RepositoryLayoutRoute } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/route';
import { Route as RepositorySecretEditRoute } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/secrets/$secretName/edit/index';
import { Route as RepositorySecretCreateRoute } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/secrets/create-secret/index';
import { Route as RepositorySecretsRoute } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/secrets/route';
import { Route as RepositorySettingsRoute } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/settings/index';
import { Route as RepositoryUsersRoute } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/users/route';
import { Route as RepositoryRoute } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/route';
import { Route as ProductRoute } from '@/routes/organizations/$orgId/products/$productId/route';
import { Route as ProductSecretEditRoute } from '@/routes/organizations/$orgId/products/$productId/secrets/$secretName/edit/index';
import { Route as ProductSecretCreateRoute } from '@/routes/organizations/$orgId/products/$productId/secrets/create-secret/index';
import { Route as ProductSecretsRoute } from '@/routes/organizations/$orgId/products/$productId/secrets/route';
import { Route as ProductSettingsRoute } from '@/routes/organizations/$orgId/products/$productId/settings/index';
import { Route as ProductUsersRoute } from '@/routes/organizations/$orgId/products/$productId/users/route';
import { Route as OrganizationRoute } from '@/routes/organizations/$orgId/route';
import { Route as OrganizationSecretEditRoute } from '@/routes/organizations/$orgId/secrets/$secretName/edit/index';
import { Route as OrganizationSecretCreateRoute } from '@/routes/organizations/$orgId/secrets/create-secret/index';
import { Route as OrganizationSecretsRoute } from '@/routes/organizations/$orgId/secrets/route';
import { Route as OrganizationSettingsRoute } from '@/routes/organizations/$orgId/settings/index';
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
  getOrganizationSecret: vi.fn(),
  getProductSecret: vi.fn(),
  getRepositorySecret: vi.fn(),
  getOrganizationInfrastructureService: vi.fn(),
  getProductInfrastructureService: vi.fn(),
  getRepositoryInfrastructureService: vi.fn(),
  getOrganizationSecrets: vi.fn(),
  getProductSecrets: vi.fn(),
  getRepositorySecrets: vi.fn(),
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  ...mocks,
}));

vi.mock('@/lib/toast', () => ({
  toast: { info: vi.fn() },
  toastError: vi.fn(),
}));

// The pages under the guarded routes, which the tests only need to recognize.
const ListPage = () => <div>List page</div>;
const OtherPage = () => <div>Other page</div>;
const Forbidden = () => <div>Forbidden</div>;

type GuardRoute = { options: { component?: TestRouteDefinition['component'] } };

// The pages under the layout of a level: the users and secrets lists behind their guards, without
// the loaders that fetch the lists, the secret create and edit pages, and another page, which
// shows the menu of the layout without passing a guard.
const subpages = (routes: {
  users: GuardRoute;
  secrets: GuardRoute;
  createSecret: GuardRoute;
  editSecret: GuardRoute;
  services: GuardRoute;
  createService: GuardRoute;
  editService: GuardRoute;
  settings: GuardRoute;
}): TestRouteDefinition[] => [
  {
    path: 'users',
    component: routes.users.options.component,
    children: [{ path: '/', component: ListPage }],
  },
  {
    path: 'secrets',
    component: routes.secrets.options.component,
    children: [
      { path: '/', component: ListPage },
      {
        path: 'create-secret/',
        component: routes.createSecret.options.component,
      },
      {
        path: '$secretName/edit/',
        component: routes.editSecret.options.component,
      },
    ],
  },
  {
    path: 'infrastructure-services',
    component: routes.services.options.component,
    children: [
      { path: '/', component: ListPage },
      { path: 'create/', component: routes.createService.options.component },
      {
        path: '$serviceName/edit/',
        component: routes.editService.options.component,
      },
    ],
  },
  { path: 'settings/', component: routes.settings.options.component },
  { path: 'other', component: OtherPage },
];

const levels = [
  {
    level: 'organization',
    path: '/organizations/1',
    routes: [
      {
        path: '/organizations/$orgId',
        loader: OrganizationRoute.options.loader,
        component: OrganizationRoute.options.component,
        children: subpages({
          users: OrganizationUsersRoute,
          secrets: OrganizationSecretsRoute,
          createSecret: OrganizationSecretCreateRoute,
          editSecret: OrganizationSecretEditRoute,
          services: OrganizationServicesRoute,
          createService: OrganizationServiceCreateRoute,
          editService: OrganizationServiceEditRoute,
          settings: OrganizationSettingsRoute,
        }),
      },
    ],
    getSecret: mocks.getOrganizationSecret,
    getService: mocks.getOrganizationInfrastructureService,
    permissionsOf: (permissions: LevelPermission[]) => ({
      organizationPermissions: permissions,
    }),
  },
  {
    level: 'product',
    path: '/organizations/1/products/2',
    routes: [
      {
        path: '/organizations/$orgId/products/$productId',
        loader: ProductRoute.options.loader,
        component: ProductRoute.options.component,
        children: subpages({
          users: ProductUsersRoute,
          secrets: ProductSecretsRoute,
          createSecret: ProductSecretCreateRoute,
          editSecret: ProductSecretEditRoute,
          services: ProductServicesRoute,
          createService: ProductServiceCreateRoute,
          editService: ProductServiceEditRoute,
          settings: ProductSettingsRoute,
        }),
      },
    ],
    getSecret: mocks.getProductSecret,
    getService: mocks.getProductInfrastructureService,
    permissionsOf: (permissions: LevelPermission[]) => ({
      productPermissions: permissions,
    }),
  },
  {
    level: 'repository',
    path: '/organizations/1/products/2/repositories/3',
    routes: [
      {
        path: '/organizations/$orgId/products/$productId/repositories/$repoId',
        loader: RepositoryRoute.options.loader,
        component: RepositoryRoute.options.component,
        children: [
          {
            id: '_repo-layout',
            component: RepositoryLayoutRoute.options.component,
            children: subpages({
              users: RepositoryUsersRoute,
              secrets: RepositorySecretsRoute,
              createSecret: RepositorySecretCreateRoute,
              editSecret: RepositorySecretEditRoute,
              services: RepositoryServicesRoute,
              createService: RepositoryServiceCreateRoute,
              editService: RepositoryServiceEditRoute,
              settings: RepositorySettingsRoute,
            }),
          },
        ],
      },
    ],
    getSecret: mocks.getRepositorySecret,
    getService: mocks.getRepositoryInfrastructureService,
    permissionsOf: (permissions: LevelPermission[]) => ({
      repositoryPermissions: permissions,
    }),
  },
] as const;

const lists = [
  { menuEntry: 'Users', subpage: 'users' },
  { menuEntry: 'Secrets', subpage: 'secrets' },
  { menuEntry: 'Infrastructure Services', subpage: 'infrastructure-services' },
] as const;

const levelsAndLists = levels.flatMap((level) =>
  lists.map((list) => ({ ...level, ...list }))
);

const renderLevel = (
  level: (typeof levels)[number],
  permissions: LevelPermission[],
  subpage: string
) => {
  const userInfo: UserInfo = {
    fullName: 'Me',
    username: 'me',
    isSuperuser: false,
    ...level.permissionsOf(permissions),
  };
  mocks.getUserInfo.mockResolvedValue({ data: userInfo });

  return renderInteractiveWithRouter(null, {
    path: `${level.path}/${subpage}`,
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

    for (const level of levels) {
      level.getSecret.mockResolvedValue({
        data: { name: 'token', description: 'A token' },
      });
      level.getService.mockResolvedValue({
        data: {
          name: 'repo',
          url: 'https://repo.example.com',
          usernameSecretRef: 'user',
          passwordSecretRef: 'password',
        },
      });
    }
    // The service forms offer the secrets of the hierarchy to choose from.
    for (const secrets of [
      mocks.getOrganizationSecrets,
      mocks.getProductSecrets,
      mocks.getRepositorySecrets,
    ]) {
      secrets.mockResolvedValue({
        data: { data: [], pagination: { limit: 10, offset: 0, totalCount: 0 } },
      });
    }
  });

  it.each(levelsAndLists)(
    'shows the $subpage of the $level level to users with read permission',
    async (level) => {
      renderLevel(level, ['READ'], level.subpage);

      expect(await screen.findByText('List page')).toBeInTheDocument();
      expect(
        screen.getByRole('link', { name: level.menuEntry })
      ).toBeInTheDocument();
    }
  );

  it.each(levelsAndLists)(
    'denies the $subpage of the $level level to users without read permission',
    async (level) => {
      renderLevel(level, [], level.subpage);

      expect(await screen.findByText('Forbidden')).toBeInTheDocument();
      await waitFor(() =>
        expect(screen.queryByText('List page')).not.toBeInTheDocument()
      );
    }
  );

  it.each(levelsAndLists)(
    'shows no $subpage menu entry on the $level level without read permission',
    async (level) => {
      renderLevel(level, [], 'other');

      expect(await screen.findByText('Other page')).toBeInTheDocument();
      expect(
        screen.getByRole('link', { name: 'Packages' })
      ).toBeInTheDocument();
      expect(
        screen.queryByRole('link', { name: level.menuEntry })
      ).not.toBeInTheDocument();
    }
  );

  describe.each([
    { page: 'create', subpage: 'secrets/create-secret' },
    { page: 'edit', subpage: 'secrets/token/edit' },
  ])('the secret $page page', ({ subpage }) => {
    it.each(levels)(
      'is denied on the $level level to users who may only read secrets',
      async (level) => {
        renderLevel(level, ['READ'], subpage);

        expect(await screen.findByText('Forbidden')).toBeInTheDocument();
      }
    );

    it.each(levels)(
      'opens on the $level level for users who may write secrets',
      async (level) => {
        renderLevel(level, ['READ', 'WRITE_SECRETS'], subpage);

        expect(await screen.findByLabelText('Value')).toBeInTheDocument();
      }
    );
  });

  describe.each([
    { page: 'create', subpage: 'infrastructure-services/create' },
    { page: 'edit', subpage: 'infrastructure-services/repo/edit' },
  ])('the infrastructure service $page page', ({ subpage }) => {
    it.each(levels)(
      'is denied on the $level level to users who may only read and write secrets',
      async (level) => {
        renderLevel(level, ['READ', 'WRITE_SECRETS'], subpage);

        expect(await screen.findByText('Forbidden')).toBeInTheDocument();
      }
    );

    it.each(levels)(
      'opens on the $level level for users who may write',
      async (level) => {
        renderLevel(level, ['READ', 'WRITE'], subpage);

        expect(await screen.findByLabelText('URL Prefix')).toBeInTheDocument();
      }
    );
  });

  describe('the settings page', () => {
    it.each(levels)(
      'is denied on the $level level to users who may only read',
      async (level) => {
        renderLevel(level, ['READ'], 'settings');

        expect(await screen.findByText('Forbidden')).toBeInTheDocument();
      }
    );

    it.each(levels)(
      'opens on the $level level for users who may write, with deletion disabled',
      async (level) => {
        renderLevel(level, ['READ', 'WRITE'], 'settings');

        expect(
          await screen.findByRole('button', { name: `Delete ${level.level}` })
        ).toBeDisabled();
      }
    );

    it.each(levels)(
      'enables deletion on the $level level for users who may delete',
      async (level) => {
        renderLevel(level, ['READ', 'WRITE', 'DELETE'], 'settings');

        await waitFor(() =>
          expect(
            screen.getByRole('button', { name: `Delete ${level.level}` })
          ).toBeEnabled()
        );
      }
    );

    it('disables moving the repository for users who may not delete it', async () => {
      renderLevel(levels[2], ['READ', 'WRITE'], 'settings');

      expect(
        await screen.findByRole('button', { name: 'Move repository' })
      ).toBeDisabled();
      expect(
        screen.getByText('Select organization').closest('button')
      ).toBeDisabled();
    });
  });
});
