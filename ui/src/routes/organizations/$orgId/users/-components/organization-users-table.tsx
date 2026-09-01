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

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getRouteApi } from '@tanstack/react-router';
import { Eye, FileOutput, Pen, Shield } from 'lucide-react';

import { UserWithGroups } from '@/api';
import {
  deleteOrganizationRoleFromUserMutation,
  getOrganizationUsersOptions,
  getOrganizationUsersQueryKey,
  putOrganizationRoleToUserMutation,
} from '@/api/@tanstack/react-query.gen';
import { DataTable } from '@/components/data-table/data-table.tsx';
import { DeleteDialog } from '@/components/delete-dialog.tsx';
import { DeleteIconButton } from '@/components/delete-icon-button.tsx';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { UserRoleRowActions } from '@/components/ui/user-role-row-actions.tsx';
import { mapUserGroupToOrganizationRole } from '@/helpers/role-helpers.ts';
import {
  createAppColumnHelper,
  selectNoTableState,
  useAppTable,
} from '@/hooks/use-app-table';
import { useUser } from '@/hooks/use-user.ts';
import { ApiError } from '@/lib/api-error';
import { toast, toastError } from '@/lib/toast';

const columnHelper = createAppColumnHelper<UserWithGroups>();

const columns = columnHelper.columns([
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
    cell: function CellComponent({ row }) {
      const queryClient = useQueryClient();
      const params = routeApi.useParams();
      const organizationId = Number.parseInt(params.orgId);

      const { mutateAsync: assignRole, isPending: isAssignRolePending } =
        useMutation({
          ...putOrganizationRoleToUserMutation(),
          onSuccess(_response, parameters) {
            queryClient.invalidateQueries({
              queryKey: getOrganizationUsersQueryKey({
                path: {
                  organizationId: organizationId,
                },
              }),
            });
            toast.info('Assign Role', {
              description: `Role ${parameters.path.role} assigned to user "${row.original.user.username}" successfully.`,
            });
          },
          onError(error: ApiError) {
            toastError(error.message, error);
          },
        });

      const { mutateAsync: removeRole, isPending: isRemoveRolePending } =
        useMutation({
          ...deleteOrganizationRoleFromUserMutation(),
          onSuccess(_response, parameters) {
            // The users query is invalidated after all roles have been removed.
            toast.info('Remove Role', {
              description: `Role ${parameters.path.role} removed from user "${row.original.user.username}" successfully.`,
            });
          },
          onError(error: ApiError) {
            toastError(error.message, error);
          },
        });

      async function assignAdminRole() {
        await assignRole({
          path: { organizationId: organizationId, role: 'ADMIN' },
          body: {
            username: row.original.user.username,
          },
        });
      }

      async function assignWriterRole() {
        await assignRole({
          path: { organizationId: organizationId, role: 'WRITER' },
          body: {
            username: row.original.user.username,
          },
        });
      }

      async function assignReaderRole() {
        await assignRole({
          path: { organizationId: organizationId, role: 'READER' },
          body: {
            username: row.original.user.username,
          },
        });
      }

      // Remove the user from the organization by removing all assigned roles.
      async function removeFromOrganization() {
        try {
          await Promise.all(
            row.original.groups.map((group) =>
              removeRole({
                path: {
                  organizationId: organizationId,
                  role: mapUserGroupToOrganizationRole(group),
                },
                query: {
                  username: row.original.user.username,
                },
              })
            )
          );
          // Upon successful removal of the user, invalidate the users query
          // to refresh the data in the table.
          queryClient.invalidateQueries({
            queryKey: getOrganizationUsersQueryKey({
              path: {
                organizationId: organizationId,
              },
            }),
          });
          toast.info('Remove User from Organization', {
            description: `User "${row.original.user.username}" removed from the organization successfully.`,
          });
        } catch (error) {
          toastError(
            'Failed to remove the user from organization',
            error as ApiError
          );
        }
      }

      return row.original.user.username !== useUser().username ? (
        <div className='flex gap-2'>
          <Tooltip delayDuration={300}>
            <TooltipTrigger asChild>
              <UserRoleRowActions
                row={row}
                onAssignAdminRole={assignAdminRole}
                onAssignWriterRole={assignWriterRole}
                onAssignReaderRole={assignReaderRole}
                disabled={isAssignRolePending || isRemoveRolePending}
              />
            </TooltipTrigger>
            <TooltipContent>Assign role</TooltipContent>
          </Tooltip>

          <DeleteDialog
            tooltip='Remove user from this organization'
            title='Confirm removal of the user from this organization'
            thingName='user'
            thingId={row.original.user.username}
            itemName='organization'
            uiComponent={
              <DeleteIconButton
                icon={<FileOutput size={16} />}
                disabled={isRemoveRolePending}
                srDescription='Remove user from this organization'
              />
            }
            onDelete={() => removeFromOrganization()}
          />
        </div>
      ) : (
        <div>
          <DeleteIconButton icon={<FileOutput size={16} />} disabled={true} />
        </div>
      );
    },
  }),
]);

const routeApi = getRouteApi('/organizations/$orgId/users');

export const OrganizationUsersTable = () => {
  const { orgId } = routeApi.useParams();
  const search = routeApi.useSearch();
  const { page = 1, pageSize = 10 } = search;
  const pageIndex = page - 1;

  const { data: usersWithGroups } = useQuery({
    ...getOrganizationUsersOptions({
      path: {
        organizationId: Number.parseInt(orgId),
      },
      query: {
        limit: pageSize,
        offset: pageIndex * pageSize,
        sort: 'username',
      },
    }),
  });

  const table = useAppTable(
    {
      data: usersWithGroups?.data || [],
      columns: columns,
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
