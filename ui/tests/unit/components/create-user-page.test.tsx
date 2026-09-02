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

import type { ReactElement } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { CreateUserPage } from '@/routes/admin/users/create-user';
import type { CreateUserFormValues } from '@/schemas';

const mocks = vi.hoisted(() => ({
  addUserToReaders: vi.fn(),
  createUser: vi.fn(),
  navigate: vi.fn(),
  toastError: vi.fn(),
  toastInfo: vi.fn(),
}));

vi.mock('@tanstack/react-query', () => ({
  useMutation: (options: { mutationKey?: string[] }) => ({
    mutateAsync:
      options.mutationKey?.[0] === 'create-user'
        ? mocks.createUser
        : mocks.addUserToReaders,
    isPending: false,
  }),
}));

vi.mock('@tanstack/react-router', () => ({
  createFileRoute: () => (options: Record<string, unknown>) => options,
  useNavigate: () => mocks.navigate,
}));

vi.mock('@/api/@tanstack/react-query.gen', () => ({
  getOrganizationsInfiniteOptions: vi.fn(),
  postUserMutation: () => ({ mutationKey: ['create-user'] }),
  putOrganizationRoleToUserMutation: () => ({
    mutationKey: ['add-user-to-readers'],
  }),
}));

vi.mock('@/lib/toast', () => ({
  toast: { info: mocks.toastInfo },
  toastError: mocks.toastError,
}));

type CreateUserPageElement = ReactElement<{
  onSubmit: (values: CreateUserFormValues) => Promise<void>;
}>;

const values: CreateUserFormValues = {
  username: 'JDOE',
  password: 'initial password',
  temporary: true,
  organizations: [
    { value: '1', label: 'First Organization' },
    { value: '2', label: 'Second Organization' },
  ],
};

function getOnSubmit() {
  return (CreateUserPage() as CreateUserPageElement).props.onSubmit;
}

describe('CreateUserPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.createUser.mockResolvedValue(undefined);
    mocks.addUserToReaders.mockResolvedValue(undefined);
  });

  it('creates the user, assigns roles and reports success once', async () => {
    await getOnSubmit()(values);

    expect(mocks.createUser).toHaveBeenCalledWith({
      body: {
        email: undefined,
        firstName: undefined,
        lastName: undefined,
        password: 'initial password',
        temporary: true,
        username: 'jdoe',
      },
    });
    expect(mocks.addUserToReaders).toHaveBeenCalledTimes(2);
    expect(mocks.addUserToReaders).toHaveBeenNthCalledWith(1, {
      body: { username: 'jdoe' },
      path: { organizationId: 1, role: 'READER' },
    });
    expect(mocks.addUserToReaders).toHaveBeenNthCalledWith(2, {
      body: { username: 'jdoe' },
      path: { organizationId: 2, role: 'READER' },
    });
    expect(mocks.toastInfo).toHaveBeenCalledOnce();
    expect(mocks.toastInfo).toHaveBeenCalledWith('Create User', {
      description:
        'User "jdoe" was created and granted the READER role for ' +
        '"First Organization", "Second Organization".',
    });
    expect(mocks.toastError).not.toHaveBeenCalled();
    expect(mocks.navigate).toHaveBeenCalledOnce();
    expect(mocks.navigate).toHaveBeenCalledWith({ to: '/admin/users' });
  });

  it('reports failed role assignments and still navigates', async () => {
    const error = new Error('Could not assign the role.');
    mocks.addUserToReaders
      .mockResolvedValueOnce(undefined)
      .mockRejectedValueOnce(error);

    await getOnSubmit()(values);

    expect(mocks.toastInfo).not.toHaveBeenCalled();
    expect(mocks.toastError).toHaveBeenCalledWith(
      'User "jdoe" was created, but the READER role could not be granted ' +
        'for "Second Organization". Assign the missing roles in each ' +
        'organization\'s "Users" section.',
      error
    );
    expect(mocks.navigate).toHaveBeenCalledOnce();
  });

  it('stops when user creation fails', async () => {
    mocks.createUser.mockRejectedValue(new Error('Could not create the user.'));

    await expect(getOnSubmit()(values)).resolves.toBeUndefined();

    expect(mocks.addUserToReaders).not.toHaveBeenCalled();
    expect(mocks.toastInfo).not.toHaveBeenCalled();
    expect(mocks.navigate).not.toHaveBeenCalled();
  });
});
