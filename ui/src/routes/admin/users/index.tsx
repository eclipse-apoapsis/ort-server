/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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
  useQueryClient,
  useSuspenseQuery,
} from '@tanstack/react-query';
import { createFileRoute, Link } from '@tanstack/react-router';
import {
  createColumnHelper,
  getCoreRowModel,
  getPaginationRowModel,
  useReactTable,
} from '@tanstack/react-table';
import { ShieldCheck, ShieldMinus, ShieldPlus, UserPlus } from 'lucide-react';

import { UserWithSuperuserStatus } from '@/api';
import {
  deleteSuperuserMutation,
  deleteUserMutation,
  getUsersOptions,
  getUsersQueryKey,
  putSuperuserMutation,
} from '@/api/@tanstack/react-query.gen';
import { DataTable } from '@/components/data-table/data-table';
import { DeleteDialog } from '@/components/delete-dialog';
import { DeleteIconButton } from '@/components/delete-icon-button';
import { LoadingIndicator } from '@/components/loading-indicator';
import { ToastError } from '@/components/toast-error';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { ApiError } from '@/lib/api-error';
import { toast } from '@/lib/toast';
import { paginationSearchParameterSchema } from '@/schemas';

const defaultPageSize = 10;

const columnHelper = createColumnHelper<UserWithSuperuserStatus>();

const columns = [
  columnHelper.accessor('user.username', {
    header: 'Username',
    cell: ({ row }) => (
      <div className='overflow-hidden text-ellipsis'>
        {row.original.user.username}
      </div>
    ),
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
    cell: ({ row }) => (
      <div className='overflow-hidden text-ellipsis'>
        {row.original.user.email}
      </div>
    ),
  }),
  columnHelper.accessor('isSuperuser', {
    header: 'Superuser',
    cell: ({ row }) => (
      <>
        {row.original.isSuperuser ? <ShieldCheck className='h-4 w-4' /> : null}
      </>
    ),
  }),
  columnHelper.display({
    id: 'actions',
    header: () => <div className='text-right'>Actions</div>,
    size: 80,
    cell: function CellComponent({ row }) {
      const queryClient = useQueryClient();

      const { mutateAsync: putSuperuser } = useMutation({
        ...putSuperuserMutation(),
        onSuccess() {
          toast.info('Add superuser role', {
            description: `Superuser role added successfully to user "${row.original.user.username}".`,
          });
          queryClient.invalidateQueries({
            queryKey: getUsersQueryKey(),
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

      const { mutateAsync: deleteSuperuser } = useMutation({
        ...deleteSuperuserMutation(),
        onSuccess() {
          toast.info('Remove superuser role', {
            description: `Superuser role removed successfully from user "${row.original.user.username}".`,
          });
          queryClient.invalidateQueries({
            queryKey: getUsersQueryKey(),
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

      const { mutateAsync: delUser } = useMutation({
        ...deleteUserMutation(),
        onSuccess() {
          toast.info('Delete User', {
            description: `User "${row.original.user.username}" deleted successfully.`,
          });
          queryClient.invalidateQueries({
            queryKey: getUsersQueryKey(),
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

      return (
        <div className='flex gap-2'>
          {row.original.isSuperuser ? (
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  size='sm'
                  variant='outline'
                  className='h-8 px-2'
                  onClick={() =>
                    deleteSuperuser({
                      path: { username: row.original.user.username },
                    })
                  }
                >
                  <span className='sr-only'>Remove superuser</span>
                  <ShieldMinus className='h-4 w-4' />
                </Button>
              </TooltipTrigger>
              <TooltipContent>Remove superuser role</TooltipContent>
            </Tooltip>
          ) : (
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  size='sm'
                  variant='outline'
                  className='h-8 px-2'
                  onClick={() =>
                    putSuperuser({
                      path: { username: row.original.user.username },
                    })
                  }
                >
                  <span className='sr-only'>Make superuser</span>
                  <ShieldPlus className='h-4 w-4' />
                </Button>
              </TooltipTrigger>
              <TooltipContent>Add superuser role</TooltipContent>
            </Tooltip>
          )}

          <DeleteDialog
            thingName={'user'}
            thingId={row.original.user.username}
            uiComponent={<DeleteIconButton />}
            onDelete={() =>
              delUser({ query: { username: row.original.user.username } })
            }
          />
        </div>
      );
    },
  }),
];

const Users = () => {
  const search = Route.useSearch();
  const pageIndex = search.page ? search.page - 1 : 0;
  const pageSize = search.pageSize ? search.pageSize : defaultPageSize;

  const { data: users } = useSuspenseQuery({ ...getUsersOptions() });

  const table = useReactTable({
    data: users || [],
    columns,

    state: {
      pagination: {
        pageIndex,
        pageSize,
      },
    },
    getCoreRowModel: getCoreRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
  });

  return (
    <Card className='h-fit'>
      <CardHeader>
        <CardTitle>Users</CardTitle>
        <CardDescription>
          These are all current users of the server. By clicking the delete
          button in the action column you can delete users, and a written
          confirmation is required to prevent accidental deletions.
        </CardDescription>
        <div className='py-2'>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button asChild size='sm' className='ml-auto gap-1'>
                <Link to='/admin/users/create-user'>
                  Create user
                  <UserPlus className='h-4 w-4' />
                </Link>
              </Button>
            </TooltipTrigger>
            <TooltipContent>Create a new user account.</TooltipContent>
          </Tooltip>
        </div>
      </CardHeader>
      <CardContent>
        <CardContent>
          <DataTable
            table={table}
            setCurrentPageOptions={(currentPage) => {
              return {
                to: Route.to,
                search: { ...search, page: currentPage },
              };
            }}
            setPageSizeOptions={(size) => {
              return {
                to: Route.to,
                search: { ...search, page: 1, pageSize: size },
              };
            }}
          />
        </CardContent>
      </CardContent>
    </Card>
  );
};

export const Route = createFileRoute('/admin/users/')({
  validateSearch: paginationSearchParameterSchema,
  loader: async ({ context: { queryClient } }) => {
    queryClient.prefetchQuery({
      ...getUsersOptions(),
    });
  },
  component: Users,
  pendingComponent: LoadingIndicator,
});
