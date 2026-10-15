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

import { screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { UserGroup, UserWithGroups } from '@/api';
import { RepositoryUsersTable } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/users/-components/repository-users-table';
import { ProductUsersTable } from '@/routes/organizations/$orgId/products/$productId/users/-components/product-users-table';
import { OrganizationUsersTable } from '@/routes/organizations/$orgId/users/-components/organization-users-table';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';
import { getColumnTexts, hierarchyLevels, pageOf } from '../fixtures/table';

const mocks = vi.hoisted(() => ({
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

const createUser = (username: string, groups: UserGroup[]): UserWithGroups => ({
  user: {
    username,
    firstName: `First ${username}`,
    lastName: `Last ${username}`,
    email: `${username}@example.com`,
  },
  groups,
});

const users = [
  createUser('admin', ['ADMINS']),
  createUser('writer', ['WRITERS', 'READERS']),
  createUser('me', ['ADMINS']),
];

const renderUsers = (level: (typeof levels)[number], search = '') =>
  renderInteractiveWithRouter(<level.Table />, {
    path: `${level.path}${search}`,
    routes: level.routes,
    withQueryClient: true,
  });

const getRow = (username: string) =>
  screen.getByRole('cell', { name: username }).closest('tr')!;

describe('users tables', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    for (const level of levels) {
      level.users.mockResolvedValue(pageOf(users));
      level.assign.mockResolvedValue({ data: undefined });
      level.remove.mockResolvedValue({ data: undefined });
    }
  });

  it.each(levels)(
    'lists the users of the $level level and their roles',
    async (level) => {
      const { user } = renderUsers(level, '?page=2&pageSize=5');

      expect(await screen.findByText('writer')).toBeVisible();
      expect(getColumnTexts('Username')).toEqual(['admin', 'writer', 'me']);
      expect(level.users).toHaveBeenCalledWith(
        expect.objectContaining({
          path: level.hierarchyId,
          query: { limit: 5, offset: 5, sort: 'username' },
        })
      );

      // The role column comes before the actions, so its icon is the first in the row.
      await user.hover(getRow('writer').querySelector('svg')!);

      expect(
        await screen.findByRole('tooltip', { name: 'WRITER' })
      ).toBeInTheDocument();
    }
  );

  it.each(levels)(
    'assigns a role at the $level level and refreshes the users',
    async (level) => {
      const { user } = renderUsers(level);

      await screen.findByText('writer');
      await user.click(
        getRow('writer').querySelector<HTMLElement>('[aria-haspopup="menu"]')!
      );
      await user.click(
        await screen.findByRole('menuitem', { name: 'Assign ADMIN role' })
      );

      await waitFor(() =>
        expect(level.assign).toHaveBeenCalledWith(
          expect.objectContaining({
            path: { ...level.hierarchyId, role: 'ADMIN' },
            body: { username: 'writer' },
          })
        )
      );
      await waitFor(() => expect(level.users).toHaveBeenCalledTimes(2));
    }
  );

  it.each(levels)(
    'removes a user from the $level level after confirming the username',
    async (level) => {
      const { user } = renderUsers(level);

      await screen.findByText('writer');
      await user.click(
        within(getRow('writer')).getByRole('button', {
          name: `Remove user from this ${level.level}`,
        })
      );

      const dialog = await screen.findByRole('alertdialog');
      const deleteButton = within(dialog).getByRole('button', {
        name: 'Delete',
      });
      expect(deleteButton).toBeDisabled();

      await user.type(within(dialog).getByRole('textbox'), 'writer');
      expect(deleteButton).toBeEnabled();
      await user.click(deleteButton);

      await waitFor(() => expect(level.users).toHaveBeenCalledTimes(2));
      expect(level.remove).toHaveBeenCalledTimes(2);
      for (const role of ['WRITER', 'READER']) {
        expect(level.remove).toHaveBeenCalledWith(
          expect.objectContaining({
            path: { ...level.hierarchyId, role },
            query: { username: 'writer' },
          })
        );
      }
    }
  );

  it.each(levels)(
    'does not let the signed-in user change their own roles at the $level level',
    async (level) => {
      renderUsers(level);

      await screen.findByText('me');
      const buttons = within(getRow('me')).getAllByRole('button');

      expect(buttons).toHaveLength(1);
      expect(buttons[0]).toBeDisabled();
    }
  );
});
