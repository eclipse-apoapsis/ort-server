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

import { getRouteApi, Link } from '@tanstack/react-router';
import {
  createColumnHelper,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table';
import { Loader2 } from 'lucide-react';

import {
  useProductsServiceGetApiV1OrganizationsByOrganizationIdProducts,
  useRepositoriesServiceGetApiV1ProductsByProductIdRepositories,
} from '@/api/queries';
import { PagedResponse_Product, Product } from '@/api/requests';
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

const columns = [
  columnHelper.accessor(
    ({ name, description }) => {
      return name + description;
    },
    {
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
          <div className='text-sm text-muted-foreground md:inline'>
            {row.original.description}
          </div>
        </>
      ),
      enableColumnFilter: false,
    }
  ),
  columnHelper.display({
    id: 'runs',
    header: 'Runs',
    size: 60,
    cell: function CellComponent({ row }) {
      const { data, isPending, isError } =
        useRepositoriesServiceGetApiV1ProductsByProductIdRepositories({
          productId: row.original.id,
          limit: 1,
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
      const { data, isPending, isError } =
        useRepositoriesServiceGetApiV1ProductsByProductIdRepositories({
          productId: row.original.id,
          limit: 1,
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
        return <span>Contains {data.pagination.totalCount} repositories</span>;
    },
    enableColumnFilter: false,
  }),
  columnHelper.display({
    id: 'lastRunDate',
    header: 'Last Run Date',
    cell: function CellComponent({ row }) {
      const { data, isPending, isError } =
        useRepositoriesServiceGetApiV1ProductsByProductIdRepositories({
          productId: row.original.id,
          limit: 1,
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
      const { data, isPending, isError } =
        useRepositoriesServiceGetApiV1ProductsByProductIdRepositories({
          productId: row.original.id,
          limit: 1,
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
];

const routeApi = getRouteApi('/organizations/$orgId/');

export const OrganizationProductTable = () => {
  const prodPageSize = useTablePrefsStore((state) => state.prodPageSize);
  const params = routeApi.useParams();
  const search = routeApi.useSearch();
  const pageIndex = search.page ? search.page - 1 : 0;
  const pageSize = search.pageSize ? search.pageSize : prodPageSize;

  const {
    data: products,
    error: prodError,
    isPending: prodIsPending,
    isError: prodIsError,
  } = useProductsServiceGetApiV1OrganizationsByOrganizationIdProducts({
    organizationId: Number.parseInt(params.orgId),
    limit: pageSize,
    offset: pageIndex * pageSize,
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
    <OrganizationProductTableInner
      products={products}
      prodPageSize={prodPageSize}
    />
  );
};

const OrganizationProductTableInner = ({
  products,
  prodPageSize,
}: {
  products: PagedResponse_Product;
  prodPageSize: number;
}) => {
  const setProdPageSize = useTablePrefsStore((state) => state.setProdPageSize);
  const search = routeApi.useSearch();
  const pageIndex = search.page ? search.page - 1 : 0;
  const pageSize = search.pageSize ? search.pageSize : prodPageSize;

  const table = useReactTable({
    data: products?.data || [],
    columns,
    pageCount: Math.ceil((products?.pagination.totalCount ?? 0) / pageSize),
    state: {
      pagination: {
        pageIndex,
        pageSize,
      },
    },
    getCoreRowModel: getCoreRowModel(),
    manualPagination: true,
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
        setProdPageSize(size);
        return {
          search: { ...search, page: 1, pageSize: size },
        };
      }}
    />
  );
};
