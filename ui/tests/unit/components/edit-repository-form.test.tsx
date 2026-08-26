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

import { EditRepositoryForm } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/settings/-components/edit-repository-form';

describe('EditRepositoryForm', () => {
  it('enables submitting only when the URL is valid', async () => {
    const user = userEvent.setup();

    render(
      <EditRepositoryForm
        defaultValues={{
          description: '',
          name: '',
          type: 'GIT',
          url: 'https://example.com/repository.git',
        }}
        isPending={false}
        onCancel={vi.fn()}
        onSubmit={vi.fn()}
      />
    );

    const urlInput = screen.getByLabelText('URL');
    const submitButton = screen.getByRole('button', { name: 'Submit' });

    await waitFor(() => expect(submitButton).toBeEnabled());

    for (const invalidUrl of ['not a URL', 's:', 'httttps:']) {
      await user.clear(urlInput);
      await user.type(urlInput, invalidUrl);
      expect(submitButton).toBeDisabled();
    }

    await user.clear(urlInput);
    await user.type(urlInput, 'https://example.com/updated-repository.git');
    expect(submitButton).toBeEnabled();
  });
});
