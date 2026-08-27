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

import { CreateRepositoryForm } from '@/routes/organizations/$orgId/products/$productId/create-repository';

const repositoryUrl = 'https://example.com/repository.git';
const passwordLabel = 'Password or Personal Access Token (PAT)';

describe('CreateRepositoryForm', () => {
  it('enables creating a repository only when the URL is valid', async () => {
    const user = userEvent.setup();

    render(<CreateRepositoryForm isPending={false} onSubmit={vi.fn()} />);

    const urlInput = screen.getByLabelText('URL');
    const createButton = screen.getByRole('button', { name: 'Create' });

    expect(createButton).toBeDisabled();

    for (const invalidUrl of ['not a URL', 's:', 'httttps:']) {
      await user.type(urlInput, invalidUrl);
      expect(createButton).toBeDisabled();
      await user.clear(urlInput);
    }

    await user.type(urlInput, repositoryUrl);
    expect(createButton).toBeEnabled();

    await user.clear(urlInput);
    expect(createButton).toBeDisabled();
  });

  it('renders the fields in the expected order', () => {
    const { container } = render(
      <CreateRepositoryForm isPending={false} onSubmit={vi.fn()} />
    );

    const labels = Array.from(container.querySelectorAll('label')).map(
      (label) => label.textContent
    );

    expect(labels).toEqual([
      'URL',
      'Type',
      'Name',
      'Description',
      'Username',
      passwordLabel,
    ]);

    const urlField = screen
      .getByLabelText('URL')
      .closest('[data-slot="form-item"]');
    const typeField = screen
      .getByLabelText('Type')
      .closest('[data-slot="form-item"]');

    expect(urlField?.parentElement).toBe(typeField?.parentElement);
    expect(urlField?.parentElement).toHaveClass(
      'grid',
      'md:grid-cols-[minmax(0,1fr)_auto]'
    );

    const usernameField = screen
      .getByLabelText('Username')
      .closest('[data-slot="form-item"]');
    const passwordField = screen
      .getByLabelText(passwordLabel)
      .closest('[data-slot="form-item"]');

    expect(usernameField?.parentElement).toBe(passwordField?.parentElement);
    expect(usernameField?.parentElement).toHaveClass('grid', 'md:grid-cols-2');
    expect(
      screen.getAllByPlaceholderText(
        '(optional, only needed for private repositories)'
      )
    ).toHaveLength(2);
  });

  it('requires a password or token when a username is entered', async () => {
    const user = userEvent.setup();

    render(<CreateRepositoryForm isPending={false} onSubmit={vi.fn()} />);

    const createButton = screen.getByRole('button', { name: 'Create' });
    const usernameInput = screen.getByLabelText('Username');
    const passwordInput = screen.getByLabelText(passwordLabel);

    await user.type(screen.getByLabelText('URL'), repositoryUrl);
    await user.type(usernameInput, 'jdoe');

    expect(createButton).toBeDisabled();
    expect(
      await screen.findByText(
        'A password or personal access token is required for the username.'
      )
    ).toBeVisible();

    await user.type(passwordInput, 'token');
    await waitFor(() => expect(createButton).toBeEnabled());

    await user.clear(passwordInput);
    expect(createButton).toBeDisabled();
    await user.clear(usernameInput);
    await waitFor(() => expect(createButton).toBeEnabled());
  });

  it('requires a username when a password or token is entered', async () => {
    const user = userEvent.setup();

    render(<CreateRepositoryForm isPending={false} onSubmit={vi.fn()} />);

    await user.type(screen.getByLabelText('URL'), repositoryUrl);
    await user.type(screen.getByLabelText(passwordLabel), 'token');

    expect(screen.getByRole('button', { name: 'Create' })).toBeDisabled();
    expect(
      await screen.findByText(
        'A username is required for the password or personal access token.'
      )
    ).toBeVisible();
  });

  it('shows and hides the password or token', async () => {
    const user = userEvent.setup();

    render(<CreateRepositoryForm isPending={false} onSubmit={vi.fn()} />);

    const passwordInput = screen.getByLabelText(passwordLabel);

    expect(passwordInput).toHaveAttribute('type', 'password');

    await user.click(screen.getByRole('button', { name: 'Show password' }));
    expect(passwordInput).toHaveAttribute('type', 'text');

    await user.click(screen.getByRole('button', { name: 'Hide password' }));
    expect(passwordInput).toHaveAttribute('type', 'password');
  });

  it('submits the repository with its credentials', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();

    render(<CreateRepositoryForm isPending={false} onSubmit={onSubmit} />);

    await user.type(screen.getByLabelText('URL'), repositoryUrl);
    await user.type(screen.getByLabelText('Username'), 'jdoe');
    await user.type(screen.getByLabelText(passwordLabel), 'token');
    await user.click(screen.getByRole('button', { name: 'Create' }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledOnce());
    expect(onSubmit.mock.calls[0]?.[0]).toEqual({
      description: '',
      name: '',
      password: 'token',
      type: 'GIT',
      url: repositoryUrl,
      username: 'jdoe',
    });
  });
});
