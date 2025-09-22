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
import { toast } from '@/lib/toast';
import {
  filterByNameSearchParameterSchema,
  paginationSearchParameterSchema,
} from '@/schemas';
import { useTablePrefsStore } from '@/store/table-prefs.store';

// Fetch the default page size to loader from the store.
const defaultPageSize = useTablePrefsStore.getState().orgPageSize;

const columnHelper = createColumnHelper<Organization>();

export const IndexPage = () => {
  const orgPageSize = useTablePrefsStore((state) => state.orgPageSize);
  const setOrgPageSize = useTablePrefsStore((state) => state.setOrgPageSize);
  const search = Route.useSearch();
  const navigate = Route.useNavigate();

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
  });

  const columns = useMemo(
    () => [
      columnHelper.accessor('name', {
        id: 'organization',
        header: 'Organizations',
        cell: ({ row }) => (
          <>
            <Link
              className='block font-semibold text-blue-400 hover:underline'
              to={`/organizations/$orgId`}
              params={{ orgId: row.original.id.toString() }}
            >
              {row.original.name}
            </Link>

            <div className='text-muted-foreground hidden text-sm md:inline'>
              {row.original.description}
            </div>
          </>
        ),
        meta: {
          filter: {
            filterVariant: 'text',
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
    toast.error('Unable to load data', {
      description: <ToastError error={error} />,
      duration: Infinity,
      cancel: {
        label: 'Dismiss',
        onClick: () => {},
      },
    });
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
              <Button asChild size='sm' className='ml-auto gap-1'>
                <Link to='/create-organization'>
                  Add organization
                  <PlusIcon className='h-4 w-4' />
                </Link>
              </Button>
            </TooltipTrigger>
            <TooltipContent>
              Add an organization for managing products
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

export const Route = createFileRoute('/')({
  validateSearch: z.object({
    ...paginationSearchParameterSchema.shape,
    ...filterByNameSearchParameterSchema.shape,
  }),
  loaderDeps: ({ search: { page, pageSize } }) => ({ page, pageSize }),
  loader: async ({ context: { queryClient }, deps: { page, pageSize } }) => {
    queryClient.prefetchQuery({
      ...getOrganizationsOptions({
        query: {
          limit: pageSize || defaultPageSize,
          offset: page ? (page - 1) * (pageSize || defaultPageSize) : 0,
        },
      }),
    });
  },
  component: IndexPage,
  pendingComponent: LoadingIndicator,
});
