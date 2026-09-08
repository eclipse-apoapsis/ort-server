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
  ShieldCheck,
  ShieldMinus,
  ShieldPlus,
  UserPlus,
  XCircle,
} from 'lucide-react';
import { useRef, useState } from 'react';

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
import { TooltipIfTruncated } from '@/components/tooltip-if-truncated';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import {
  convertToBackendSorting,
  EMPTY_SORTING_STATE,
  updateColumnSorting,
} from '@/helpers/handle-multisort';
import {
  createAppColumnHelper,
  selectNoTableState,
  useAppTable,
} from '@/hooks/use-app-table';
import { ApiError } from '@/lib/api-error';
import { routePrefetchStaleTime } from '@/lib/query-client';
import { toast, toastError } from '@/lib/toast';
import {
  adminUsersSearchParameterSchema,
  type AdminUsersSearchParameters,
} from '@/schemas';

const defaultPageSize = 10;

const getUsersOptionsForSearch = ({
  page,
  pageSize,
  sortBy,
  search,
}: AdminUsersSearchParameters) => {
  const requestPageSize = pageSize ?? defaultPageSize;

  return getUsersOptions({
    query: {
      limit: requestPageSize,
      offset: page ? (page - 1) * requestPageSize : 0,
      sort: convertToBackendSorting(sortBy),
      search: search || undefined,
    },
  });
};

const columnHelper = createAppColumnHelper<UserWithSuperuserStatus>();

const columns = columnHelper.columns([
  columnHelper.accessor('user.username', {
    id: 'username',
    header: 'Username',
    cell: ({ row }) => <TooltipIfTruncated text={row.original.user.username} />,
  }),
  columnHelper.accessor('user.firstName', {
    id: 'firstName',
    header: 'First name',
    cell: ({ row }) => <>{row.original.user.firstName}</>,
  }),
  columnHelper.accessor('user.lastName', {
    id: 'lastName',
    header: 'Last name',
    cell: ({ row }) => <>{row.original.user.lastName}</>,
  }),
  columnHelper.accessor('user.email', {
    id: 'email',
    header: 'Email address',
    cell: ({ row }) => (
      <TooltipIfTruncated text={row.original.user.email ?? ''} />
    ),
  }),
  columnHelper.accessor('isSuperuser', {
    header: 'Superuser',
    enableSorting: false,
    cell: ({ row }) => (
      <>
        {row.original.isSuperuser ? <ShieldCheck className='h-4 w-4' /> : null}
      </>
    ),
  }),
  columnHelper.display({
    id: 'actions',
    header: () => <div className='text-right'>Actions</div>,
    enableSorting: false,
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
          toastError(error.message, error);
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
          toastError(error.message, error);
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
          toastError(error.message, error);
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
]);

const UserSearch = ({ search }: { search: AdminUsersSearchParameters }) => {
  const committedSearch = search.search ?? '';
  const navigate = Route.useNavigate();
  const [searchInput, setSearchInput] = useState(committedSearch);
  const lastAppliedSearch = useRef(committedSearch);

  const applySearch = () => {
    const normalizedSearch = searchInput.trim();

    if (normalizedSearch === lastAppliedSearch.current) return;

    lastAppliedSearch.current = normalizedSearch;
    navigate({
      search: {
        ...search,
        page: 1,
        search: normalizedSearch || undefined,
      },
    });
  };

  const clearSearch = () => {
    setSearchInput('');

    if (lastAppliedSearch.current === '') return;

    lastAppliedSearch.current = '';
    navigate({
      search: {
        ...search,
        page: 1,
        search: undefined,
      },
    });
  };

  return (
    <form
      className='mb-4 max-w-xl'
      onSubmit={(event) => {
        event.preventDefault();
        applySearch();
      }}
    >
      <label className='mb-2 block text-sm font-medium' htmlFor='user-search'>
        Search users
      </label>
      <div className='flex gap-2'>
        <Input
          id='user-search'
          type='search'
          placeholder='Search by username, first name, last name, or email'
          value={searchInput}
          onChange={(event) => setSearchInput(event.target.value)}
          onBlur={applySearch}
        />
        <Button
          type='button'
          variant='ghost'
          className='px-2'
          onMouseDown={(event) => event.preventDefault()}
          onClick={clearSearch}
        >
          <XCircle
            className={
              searchInput.length === 0
                ? 'h-fit text-gray-400 opacity-40'
                : 'h-fit text-gray-400 opacity-100'
            }
          />
          <span className='sr-only'>Clear search</span>
        </Button>
      </div>
    </form>
  );
};

const Users = () => {
  const search = Route.useSearch();
  const pageIndex = search.page ? search.page - 1 : 0;
  const pageSize = search.pageSize ?? defaultPageSize;

  const { data: users } = useSuspenseQuery({
    ...getUsersOptionsForSearch(search),
    staleTime: routePrefetchStaleTime,
  });

  const table = useAppTable(
    {
      data: users.data,
      columns,
      pageCount: Math.ceil(users.pagination.totalCount / pageSize),
      state: {
        pagination: {
          pageIndex,
          pageSize,
        },
        sorting: search.sortBy ?? EMPTY_SORTING_STATE,
      },
      manualPagination: true,
      manualSorting: true,
    },
    selectNoTableState
  );

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
        <UserSearch key={search.search ?? ''} search={search} />
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
          setSortingOptions={(sortBy) => {
            return {
              to: Route.to,
              search: {
                ...search,
                page: 1,
                sortBy: updateColumnSorting(search.sortBy, sortBy),
              },
            };
          }}
        />
      </CardContent>
    </Card>
  );
};

export const Route = createFileRoute('/admin/users/')({
  validateSearch: adminUsersSearchParameterSchema,
  loaderDeps: ({ search: { page, pageSize, sortBy, search: searchTerm } }) => ({
    page,
    pageSize,
    sortBy,
    search: searchTerm,
  }),
  loader: async ({ context: { queryClient }, deps }) => {
    await queryClient.ensureQueryData({
      ...getUsersOptionsForSearch(deps),
      staleTime: routePrefetchStaleTime,
    });
  },
  component: Users,
  pendingComponent: LoadingIndicator,
});
