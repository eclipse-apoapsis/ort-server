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

import { UserRoleRowActions } from '@/components/ui/user-role-row-actions';
import type { UserWithRole } from '@/helpers/role-helpers';
import {
  selectNoTableState,
  useAppTable,
  type AppColumnDef,
} from '@/hooks/use-app-table';

const columns: AppColumnDef<UserWithRole>[] = [];

type UserRoleRowActionsHarnessProps = {
  role: UserWithRole['role'];
  onAssignAdminRole?: () => Promise<void>;
  onAssignWriterRole?: () => Promise<void>;
  onAssignReaderRole?: () => Promise<void>;
};

const UserRoleRowActionsHarness = ({
  role,
  onAssignAdminRole = async () => {},
  onAssignWriterRole = async () => {},
  onAssignReaderRole = async () => {},
}: UserRoleRowActionsHarnessProps) => {
  const table = useAppTable(
    {
      columns,
      data: [{ role, user: { username: 'test-user' } }],
    },
    selectNoTableState
  );

  return (
    <UserRoleRowActions
      row={table.getRowModel().rows[0]!}
      onAssignAdminRole={onAssignAdminRole}
      onAssignWriterRole={onAssignWriterRole}
      onAssignReaderRole={onAssignReaderRole}
      disabled={false}
    />
  );
};

describe('UserRoleRowActions', () => {
  it.each([
    ['ADMIN', 'Assign ADMIN role'],
    ['WRITER', 'Assign WRITER role'],
    ['READER', 'Assign READER role'],
  ] as const)(
    'does not offer the %s role the user already has',
    async (role, label) => {
      const user = userEvent.setup();
      render(<UserRoleRowActionsHarness role={role} />);

      await user.click(screen.getByRole('button'));

      expect(
        screen.queryByRole('menuitem', { name: label })
      ).not.toBeInTheDocument();
    }
  );

  it('uses assignment labels and invokes the selected role callback', async () => {
    const user = userEvent.setup();
    const onAssignWriterRole = vi.fn().mockResolvedValue(undefined);
    render(
      <UserRoleRowActionsHarness
        role='READER'
        onAssignWriterRole={onAssignWriterRole}
      />
    );

    await user.click(screen.getByRole('button'));

    expect(
      screen.getByRole('menuitem', { name: 'Assign ADMIN role' })
    ).toBeVisible();

    await user.click(
      screen.getByRole('menuitem', { name: 'Assign WRITER role' })
    );

    expect(onAssignWriterRole).toHaveBeenCalledOnce();
  });
});
