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

import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { CreateRepositoryForm } from '@/routes/organizations/$orgId/products/$productId/create-repository';

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

    await user.type(urlInput, 'https://example.com/repository.git');
    expect(createButton).toBeEnabled();

    await user.clear(urlInput);
    expect(createButton).toBeDisabled();
  });
});
