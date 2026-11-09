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

import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  withOrganizationPermission,
  withProductPermission,
  withRepositoryPermission,
} from '@/components/authorization';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';

const mocks = vi.hoisted(() => ({ getUserInfo: vi.fn() }));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  ...mocks,
}));

const Page = () => <div>Page</div>;
const Forbidden = () => <div>Forbidden</div>;

const guards = [
  {
    level: 'organization',
    guard: withOrganizationPermission('READ', Page),
    query: { organizationId: 1 },
    permissionsOf: (permissions: 'READ'[]) => ({
      organizationPermissions: permissions,
    }),
  },
  {
    level: 'product',
    guard: withProductPermission('READ', Page),
    query: { productId: 2 },
    permissionsOf: (permissions: 'READ'[]) => ({
      productPermissions: permissions,
    }),
  },
  {
    level: 'repository',
    guard: withRepositoryPermission('READ', Page),
    query: { repositoryId: 3 },
    permissionsOf: (permissions: 'READ'[]) => ({
      repositoryPermissions: permissions,
    }),
  },
];

const renderGuard = (guard: (typeof guards)[number]['guard']) =>
  renderInteractiveWithRouter(null, {
    path: '/organizations/1/products/2/repositories/3/page',
    routes: [
      {
        path: '/organizations/$orgId/products/$productId/repositories/$repoId/page',
        component: guard,
      },
      { path: '/403', component: Forbidden },
    ],
    withQueryClient: true,
  });

describe('permission route guards', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it.each(guards)(
    'shows the page to users with the permission on the $level of the route',
    async ({ guard, query, permissionsOf }) => {
      mocks.getUserInfo.mockResolvedValue({
        data: {
          fullName: 'Me',
          username: 'me',
          isSuperuser: false,
          ...permissionsOf(['READ']),
        },
      });
      renderGuard(guard);

      expect(await screen.findByText('Page')).toBeInTheDocument();
      expect(mocks.getUserInfo).toHaveBeenCalledWith(
        expect.objectContaining({ query })
      );
    }
  );

  it.each(guards)(
    'denies the page to users without the permission on the $level of the route',
    async ({ guard, permissionsOf }) => {
      mocks.getUserInfo.mockResolvedValue({
        data: {
          fullName: 'Me',
          username: 'me',
          isSuperuser: false,
          ...permissionsOf([]),
        },
      });
      renderGuard(guard);

      expect(await screen.findByText('Forbidden')).toBeInTheDocument();
      expect(screen.queryByText('Page')).not.toBeInTheDocument();
    }
  );
});
