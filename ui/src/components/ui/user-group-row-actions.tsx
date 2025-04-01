/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import { Row } from '@tanstack/react-table';
import { Eye, Pen, Shield } from 'lucide-react';

import { UserGroup, UserWithGroups } from '@/api/requests';
import { EllipsisIconButton } from '@/components/ellipsis-icon-button.tsx';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu.tsx';

type UserGroupRowActionsProps = {
  row: Row<UserWithGroups>;
  onJoinAdminsGroup: (user: UserWithGroups) => Promise<void>;
  onJoinWritersGroup: (user: UserWithGroups) => Promise<void>;
  onJoinReadersGroup: (user: UserWithGroups) => Promise<void>;
  disabled: boolean;
};

/**
 * Component for rendering row actions in a user group table.
 * Provides options to join different user groups (ADMINS, WRITERS, READERS).
 *
 * @param {UserGroupRowActionsProps} props - The props for the component.
 * @returns {JSX.Element} The rendered component.
 */
export function UserGroupRowActions({
  row,
  onJoinAdminsGroup,
  onJoinWritersGroup,
  onJoinReadersGroup,
  ...nativeButtonAttributes // From standard HTML button attributes
}: UserGroupRowActionsProps) {
  const userWithGroups: UserWithGroups = row.original;
  const groups: UserGroup[] = row.original.groups;

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <EllipsisIconButton {...nativeButtonAttributes} />
      </DropdownMenuTrigger>

      <DropdownMenuContent side='bottom' align='end'>
        {!groups.includes('ADMINS') && (
          <DropdownMenuItem onSelect={() => onJoinAdminsGroup(userWithGroups)}>
            <div className='flex items-center gap-x-2'>
              <span>Join ADMINS</span>
              <Shield size={16} />
            </div>
          </DropdownMenuItem>
        )}

        {!groups.includes('WRITERS') && (
          <DropdownMenuItem onSelect={() => onJoinWritersGroup(userWithGroups)}>
            <div className='flex items-center gap-x-2'>
              <span>Join WRITERS</span>
              <Pen size={16} />
            </div>
          </DropdownMenuItem>
        )}

        {!groups.includes('READERS') && (
          <DropdownMenuItem onSelect={() => onJoinReadersGroup(userWithGroups)}>
            <div className='flex items-center gap-x-2'>
              <span>Join READERS</span>
              <Eye size={16} />
            </div>
          </DropdownMenuItem>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
