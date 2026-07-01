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

import { useQuery, useSuspenseQuery } from '@tanstack/react-query';
import { createFileRoute, Link } from '@tanstack/react-router';
import {
  createColumnHelper,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table';
import { PlusIcon } from 'lucide-react';
import { useMemo } from 'react';
import z from 'zod';

import { Organization } from '@/api';
import { getOrganizationsOptions } from '@/api/@tanstack/react-query.gen';
import { DataTable } from '@/components/data-table/data-table';
import { OrganizationFavoriteButton } from '@/components/favorite-button';
import { LoadingIndicator } from '@/components/loading-indicator';
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
import { useIsSuperuser } from '@/hooks/use-authorization';
import { routePrefetchStaleTime } from '@/lib/query-client';
import { toastError } from '@/lib/toast';
import {
  filterByNameSearchParameterSchema,
  paginationSearchParameterSchema,
} from '@/schemas';
import { useTablePrefsStore } from '@/store/table-prefs.store';

const columnHelper = createColumnHelper<Organization>();

export const OrganizationsPage = () => {
  const orgPageSize = useTablePrefsStore((state) => state.orgPageSize);
  const setOrgPageSize = useTablePrefsStore((state) => state.setOrgPageSize);
  const search = Route.useSearch();
  const navigate = Route.useNavigate();
  const { isSuperuser } = useIsSuperuser();

  const pageIndex = useMemo(
    () => (search.page ? search.page - 1 : 0),
    [search.page]
  );
  const pageSize = useMemo(
    () => (search.pageSize ? search.pageSize : orgPageSize),
    [search.pageSize, orgPageSize]
  );
  const nameFilter = useMemo(
    () => (search.filter ? search.filter : undefined),
    [search.filter]
  );

  const { data: totalOrganizations } = useSuspenseQuery({
    ...getOrganizationsOptions({
      query: { limit: 1 },
    }),
    staleTime: routePrefetchStaleTime,
  });

  const {
    data: organizations,
    isPending,
    isError,
    error,
  } = useQuery({
    ...getOrganizationsOptions({
      query: {
        limit: pageSize,
        offset: pageIndex * pageSize,
        filter: nameFilter,
      },
    }),
    staleTime: routePrefetchStaleTime,
  });

  const columns = useMemo(
    () => [
      columnHelper.accessor('name', {
        id: 'organization',
        header: 'Organizations',
        cell: ({ row }) => (
          <>
            <div className='flex items-center gap-1.5'>
              <Link
                className='font-semibold text-blue-400 hover:underline'
                to={`/organizations/$orgId`}
                params={{ orgId: row.original.id.toString() }}
              >
                {row.original.name}
              </Link>
              <OrganizationFavoriteButton
                organization={row.original}
                size='xs'
                variant='ghost'
                className='size-6 p-0'
              />
            </div>

            <div className='text-muted-foreground hidden text-sm md:inline'>
              {row.original.description}
            </div>
          </>
        ),
        meta: {
          filter: {
            filterVariant: 'regex',
            setFilterValue: (value: string | undefined) => {
              navigate({
                search: { ...search, page: 1, filter: value },
              });
            },
          },
        },
      }),
    ],
    [navigate, search]
  );

  const table = useReactTable({
    data: organizations?.data || [],
    columns,
    pageCount: Math.ceil(
      (organizations?.pagination.totalCount ?? 0) / pageSize
    ),
    state: {
      pagination: {
        pageIndex,
        pageSize,
      },
      columnFilters: [{ id: 'organization', value: nameFilter }],
    },
    getCoreRowModel: getCoreRowModel(),
    manualPagination: true,
  });

  if (isPending) {
    return <LoadingIndicator />;
  }

  if (isError) {
    toastError('Unable to load data', error);
    return;
  }

  const filtersInUse =
    totalOrganizations.pagination.totalCount !==
    organizations.pagination.totalCount;
  const matching = `, ${organizations.pagination.totalCount} matching filters`;

  return (
    <Card className='mx-auto w-full max-w-4xl'>
      <CardHeader>
        <CardTitle>
          Organizations ({totalOrganizations.pagination.totalCount} in total
          {filtersInUse && matching})
        </CardTitle>
        <CardDescription>
          Browse your organizations or create a new one
        </CardDescription>
        <div className='py-2'>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button
                asChild
                size='sm'
                className='ml-auto gap-1'
                disabled={isSuperuser === false}
              >
                <Link to='/create-organization'>
                  Add organization
                  <PlusIcon className='h-4 w-4' />
                </Link>
              </Button>
            </TooltipTrigger>
            <TooltipContent>
              {isSuperuser !== false
                ? 'Add an organization for managing products'
                : 'Insufficient permissions.'}
            </TooltipContent>
          </Tooltip>
        </div>
      </CardHeader>
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
            // Persist the user preference for page size to local storage.
            setOrgPageSize(size);
            return {
              to: Route.to,
              search: { ...search, page: 1, pageSize: size },
            };
          }}
        />
      </CardContent>
    </Card>
  );
};

export const Route = createFileRoute('/organizations/')({
  validateSearch: z.object({
    ...paginationSearchParameterSchema.shape,
    ...filterByNameSearchParameterSchema.shape,
  }),
  loaderDeps: ({ search: { page, pageSize, filter } }) => ({
    page,
    pageSize,
    filter,
  }),
  loader: async ({
    context: { queryClient },
    deps: { page, pageSize, filter },
  }) => {
    // Read the default page size from the store at loader time so the
    // prefetched query key matches the component, which reads the same
    // (live) preference. A module-level snapshot would go stale after the
    // user changes their page-size preference during the session.
    const limit = pageSize || useTablePrefsStore.getState().orgPageSize;

    await Promise.all([
      queryClient.prefetchQuery({
        ...getOrganizationsOptions({
          query: {
            limit,
            offset: page ? (page - 1) * limit : 0,
            filter: filter || undefined,
          },
        }),
        staleTime: routePrefetchStaleTime,
      }),
      queryClient.prefetchQuery({
        ...getOrganizationsOptions({
          query: { limit: 1 },
        }),
        staleTime: routePrefetchStaleTime,
      }),
    ]);
  },
  component: OrganizationsPage,
  pendingComponent: LoadingIndicator,
});
