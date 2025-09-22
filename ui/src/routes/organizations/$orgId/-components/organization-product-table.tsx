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

import { useQuery } from '@tanstack/react-query';
import { getRouteApi, Link } from '@tanstack/react-router';
import {
  createColumnHelper,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table';
import { Loader2 } from 'lucide-react';
import { useMemo } from 'react';

import { Product } from '@/api';
import {
  getOrganizationProductsOptions,
  getRepositoriesByProductIdOptions,
} from '@/api/@tanstack/react-query.gen';
import { DataTable } from '@/components/data-table/data-table';
import { LoadingIndicator } from '@/components/loading-indicator';
import { ToastError } from '@/components/toast-error';
import { toast } from '@/lib/toast';
import { useTablePrefsStore } from '@/store/table-prefs.store';
import { LastJobStatus } from '../products/$productId/-components/last-job-status';
import { LastRunDate } from '../products/$productId/-components/last-run-date';
import { LastRunStatus } from '../products/$productId/-components/last-run-status';
import { TotalRuns } from '../products/$productId/-components/total-runs';

const columnHelper = createColumnHelper<Product>();

const routeApi = getRouteApi('/organizations/$orgId/');

export const OrganizationProductTable = () => {
  const prodPageSize = useTablePrefsStore((state) => state.prodPageSize);
  const setProdPageSize = useTablePrefsStore((state) => state.setProdPageSize);
  const navigate = routeApi.useNavigate();
  const params = routeApi.useParams();
  const search = routeApi.useSearch();

  const pageIndex = useMemo(
    () => (search.page ? search.page - 1 : 0),
    [search.page]
  );
  const pageSize = useMemo(
    () => (search.pageSize ? search.pageSize : prodPageSize),
    [search.pageSize, prodPageSize]
  );
  const nameFilter = useMemo(
    () => (search.filter ? search.filter : undefined),
    [search.filter]
  );

  const {
    data: products,
    error: prodError,
    isPending: prodIsPending,
    isError: prodIsError,
  } = useQuery({
    ...getOrganizationProductsOptions({
      path: { organizationId: Number.parseInt(params.orgId) },
      query: {
        limit: pageSize,
        offset: pageIndex * pageSize,
        filter: nameFilter,
      },
    }),
  });

  const columns = useMemo(
    () => [
      columnHelper.accessor(
        ({ name, description }) => {
          return name + description;
        },
        {
          id: 'product',
          header: 'Products',
          cell: ({ row }) => (
            <>
              <Link
                className='block font-semibold text-blue-400 hover:underline'
                to={`/organizations/$orgId/products/$productId`}
                params={{
                  orgId: row.original.organizationId.toString(),
                  productId: row.original.id.toString(),
                }}
              >
                {row.original.name}
              </Link>
              <div className='text-muted-foreground text-sm md:inline'>
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
        }
      ),
      columnHelper.display({
        id: 'runs',
        header: 'Runs',
        size: 60,
        cell: function CellComponent({ row }) {
          const { data, isPending, isError } = useQuery({
            ...getRepositoriesByProductIdOptions({
              path: { productId: row.original.id },
              query: { limit: 1 },
            }),
          });

          if (isPending)
            return (
              <>
                <span className='sr-only'>Loading...</span>
                <Loader2 size={16} className='mx-3 animate-spin' />
              </>
            );

          if (isError) return <span>Error loading data.</span>;

          if (data.pagination.totalCount === 1 && data.data[0])
            return <TotalRuns repoId={data.data[0].id} />;
          else return <span>-</span>;
        },
        enableColumnFilter: false,
      }),
      columnHelper.display({
        id: 'runStatus',
        header: 'Last Run Status',
        cell: function CellComponent({ row }) {
          const { data, isPending, isError } = useQuery({
            ...getRepositoriesByProductIdOptions({
              path: { productId: row.original.id },
              query: { limit: 1 },
            }),
          });

          if (isPending)
            return (
              <>
                <span className='sr-only'>Loading...</span>
                <Loader2 size={16} className='mx-3 animate-spin' />
              </>
            );

          if (isError) return <span>Error loading data.</span>;

          if (data.pagination.totalCount === 1 && data.data[0])
            return <LastRunStatus repoId={data.data[0].id} />;
          else
            return (
              <span>Contains {data.pagination.totalCount} repositories</span>
            );
        },
        enableColumnFilter: false,
      }),
      columnHelper.display({
        id: 'lastRunDate',
        header: 'Last Run Date',
        cell: function CellComponent({ row }) {
          const { data, isPending, isError } = useQuery({
            ...getRepositoriesByProductIdOptions({
              path: { productId: row.original.id },
              query: { limit: 1 },
            }),
          });

          if (isPending)
            return (
              <>
                <span className='sr-only'>Loading...</span>
                <Loader2 size={16} className='mx-3 animate-spin' />
              </>
            );

          if (isError) return <span>Error loading data.</span>;

          if (data.pagination.totalCount === 1 && data.data[0])
            return <LastRunDate repoId={data.data[0].id} />;
          else return null;
        },
        enableColumnFilter: false,
      }),
      columnHelper.display({
        id: 'jobStatus',
        header: 'Last Job Status',
        cell: function CellComponent({ row }) {
          const { data, isPending, isError } = useQuery({
            ...getRepositoriesByProductIdOptions({
              path: { productId: row.original.id },
              query: { limit: 1 },
            }),
          });

          if (isPending)
            return (
              <>
                <span className='sr-only'>Loading...</span>
                <Loader2 size={16} className='mx-3 animate-spin' />
              </>
            );

          if (isError) return <span>Error loading data.</span>;

          if (data.pagination.totalCount === 1 && data.data[0])
            return <LastJobStatus repoId={data.data[0].id} />;
          else return null;
        },
        enableColumnFilter: false,
      }),
    ],
    [navigate, search]
  );

  const table = useReactTable({
    data: products?.data || [],
    columns,
    pageCount: Math.ceil((products?.pagination.totalCount ?? 0) / pageSize),
    state: {
      pagination: {
        pageIndex,
        pageSize,
      },
      columnFilters: [{ id: 'product', value: nameFilter }],
    },
    getCoreRowModel: getCoreRowModel(),
    manualPagination: true,
  });

  if (prodIsPending) {
    return <LoadingIndicator />;
  }

  if (prodIsError) {
    toast.error('Unable to load data', {
      description: <ToastError error={prodError} />,
      duration: Infinity,
      cancel: {
        label: 'Dismiss',
        onClick: () => {},
      },
    });
    return;
  }

  return (
    <DataTable
      table={table}
      setCurrentPageOptions={(currentPage) => {
        return {
          search: { ...search, page: currentPage },
        };
      }}
      setPageSizeOptions={(size) => {
        setProdPageSize(size);
        return {
          search: { ...search, page: 1, pageSize: size },
        };
      }}
    />
  );
};
