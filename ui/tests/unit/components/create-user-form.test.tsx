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
      'Password',
      'Password change required on first login',
      'First name',
      'Last name',
      'Email address',
      'Organizations',
    ]);

    const passwordField = screen
      .getByLabelText('Password')
      .closest('[data-slot="form-item"]');
    const passwordChangeField = screen
      .getByRole('checkbox', {
        name: 'Password change required on first login',
      })
      .closest('[data-slot="form-item"]');

    expect(passwordField?.parentElement).toBe(
      passwordChangeField?.parentElement
    );
    expect(passwordField?.parentElement).toHaveClass(
      'grid',
      'items-center',
      'gap-4',
      'md:grid-cols-[minmax(0,1fr)_auto]'
    );

    const firstNameField = screen
      .getByLabelText('First name')
      .closest('[data-slot="form-item"]');
    const lastNameField = screen
      .getByLabelText('Last name')
      .closest('[data-slot="form-item"]');

    expect(firstNameField?.parentElement).toBe(lastNameField?.parentElement);
    expect(firstNameField?.parentElement).toHaveClass(
      'grid',
      'gap-4',
      'md:grid-cols-2'
    );
  });

  it('enables creating a user only when the form is valid', async () => {
    const user = userEvent.setup();

    render(<CreateUserForm isPending={false} onSubmit={vi.fn()} />);

    const createButton = screen.getByRole('button', { name: 'Create' });
    const usernameInput = screen.getByLabelText('Username');
    const passwordInput = screen.getByLabelText('Password');
    const emailInput = screen.getByLabelText('Email address');

    expect(createButton).toBeDisabled();

    await user.type(usernameInput, 'jdoe');
    expect(createButton).toBeDisabled();

    await user.clear(usernameInput);
    await user.type(passwordInput, 'initial password');
    expect(createButton).toBeDisabled();

    await user.type(usernameInput, 'jdoe');
    await waitFor(() => expect(createButton).toBeEnabled());

    await user.type(emailInput, 'invalid');
    expect(createButton).toBeDisabled();

    await user.clear(emailInput);
    await waitFor(() => expect(createButton).toBeEnabled());
  });

  it('shows the creation progress while pending', () => {
    render(<CreateUserForm isPending onSubmit={vi.fn()} />);

    expect(
      screen.getByRole('button', { name: 'Creating user...' })
    ).toBeDisabled();
  });

  it('shows the creation progress while submitting', async () => {
    const user = userEvent.setup();
    let resolveSubmit: (() => void) | undefined;
    const onSubmit = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          resolveSubmit = resolve;
        })
    );

    render(<CreateUserForm isPending={false} onSubmit={onSubmit} />);

    await user.type(screen.getByLabelText('Username'), 'jdoe');
    await user.type(screen.getByLabelText('Password'), 'initial password');
    await user.click(screen.getByRole('button', { name: 'Create' }));

    expect(
      await screen.findByRole('button', { name: 'Creating user...' })
    ).toBeDisabled();

    resolveSubmit?.();

    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Create' })).toBeEnabled()
    );
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
    await user.type(screen.getByLabelText('Password'), 'x');
    await user.clear(screen.getByLabelText('Password'));

    expect(await screen.findByText('A password is required.')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Create' })).toBeDisabled();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('submits the user without an organization', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();

    render(<CreateUserForm isPending={false} onSubmit={onSubmit} />);

    await user.type(screen.getByLabelText('Username'), 'jdoe');
    await user.type(screen.getByLabelText('Password'), 'initial password');
    await user.click(screen.getByRole('button', { name: 'Create' }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledOnce());
    expect(onSubmit.mock.calls[0]?.[0]).toEqual({
      organizations: [],
      password: 'initial password',
      temporary: true,
      username: 'jdoe',
    });
  });

  it('submits the user with the selected organization', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();

    render(<CreateUserForm isPending={false} onSubmit={onSubmit} />);

    await user.type(screen.getByLabelText('Username'), 'jdoe');
    await user.type(screen.getByLabelText('Password'), 'initial password');
    await user.click(
      screen.getByPlaceholderText(
        '(optional) Start typing to find organizations...'
      )
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
