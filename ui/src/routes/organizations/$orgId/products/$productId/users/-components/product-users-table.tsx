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

import { useQueryClient } from '@tanstack/react-query';
import { getRouteApi } from '@tanstack/react-router';
import {
  createColumnHelper,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table';
import { Eye, FileOutput, Pen, Shield } from 'lucide-react';

import {
  useProductsServiceDeleteApiV1ProductsByProductIdGroupsByGroupId,
  useProductsServiceGetApiV1ProductsByProductIdUsers,
  useProductsServiceGetApiV1ProductsByProductIdUsersKey,
  useProductsServicePutApiV1ProductsByProductIdGroupsByGroupId,
} from '@/api/queries';
import { ApiError, UserWithGroups } from '@/api/requests';
import { DataTable } from '@/components/data-table/data-table.tsx';
import { DeleteDialog } from '@/components/delete-dialog.tsx';
import { DeleteIconButton } from '@/components/delete-icon-button.tsx';
import { ToastError } from '@/components/toast-error.tsx';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { UserGroupRowActions } from '@/components/ui/user-group-row-actions.tsx';
import { useUser } from '@/hooks/use-user.ts';
import { toast } from '@/lib/toast.ts';

const columnHelper = createColumnHelper<UserWithGroups>();

const columns = [
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
    header: 'Group',
    cell: ({ row }) => {
      const groups = row.original.groups;
      let IconComponent;
      let effectiveGroup = '';
      if (groups.includes('ADMINS')) {
        IconComponent = Shield;
        effectiveGroup = 'ADMINS';
      } else if (groups.includes('WRITERS')) {
        IconComponent = Pen;
        effectiveGroup = 'WRITERS';
      } else if (groups.includes('READERS')) {
        IconComponent = Eye;
        effectiveGroup = 'READERS';
      } else {
        return <>{groups.join(' ')}</>;
      }

      return (
        <Tooltip>
          <TooltipTrigger asChild>
            <IconComponent size={16} />
          </TooltipTrigger>
          <TooltipContent>{effectiveGroup}</TooltipContent>
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
      const productId = Number.parseInt(params.productId);

      const { mutateAsync: joinGroup, isPending: isJoinGroupPending } =
        useProductsServicePutApiV1ProductsByProductIdGroupsByGroupId({
          onSuccess(_response, parameters) {
            queryClient.invalidateQueries({
              queryKey: [useProductsServiceGetApiV1ProductsByProductIdUsersKey],
            });
            toast.info('Join Group', {
              description: `User "${row.original.user.username}" joined group ${parameters.groupId} successfully.`,
            });
          },
          onError(error: ApiError) {
            toast.error(error.message, {
              description: <ToastError error={error} />,
              duration: Infinity,
              cancel: {
                label: 'Dismiss',
                onClick: () => {},
              },
            });
          },
        });

      const { mutateAsync: leaveGroup, isPending: isLeaveGroupPending } =
        useProductsServiceDeleteApiV1ProductsByProductIdGroupsByGroupId({
          onSuccess(_response, parameters) {
            // Intentionally, no queryClient.invalidateQueries() here. This is done after joining the new group.
            toast.info('Leave Group', {
              description: `User "${row.original.user.username}" left group ${parameters.groupId} successfully.`,
            });
          },
          onError(error: ApiError) {
            toast.error(error.message, {
              description: <ToastError error={error} />,
              duration: Infinity,
              cancel: {
                label: 'Dismiss',
                onClick: () => {},
              },
            });
          },
        });

      // Leave all groups except the one to join.
      async function leaveOtherGroups(joinGroupId: string) {
        const groups = row.original.groups;
        const otherGroups = groups.filter((group) => group !== joinGroupId);

        for (const group of otherGroups) {
          await leaveGroup({
            productId: productId,
            groupId: group,
            requestBody: {
              username: row.original.user.username,
            },
          });
        }
      }

      async function joinAdminsGroup() {
        await leaveOtherGroups('ADMINS');
        await joinGroup({
          productId: productId,
          groupId: 'ADMINS',
          requestBody: {
            username: row.original.user.username,
          },
        });
      }

      async function joinWritersGroup() {
        await leaveOtherGroups('WRITERS');
        await joinGroup({
          productId: productId,
          groupId: 'WRITERS',
          requestBody: {
            username: row.original.user.username,
          },
        });
      }

      async function joinReadersGroup() {
        await leaveOtherGroups('READERS');
        await joinGroup({
          productId: productId,
          groupId: 'READERS',
          requestBody: {
            username: row.original.user.username,
          },
        });
      }

      // Remove the user from the product
      // This is identical to removing the user from all groups.
      async function removeFromProduct() {
        try {
          await Promise.all(
            row.original.groups.map((group) =>
              leaveGroup({
                productId: productId,
                groupId: group,
                requestBody: {
                  username: row.original.user.username,
                },
              })
            )
          );
          // Upon successful removal of the user, invalidate the users query
          // to refresh the data in the table.
          queryClient.invalidateQueries({
            queryKey: [useProductsServiceGetApiV1ProductsByProductIdUsersKey],
          });
          toast.info('Remove User from Product', {
            description: `User "${row.original.user.username}" removed from the product successfully.`,
          });
        } catch (error) {
          toast.error('Failed to remove the user from product', {
            description: <ToastError error={error as ApiError} />,
            duration: Infinity,
            cancel: {
              label: 'Dismiss',
              onClick: () => {},
            },
          });
        }
      }

      return row.original.user.username !== useUser().username ? (
        <div className='flex gap-2'>
          <Tooltip delayDuration={300}>
            <TooltipTrigger asChild>
              <UserGroupRowActions
                row={row}
                onJoinAdminsGroup={joinAdminsGroup}
                onJoinWritersGroup={joinWritersGroup}
                onJoinReadersGroup={joinReadersGroup}
                disabled={isJoinGroupPending || isLeaveGroupPending}
              />
            </TooltipTrigger>
            <TooltipContent>Join group</TooltipContent>
          </Tooltip>

          <DeleteDialog
            tooltip='Remove user from this product'
            title='Confirm removal of the user from this product'
            thingName='user'
            thingId={row.original.user.username}
            itemName='product'
            uiComponent={
              <DeleteIconButton
                icon={<FileOutput size={16} />}
                disabled={isLeaveGroupPending}
                srDescription='Remove user from this product'
              />
            }
            onDelete={() => removeFromProduct()}
          />
        </div>
      ) : (
        <div>
          <DeleteIconButton icon={<FileOutput size={16} />} disabled={true} />
        </div>
      );
    },
  }),
];

const routeApi = getRouteApi('/organizations/$orgId/products/$productId/users');

export const ProductUsersTable = () => {
  const { productId } = routeApi.useParams();
  const search = routeApi.useSearch();
  const { page = 1, pageSize = 10 } = search;
  const pageIndex = page - 1;

  const { data: usersWithGroups } =
    useProductsServiceGetApiV1ProductsByProductIdUsers({
      productId: Number.parseInt(productId),
      limit: pageSize,
      offset: pageIndex * pageSize,
      sort: 'username',
    });

  const table = useReactTable({
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
    getCoreRowModel: getCoreRowModel(),
  });

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
