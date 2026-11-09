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
import { screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type {
  OrganizationPermission,
  ProductPermission,
  RepositoryPermission,
  UserInfo,
} from '@/api';
import type { UserWithRole } from '@/helpers/role-helpers';
import { RepositoryUsersTable } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/users/-components/repository-users-table';
import { Route as RepositoryUsersRoute } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/users/index';
import { ProductUsersTable } from '@/routes/organizations/$orgId/products/$productId/users/-components/product-users-table';
import { Route as ProductUsersRoute } from '@/routes/organizations/$orgId/products/$productId/users/index';
import { OrganizationUsersTable } from '@/routes/organizations/$orgId/users/-components/organization-users-table';
import { Route as OrganizationUsersRoute } from '@/routes/organizations/$orgId/users/index';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';
import { getColumnTexts, hierarchyLevels, pageOf } from '../fixtures/table';

// A permission that exists on every level of the hierarchy, such as READ.
type LevelPermission = OrganizationPermission &
  ProductPermission &
  RepositoryPermission;

const mocks = vi.hoisted(() => ({
  getOrganization: vi.fn(),
  getProduct: vi.fn(),
  getRepository: vi.fn(),
  getUserInfo: vi.fn(),
  getOrganizationUsers: vi.fn(),
  putOrganizationRoleToUser: vi.fn(),
  deleteOrganizationRoleFromUser: vi.fn(),
  getProductUsers: vi.fn(),
  putProductRoleToUser: vi.fn(),
  deleteProductRoleFromUser: vi.fn(),
  getRepositoryUsers: vi.fn(),
  putRepositoryRoleToUser: vi.fn(),
  deleteRepositoryRoleFromUser: vi.fn(),
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  ...mocks,
}));

vi.mock('@/hooks/use-user', () => ({
  useUser: () => ({ username: 'me' }),
}));

const [organization, product, repository] = hierarchyLevels('users', {
  layout: true,
});
const levels = [
  {
    ...organization,
    Table: OrganizationUsersTable,
    users: mocks.getOrganizationUsers,
    assign: mocks.putOrganizationRoleToUser,
    remove: mocks.deleteOrganizationRoleFromUser,
  },
  {
    ...product,
    Table: ProductUsersTable,
    users: mocks.getProductUsers,
    assign: mocks.putProductRoleToUser,
    remove: mocks.deleteProductRoleFromUser,
  },
  {
    ...repository,
    Table: RepositoryUsersTable,
    users: mocks.getRepositoryUsers,
    assign: mocks.putRepositoryRoleToUser,
    remove: mocks.deleteRepositoryRoleFromUser,
  },
];

const createUser = (
  username: string,
  role: UserWithRole['role']
): UserWithRole => ({
  user: {
    username,
    firstName: `First ${username}`,
    lastName: `Last ${username}`,
    email: `${username}@example.com`,
  },
  role,
});

const users = [
  createUser('admin', 'ADMIN'),
  createUser('writer', 'WRITER'),
  createUser('me', 'ADMIN'),
];

const renderUsers = (
  level: (typeof levels)[number],
  search = '',
  canManageRoles = true
) =>
  renderInteractiveWithRouter(<level.Table canManageRoles={canManageRoles} />, {
    path: `${level.path}${search}`,
    routes: level.routes,
    withQueryClient: true,
  });

const getRow = (username: string) =>
  screen.getByRole('cell', { name: username }).closest('tr')!;

type User = ReturnType<typeof renderUsers>['user'];

const assignAdminRole = async (user: User, username: string) => {
  await user.click(
    getRow(username).querySelector<HTMLElement>('[aria-haspopup="menu"]')!
  );
  await user.click(
    await screen.findByRole('menuitem', { name: 'Assign ADMIN role' })
  );
};

// Opens the removal dialog of the user and returns its Delete button.
const openRemoval = async (
  user: User,
  username: string,
  level: (typeof levels)[number]
) => {
  await user.click(
    within(getRow(username)).getByRole('button', {
      name: `Remove user from this ${level.level}`,
    })
  );

  return within(await screen.findByRole('alertdialog')).getByRole('button', {
    name: 'Delete',
  });
};

describe('users tables', () => {
  const organizationLevel = levels[0]!;

  beforeEach(() => {
    vi.clearAllMocks();

    for (const level of levels) {
      level.users.mockResolvedValue(pageOf(users));
      level.assign.mockResolvedValue({ data: undefined });
      level.remove.mockResolvedValue({ data: undefined });
    }
  });

  it('lists a page of the users and their roles', async () => {
    const { user } = renderUsers(organizationLevel, '?page=2&pageSize=5');

    expect(await screen.findByText('writer')).toBeVisible();
    expect(getColumnTexts('Username')).toEqual(['admin', 'writer', 'me']);
    expect(organizationLevel.users).toHaveBeenCalledWith(
      expect.objectContaining({
        query: { limit: 5, offset: 5, sort: 'username' },
      })
    );

    // The role column comes before the actions, so its icon is the first in the row.
    await user.hover(getRow('writer').querySelector('svg')!);

    expect(
      await screen.findByRole('tooltip', { name: 'WRITER' })
    ).toBeInTheDocument();
  });

  it('refreshes the users after assigning a role', async () => {
    const { user } = renderUsers(organizationLevel);

    await screen.findByText('writer');
    await assignAdminRole(user, 'writer');

    await waitFor(() =>
      expect(organizationLevel.users).toHaveBeenCalledTimes(2)
    );
  });

  it('removes the role of a user after confirming the username, then refreshes the users', async () => {
    const { user } = renderUsers(organizationLevel);

    await screen.findByText('writer');
    const deleteButton = await openRemoval(user, 'writer', organizationLevel);
    expect(deleteButton).toBeDisabled();

    await user.type(screen.getByRole('textbox'), 'writer');
    expect(deleteButton).toBeEnabled();
    await user.click(deleteButton);

    await waitFor(() =>
      expect(organizationLevel.users).toHaveBeenCalledTimes(2)
    );
    expect(organizationLevel.remove).toHaveBeenCalledOnce();
    expect(organizationLevel.remove).toHaveBeenCalledWith(
      expect.objectContaining({
        path: { ...organizationLevel.hierarchyId, role: 'WRITER' },
      })
    );
  });

  it('does not let the signed-in user change their own roles', async () => {
    const { user } = renderUsers(organizationLevel);

    await screen.findByText('me');
    const buttons = within(getRow('me')).getAllByRole('button');

    expect(buttons).toHaveLength(1);
    expect(buttons[0]).toBeDisabled();

    await user.hover(buttons[0]!.parentElement!);

    expect(
      await screen.findByRole('tooltip', {
        name: 'You cannot remove yourself from this organization.',
      })
    ).toBeInTheDocument();
  });

  it.each(levels)(
    'disables the role changes and removals on the $level level without permission to manage roles',
    async (level) => {
      renderUsers(level, '', false);

      await screen.findByText('writer');

      for (const username of ['admin', 'writer', 'me']) {
        const buttons = within(getRow(username)).getAllByRole('button');

        expect(buttons.length).toBeGreaterThan(0);
        for (const button of buttons) expect(button).toBeDisabled();
      }
    }
  );

  it.each(levels)('calls the endpoints of the $level level', async (level) => {
    const { user } = renderUsers(level);

    await screen.findByText('writer');
    expect(level.users).toHaveBeenCalledWith(
      expect.objectContaining({ path: level.hierarchyId })
    );

    await assignAdminRole(user, 'writer');

    await waitFor(() =>
      expect(level.assign).toHaveBeenCalledWith(
        expect.objectContaining({
          path: { ...level.hierarchyId, role: 'ADMIN' },
          body: { username: 'writer' },
        })
      )
    );

    const deleteButton = await openRemoval(user, 'writer', level);
    await user.type(screen.getByRole('textbox'), 'writer');
    await user.click(deleteButton);

    await waitFor(() => expect(level.remove).toHaveBeenCalledOnce());
    expect(level.remove).toHaveBeenCalledWith(
      expect.objectContaining({
        path: { ...level.hierarchyId, role: 'WRITER' },
        query: { username: 'writer' },
      })
    );
  });
});

// The users page of each level, which has the form for adding a user above the table. The page
// is rendered as the index route under the users route, whose parameters the table reads.
const pages = [
  {
    ...levels[0]!,
    page: OrganizationUsersRoute,
    entity: mocks.getOrganization,
    routes: [
      {
        path: '/organizations/$orgId/users',
        component: Outlet,
        children: [
          { path: '/', component: OrganizationUsersRoute.options.component },
        ],
      },
    ],
    permissionsOf: (permissions: LevelPermission[]) => ({
      organizationPermissions: permissions,
    }),
  },
  {
    ...levels[1]!,
    page: ProductUsersRoute,
    entity: mocks.getProduct,
    routes: [
      {
        path: '/organizations/$orgId/products/$productId/users',
        component: Outlet,
        children: [
          { path: '/', component: ProductUsersRoute.options.component },
        ],
      },
    ],
    permissionsOf: (permissions: LevelPermission[]) => ({
      productPermissions: permissions,
    }),
  },
  {
    ...levels[2]!,
    page: RepositoryUsersRoute,
    entity: mocks.getRepository,
    routes: [
      {
        path: '/organizations/$orgId/products/$productId/repositories/$repoId',
        component: Outlet,
        children: [
          {
            id: '_repo-layout',
            children: [
              {
                path: 'users',
                children: [
                  {
                    path: '/',
                    component: RepositoryUsersRoute.options.component,
                  },
                ],
              },
            ],
          },
        ],
      },
    ],
    permissionsOf: (permissions: LevelPermission[]) => ({
      repositoryPermissions: permissions,
    }),
  },
];

const userInfo = (permissions: Partial<UserInfo>): { data: UserInfo } => ({
  data: { fullName: 'Me', username: 'me', isSuperuser: false, ...permissions },
});

const renderPage = (page: (typeof pages)[number]) =>
  renderInteractiveWithRouter(null, {
    path: page.path,
    routes: page.routes,
    withQueryClient: true,
  });

const formControls = () => [
  screen.getByRole('textbox', { name: 'Username' }),
  screen.getByRole('combobox', { name: 'Role' }),
  screen.getByRole('button', { name: 'Assign role' }),
];

describe('users pages', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    for (const page of pages) {
      page.entity.mockResolvedValue({
        data: { id: 1, name: `The ${page.level}`, url: 'https://example.com' },
      });
      page.users.mockResolvedValue(pageOf(users));
      page.assign.mockResolvedValue({ data: undefined });
    }
  });

  it.each(pages)(
    'shows the users of the $level level to readers, with the form and the actions disabled',
    async (page) => {
      mocks.getUserInfo.mockResolvedValue(
        userInfo(page.permissionsOf(['READ']))
      );
      const { user } = renderPage(page);

      await screen.findByText('writer');
      await waitFor(() =>
        expect(mocks.getUserInfo).toHaveBeenCalledWith(
          expect.objectContaining({ query: page.hierarchyId })
        )
      );

      for (const control of formControls()) expect(control).toBeDisabled();
      for (const button of within(getRow('writer')).getAllByRole('button')) {
        expect(button).toBeDisabled();
      }

      // A disabled control ignores the pointer, so the span around it opens the tooltip.
      for (const control of formControls()) {
        await user.hover(control.parentElement!);
        await waitFor(() =>
          expect(control.parentElement).toHaveAttribute(
            'data-state',
            'delayed-open'
          )
        );
        await user.unhover(control.parentElement!);
      }
      expect(
        screen.getAllByRole('tooltip', { name: 'Insufficient permissions.' })
          .length
      ).toBeGreaterThan(0);
    }
  );

  it.each(pages)(
    'lets users with permission to manage roles add a user on the $level level',
    async (page) => {
      mocks.getUserInfo.mockResolvedValue(
        userInfo(page.permissionsOf(['READ', 'MANAGE_ROLES']))
      );
      const { user } = renderPage(page);

      // The button is rendered anew once the permissions are known, so it is looked up again.
      const getAssignButton = () =>
        screen.getByRole('button', { name: 'Assign role' });
      await waitFor(() => expect(getAssignButton()).toBeEnabled());
      expect(
        within(getRow('writer')).getByRole('button', {
          name: `Remove user from this ${page.level}`,
        })
      ).toBeEnabled();

      await user.type(
        screen.getByRole('textbox', { name: 'Username' }),
        'newcomer'
      );
      await user.click(getAssignButton());

      await waitFor(() =>
        expect(page.assign).toHaveBeenCalledWith(
          expect.objectContaining({
            path: { ...page.hierarchyId, role: 'READER' },
            body: { username: 'newcomer' },
          })
        )
      );
    }
  );

  it('keeps the form disabled until the permissions are known', async () => {
    const page = pages[0]!;
    let resolveUserInfo: (value: { data: UserInfo }) => void = () => {};
    mocks.getUserInfo.mockReturnValue(
      new Promise((resolve) => {
        resolveUserInfo = resolve;
      })
    );
    renderPage(page);

    await screen.findByText('writer');
    for (const control of formControls()) expect(control).toBeDisabled();

    resolveUserInfo(userInfo(page.permissionsOf(['READ', 'MANAGE_ROLES'])));

    await waitFor(() => {
      for (const control of formControls()) expect(control).toBeEnabled();
    });
  });
});
