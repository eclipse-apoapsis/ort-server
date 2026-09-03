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

import { DeleteDialog } from '@/components/delete-dialog';

const defaultDescription =
  'Note that deletion is irreversible and might have unwanted side effects.';

const renderDialog = (description?: React.ReactNode, thingId?: string) =>
  render(
    <DeleteDialog
      thingName='repository'
      thingId={thingId}
      description={description}
      uiComponent={<button>Open dialog</button>}
      onDelete={vi.fn()}
    />
  );

describe('DeleteDialog', () => {
  it('shows the default description', async () => {
    const user = userEvent.setup();
    renderDialog();

    await user.click(screen.getByRole('button', { name: 'Open dialog' }));

    expect(screen.getByText(defaultDescription)).toBeVisible();
  });

  it('shows a custom description instead of the default description', async () => {
    const user = userEvent.setup();
    renderDialog(<span>Custom deletion consequences.</span>);

    await user.click(screen.getByRole('button', { name: 'Open dialog' }));

    expect(screen.getByText('Custom deletion consequences.')).toBeVisible();
    expect(screen.queryByText(defaultDescription)).not.toBeInTheDocument();
  });

  it('requires the thing ID to enable deletion', async () => {
    const user = userEvent.setup();
    renderDialog(undefined, 'repository-id');

    await user.click(screen.getByRole('button', { name: 'Open dialog' }));

    const deleteButton = screen.getByRole('button', { name: 'Delete' });
    const confirmationInput = screen.getByRole('textbox');

    expect(deleteButton).toBeDisabled();

    await user.type(confirmationInput, 'wrong-id');
    expect(deleteButton).toBeDisabled();

    await user.clear(confirmationInput);
    await user.type(confirmationInput, 'repository-id');
    expect(deleteButton).toBeEnabled();
  });
});
