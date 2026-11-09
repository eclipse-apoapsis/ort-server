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

import { Outlet } from '@tanstack/react-router';
import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { UserInfo } from '@/api';
import { Route as CreateOrganizationRoute } from '@/routes/create-organization/index';
import { Route as CreateProductRoute } from '@/routes/organizations/$orgId/create-product/index';
import { Route as CreateRepositoryRoute } from '@/routes/organizations/$orgId/products/$productId/create-repository/index';
import { Route as CreateRunRoute } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/create-run/index';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';
import type { TestRouteDefinition } from '../fixtures/router-harness';

const mocks = vi.hoisted(() => ({
  getSuperuser: vi.fn(),
  getUserInfo: vi.fn(),
  getAvailableRepositorySecrets: vi.fn(),
  getOrganization: vi.fn(),
  getProduct: vi.fn(),
  getRepository: vi.fn(),
  getPluginsForRepository: vi.fn(),
  getDefaultPluginsForRepository: vi.fn(),
  getRepositoryRuns: vi.fn(),
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  ...mocks,
}));

// The pages read the signed-in user, who is no superuser here.
vi.mock('@/hooks/use-user', () => ({
  useUser: () => ({ isSuperuser: false, refreshUser: vi.fn() }),
}));

vi.mock('@/lib/toast', () => ({
  toast: { info: vi.fn() },
  toastError: vi.fn(),
}));

const Forbidden = () => <div>Forbidden</div>;

// Each creation page with the permission the API requires for it, and permissions of the same
// level that do not allow it.
type CreationPage = {
  page: string;
  path: string;
  routes: TestRouteDefinition[];
  allowed: Partial<UserInfo>;
  denied: Partial<UserInfo>;
  heading: string;
};

const pages: CreationPage[] = [
  {
    page: 'create product',
    path: '/organizations/1/create-product',
    routes: [
      {
        path: '/organizations/$orgId/create-product/',
        component: CreateProductRoute.options.component,
      },
    ],
    allowed: { organizationPermissions: ['READ', 'CREATE_PRODUCT'] },
    denied: { organizationPermissions: ['READ', 'WRITE', 'MANAGE_ROLES'] },
    heading: 'Name',
  },
  {
    page: 'create repository',
    path: '/organizations/1/products/2/create-repository',
    routes: [
      {
        path: '/organizations/$orgId/products/$productId/create-repository/',
        component: CreateRepositoryRoute.options.component,
      },
    ],
    allowed: { productPermissions: ['READ', 'CREATE_REPOSITORY'] },
    denied: { productPermissions: ['READ', 'WRITE', 'TRIGGER_ORT_RUN'] },
    heading: 'URL',
  },
  {
    page: 'create run',
    path: '/organizations/1/products/2/repositories/3/create-run',
    routes: [
      {
        path: '/organizations/$orgId/products/$productId/repositories/$repoId',
        component: Outlet,
        children: [
          {
            id: '_repo-layout',
            children: [
              {
                path: 'create-run/',
                // The test router has no dependencies for the loader, which the page reads
                // only to rerun a run.
                loader: (context: object) =>
                  (
                    CreateRunRoute.options.loader as unknown as (
                      context: object
                    ) => unknown
                  )({ ...context, deps: {} }),
                component: CreateRunRoute.options.component,
              },
            ],
          },
        ],
      },
    ],
    allowed: { repositoryPermissions: ['READ', 'TRIGGER_ORT_RUN'] },
    denied: { repositoryPermissions: ['READ', 'READ_ORT_RUNS', 'WRITE'] },
    heading: 'Revision',
  },
];

const renderPage = (page: CreationPage, permissions: Partial<UserInfo>) => {
  mocks.getUserInfo.mockResolvedValue({
    data: {
      fullName: 'Me',
      username: 'me',
      isSuperuser: false,
      ...permissions,
    },
  });

  return renderInteractiveWithRouter(null, {
    path: page.path,
    routes: [...page.routes, { path: '/403', component: Forbidden }],
    withQueryClient: true,
    context: { permissions: {} },
  });
};

describe('creation permission access', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    // The create run page shows its form once it knows the repository and its plugins.
    mocks.getAvailableRepositorySecrets.mockResolvedValue({ data: [] });
    mocks.getOrganization.mockResolvedValue({ data: { id: 1, name: 'Org' } });
    mocks.getProduct.mockResolvedValue({
      data: { id: 2, organizationId: 1, name: 'Product' },
    });
    mocks.getRepository.mockResolvedValue({
      data: {
        id: 3,
        organizationId: 1,
        productId: 2,
        type: 'GIT',
        url: 'https://example.com/repository.git',
      },
    });
    mocks.getPluginsForRepository.mockResolvedValue({ data: [] });
    mocks.getDefaultPluginsForRepository.mockResolvedValue({ data: [] });
    mocks.getRepositoryRuns.mockResolvedValue({
      data: { data: [], pagination: { limit: 1, offset: 0, totalCount: 0 } },
    });
  });

  it.each(pages)(
    'denies the $page page to users without the permission to create',
    async (page) => {
      renderPage(page, page.denied);

      expect(await screen.findByText('Forbidden')).toBeInTheDocument();
      expect(screen.queryByLabelText(page.heading)).not.toBeInTheDocument();
    }
  );

  it.each(pages)(
    'opens the $page page for users with the permission to create',
    async (page) => {
      renderPage(page, page.allowed);

      expect(await screen.findByLabelText(page.heading)).toBeInTheDocument();
      expect(screen.queryByText('Forbidden')).not.toBeInTheDocument();
    }
  );

  it.each([false, true])(
    'opens the create organization page only for superusers: %s',
    async (isSuperuser) => {
      mocks.getSuperuser.mockResolvedValue({ data: isSuperuser });
      renderInteractiveWithRouter(null, {
        path: '/create-organization',
        routes: [
          {
            path: '/create-organization/',
            component: CreateOrganizationRoute.options.component,
          },
          { path: '/403', component: Forbidden },
        ],
        withQueryClient: true,
      });

      if (isSuperuser) {
        expect(await screen.findByLabelText('Name')).toBeInTheDocument();
      } else {
        expect(await screen.findByText('Forbidden')).toBeInTheDocument();
        expect(screen.queryByLabelText('Name')).not.toBeInTheDocument();
      }
    }
  );
});
