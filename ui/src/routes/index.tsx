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

import { createFileRoute, Link } from '@tanstack/react-router';
import {
  ColumnDef,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table';
import { PlusIcon } from 'lucide-react';

import { useOrganizationsServiceGetApiV1Organizations } from '@/api/queries';
import { prefetchUseOrganizationsServiceGetApiV1Organizations } from '@/api/queries/prefetch';
import { Organization } from '@/api/requests';
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
import { paginationSearchParameterSchema } from '@/schemas';
import { useTablePrefsStore } from '@/store/table-prefs.store';

// Fetch the default page size to loader from the store.
const defaultPageSize = useTablePrefsStore.getState().orgPageSize;

const columns: ColumnDef<Organization>[] = [
  {
    accessorKey: 'organization',
    header: () => <div>Organizations</div>,
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
    enableColumnFilter: false,
  },
];

export const IndexPage = () => {
  const orgPageSize = useTablePrefsStore((state) => state.orgPageSize);
  const setOrgPageSize = useTablePrefsStore((state) => state.setOrgPageSize);
  const search = Route.useSearch();
  const pageIndex = search.page ? search.page - 1 : 0;
  const pageSize = search.pageSize ? search.pageSize : orgPageSize;

  const {
    data: organizations,
    isPending,
    isError,
    error,
  } = useOrganizationsServiceGetApiV1Organizations({
    limit: pageSize,
    offset: pageIndex * pageSize,
  });

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

  return (
    <Card className='mx-auto w-full max-w-4xl'>
      <CardHeader>
        <CardTitle>Organizations</CardTitle>
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
  validateSearch: paginationSearchParameterSchema,
  loaderDeps: ({ search: { page, pageSize } }) => ({ page, pageSize }),
  loader: async ({ context, deps: { page, pageSize } }) => {
    prefetchUseOrganizationsServiceGetApiV1Organizations(context.queryClient, {
      limit: pageSize || defaultPageSize,
      offset: page ? (page - 1) * (pageSize || defaultPageSize) : 0,
    });
  },
  component: IndexPage,
  pendingComponent: LoadingIndicator,
});
