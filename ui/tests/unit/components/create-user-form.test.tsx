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

import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { CreateUserForm } from '@/routes/admin/users/create-user';

vi.mock('@/hooks/use-in-view', () => ({
  useInView: () => ({ ref: vi.fn(), inView: false }),
}));

vi.mock('@/hooks/use-infinite-list', () => ({
  useInfiniteList: () => ({
    items: [{ id: 1, name: 'Test Organization' }],
    totalCount: 1,
    isPending: false,
    isError: false,
    error: null,
    hasNextPage: false,
    isFetchingNextPage: false,
    fetchNextPage: vi.fn(),
  }),
}));

describe('CreateUserForm', () => {
  it('renders the fields in the expected order', () => {
    const { container } = render(
      <CreateUserForm isPending={false} onSubmit={vi.fn()} />
    );

    const labels = Array.from(
      container.querySelectorAll('[data-slot="form-label"]')
    ).map((label) => label.textContent);

    expect(labels).toEqual([
      'Username',
      'First name',
      'Last name',
      'Email address',
      'Password',
      'Password change required on first login',
      'Organizations',
    ]);
  });

  it('enables creating a user while idle', () => {
    render(<CreateUserForm isPending={false} onSubmit={vi.fn()} />);

    expect(screen.getByRole('button', { name: 'Create' })).toBeEnabled();
  });

  it('shows the creation progress while pending', () => {
    render(<CreateUserForm isPending onSubmit={vi.fn()} />);

    expect(
      screen.getByRole('button', { name: 'Creating user...' })
    ).toBeDisabled();
  });

  it('requires a password', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();

    render(<CreateUserForm isPending={false} onSubmit={onSubmit} />);

    expect(screen.getByLabelText('Password')).not.toHaveAttribute(
      'placeholder',
      '(optional)'
    );

    await user.type(screen.getByLabelText('Username'), 'jdoe');
    await user.click(
      screen.getByPlaceholderText('Start typing to find organizations...')
    );
    await user.click(await screen.findByText('Test Organization'));
    await user.click(screen.getByRole('button', { name: 'Create' }));

    expect(await screen.findByText('A password is required.')).toBeVisible();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('submits the user with the selected organization', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();

    render(<CreateUserForm isPending={false} onSubmit={onSubmit} />);

    await user.type(screen.getByLabelText('Username'), 'jdoe');
    await user.type(screen.getByLabelText('Password'), 'initial password');
    await user.click(
      screen.getByPlaceholderText('Start typing to find organizations...')
    );
    await user.click(await screen.findByText('Test Organization'));
    await user.click(screen.getByRole('button', { name: 'Create' }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledOnce());
    expect(onSubmit.mock.calls[0]?.[0]).toEqual({
      organizations: [{ label: 'Test Organization', value: '1' }],
      password: 'initial password',
      temporary: true,
      username: 'jdoe',
    });
  });
});
