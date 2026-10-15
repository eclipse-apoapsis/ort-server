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

import {
  useMutation,
  useQuery,
  useQueryClient,
  type DefaultError,
  type QueryKey,
  type UseQueryOptions,
} from '@tanstack/react-query';
import { useSearch } from '@tanstack/react-router';
import type { AxiosError } from 'axios';
import { Eye, FileOutput, Pen, Shield } from 'lucide-react';
import { z } from 'zod';

import { PagedResponseUserWithGroups, UserGroup, UserWithGroups } from '@/api';
import { DataTable } from '@/components/data-table/data-table.tsx';
import { DeleteDialog } from '@/components/delete-dialog.tsx';
import { DeleteIconButton } from '@/components/delete-icon-button.tsx';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { UserRoleRowActions } from '@/components/ui/user-role-row-actions.tsx';
import {
  createAppColumnHelper,
  selectNoTableState,
  useAppTable,
  type AppRow,
} from '@/hooks/use-app-table';
import { useUser } from '@/hooks/use-user.ts';
import { ApiError } from '@/lib/api-error';
import { toast, toastError } from '@/lib/toast';
import { roleSchema } from '@/schemas';

type Role = z.infer<typeof roleSchema>;

type UsersQuery = { limit: number; offset: number; sort: string };

type UsersTableProps<TQueryKey extends QueryKey> = {
  // The hierarchy level the users belong to, as named in the texts of the table.
  level: 'organization' | 'product' | 'repository';
  // The options of the query for one page of the users of the level.
  usersOptions: (
    query: UsersQuery
  ) => UseQueryOptions<
    PagedResponseUserWithGroups,
    AxiosError<DefaultError>,
    PagedResponseUserWithGroups,
    TQueryKey
  >;
  // The key shared by the queries for all pages of the users of the level.
  usersQueryKey: QueryKey;
  mapGroupToRole: (group: UserGroup) => Role;
  assignRole: (username: string, role: Role) => Promise<unknown>;
  removeRole: (username: string, role: Role) => Promise<unknown>;
};

type UserActionsProps = Omit<UsersTableProps<QueryKey>, 'usersOptions'> & {
  row: AppRow<UserWithGroups>;
};

const capitalize = (text: string) =>
  text.charAt(0).toUpperCase() + text.slice(1);

const UserActions = ({
  row,
  level,
  usersQueryKey,
  mapGroupToRole,
  assignRole,
  removeRole,
}: UserActionsProps) => {
  const queryClient = useQueryClient();
  const { username } = row.original.user;

  const { mutateAsync: assign, isPending: isAssignRolePending } = useMutation({
    mutationFn: (role: Role) => assignRole(username, role),
    onSuccess(_response, role) {
      queryClient.invalidateQueries({ queryKey: usersQueryKey });
      toast.info('Assign Role', {
        description: `Role ${role} assigned to user "${username}" successfully.`,
      });
    },
    onError(error: ApiError) {
      toastError(error.message, error);
    },
  });

  const { mutateAsync: remove, isPending: isRemoveRolePending } = useMutation({
    mutationFn: (role: Role) => removeRole(username, role),
    onSuccess(_response, role) {
      // The users query is invalidated after all roles have been removed.
      toast.info('Remove Role', {
        description: `Role ${role} removed from user "${username}" successfully.`,
      });
    },
    onError(error: ApiError) {
      toastError(error.message, error);
    },
  });

  // Remove the user from the level by removing all assigned roles.
  async function removeFromLevel() {
    try {
      await Promise.all(
        row.original.groups.map((group) => remove(mapGroupToRole(group)))
      );
      // Upon successful removal of the user, invalidate the users query
      // to refresh the data in the table.
      queryClient.invalidateQueries({ queryKey: usersQueryKey });
      toast.info(`Remove User from ${capitalize(level)}`, {
        description: `User "${username}" removed from the ${level} successfully.`,
      });
    } catch (error) {
      toastError(`Failed to remove the user from ${level}`, error as ApiError);
    }
  }

  return username !== useUser().username ? (
    <div className='flex gap-2'>
      <Tooltip delayDuration={300}>
        <TooltipTrigger asChild>
          <UserRoleRowActions
            row={row}
            onAssignAdminRole={() => assign('ADMIN').then(() => {})}
            onAssignWriterRole={() => assign('WRITER').then(() => {})}
            onAssignReaderRole={() => assign('READER').then(() => {})}
            disabled={isAssignRolePending || isRemoveRolePending}
          />
        </TooltipTrigger>
        <TooltipContent>Assign role</TooltipContent>
      </Tooltip>

      <DeleteDialog
        tooltip={`Remove user from this ${level}`}
        title={`Confirm removal of the user from this ${level}`}
        thingName='user'
        thingId={username}
        itemName={level}
        uiComponent={
          <DeleteIconButton
            icon={<FileOutput size={16} />}
            disabled={isRemoveRolePending}
            srDescription={`Remove user from this ${level}`}
          />
        }
        onDelete={() => removeFromLevel()}
      />
    </div>
  ) : (
    <div>
      <DeleteIconButton icon={<FileOutput size={16} />} disabled={true} />
    </div>
  );
};

const columnHelper = createAppColumnHelper<UserWithGroups>();

const createColumns = (actions: Omit<UserActionsProps, 'row'>) =>
  columnHelper.columns([
    columnHelper.accessor('user.username', {
      header: 'Username',
      cell: ({ row }) => <>{row.original.user.username}</>,
    }),
    columnHelper.accessor('user.firstName', {
      header: 'First name',
      cell: ({ row }) => <>{row.original.user.firstName}</>,
    }),
    columnHelper.accessor('user.lastName', {
      header: 'Last name',
      cell: ({ row }) => <>{row.original.user.lastName}</>,
    }),
    columnHelper.accessor('user.email', {
      header: 'Email address',
      cell: ({ row }) => <>{row.original.user.email}</>,
    }),
    columnHelper.accessor('groups', {
      header: 'Role',
      cell: ({ row }) => {
        const groups = row.original.groups;
        let IconComponent;
        let effectiveRole;
        if (groups.includes('ADMINS')) {
          IconComponent = Shield;
          effectiveRole = 'ADMIN';
        } else if (groups.includes('WRITERS')) {
          IconComponent = Pen;
          effectiveRole = 'WRITER';
        } else if (groups.includes('READERS')) {
          IconComponent = Eye;
          effectiveRole = 'READER';
        } else {
          return <>{groups.join(' ')}</>;
        }

        return (
          <Tooltip>
            <TooltipTrigger asChild>
              <IconComponent size={16} />
            </TooltipTrigger>
            <TooltipContent>{effectiveRole}</TooltipContent>
          </Tooltip>
        );
      },
    }),
    columnHelper.display({
      id: 'actions',
      header: () => <div>Actions</div>,
      size: 70,
      cell: ({ row }) => <UserActions row={row} {...actions} />,
    }),
  ]);

// The users of an organization, a product or a repository, with actions to change their roles.
export const UsersTable = <TQueryKey extends QueryKey>({
  usersOptions,
  ...actions
}: UsersTableProps<TQueryKey>) => {
  const search = useSearch({ strict: false });
  const { page = 1, pageSize = 10 } = search;
  const pageIndex = page - 1;

  const { data: usersWithGroups } = useQuery(
    usersOptions({
      limit: pageSize,
      offset: pageIndex * pageSize,
      sort: 'username',
    })
  );

  const table = useAppTable(
    {
      data: usersWithGroups?.data || [],
      columns: createColumns(actions),
      pageCount: Math.ceil(
        (usersWithGroups?.pagination.totalCount ?? 0) / pageSize
      ),
      manualPagination: true, // Using server-side pagination
      state: {
        pagination: {
          pageIndex: pageIndex,
          pageSize: pageSize,
        },
      },
    },
    selectNoTableState
  );

  return (
    <DataTable
      table={table}
      setCurrentPageOptions={(currentPage) => {
        return {
          search: { ...search, page: currentPage },
        };
      }}
      setPageSizeOptions={(size) => {
        return {
          search: { ...search, page: 1, pageSize: size },
        };
      }}
    />
  );
};
