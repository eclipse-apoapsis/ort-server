/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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
import z from 'zod';

import { Product } from '@/api';
import {
  getOrganizationOptions,
  getProductsOptions,
} from '@/api/@tanstack/react-query.gen';
import { DataTableCards } from '@/components/data-table-cards/data-table-cards';
import { LoadingIndicator } from '@/components/loading-indicator';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import {
  createAppColumnHelper,
  selectNoTableState,
  useAppTable,
} from '@/hooks/use-app-table';
import {
  filterByNameSearchParameterSchema,
  paginationSearchParameterSchema,
} from '@/schemas';

const defaultPageSize = 10;

const productsPageOptions = (
  page?: number,
  pageSize?: number,
  filter?: string
) => {
  const limit = pageSize ?? defaultPageSize;

  return getProductsOptions({
    query: {
      limit,
      offset: page ? (page - 1) * limit : 0,
      filter: filter || undefined,
    },
  });
};

// Only the total count of this query is used.
const allProductsOptions = getProductsOptions({ query: { limit: 1 } });

const columnHelper = createAppColumnHelper<Product>();

const OrganizationLink = ({ organizationId }: { organizationId: number }) => {
  const { data: organization } = useQuery({
    ...getOrganizationOptions({ path: { organizationId } }),
  });

  return (
    <Link
      className='hover:underline'
      to='/organizations/$orgId'
      params={{ orgId: organizationId.toString() }}
    >
      {organization?.name}
    </Link>
  );
};

// Component to render a single product card in the list.
const ProductCard = ({ product }: { product: Product }) => (
  <div className='flex flex-col gap-1'>
    <div className='flex items-center justify-between gap-2'>
      <Link
        className='font-semibold wrap-anywhere text-blue-400 hover:underline'
        to='/organizations/$orgId/products/$productId'
        params={{
          orgId: product.organizationId.toString(),
          productId: product.id.toString(),
        }}
      >
        {product.name}
      </Link>
      <div className='text-muted-foreground shrink-0 text-sm'>
        in <OrganizationLink organizationId={product.organizationId} />
      </div>
    </div>
    {product.description ? (
      <div className='text-sm wrap-anywhere'>{product.description}</div>
    ) : (
      <div className='text-muted-foreground text-sm italic'>No description</div>
    )}
  </div>
);

const ProductsComponent = () => {
  const search = Route.useSearch();
  const navigate = Route.useNavigate();
  const pageIndex = search.page ? search.page - 1 : 0;
  const pageSize = search.pageSize ?? defaultPageSize;
  const nameFilter = search.filter || undefined;

  const { data: allProducts } = useSuspenseQuery(allProductsOptions);
  const { data } = useSuspenseQuery(
    productsPageOptions(search.page, search.pageSize, nameFilter)
  );

  const columns = columnHelper.columns([
    columnHelper.display({
      id: 'card',
      cell: ({ row }) => <ProductCard product={row.original} />,
    }),
    // A hidden column whose filter is shown in the header of the cards.
    columnHelper.accessor('name', {
      id: 'name',
      header: 'Name',
      meta: {
        filter: {
          filterVariant: 'regex',
          setFilterValue: (value: string | undefined) => {
            navigate({ search: { ...search, page: 1, filter: value } });
          },
        },
      },
    }),
  ]);

  const table = useAppTable(
    {
      data: data.data,
      columns,
      pageCount: Math.ceil(data.pagination.totalCount / pageSize),
      state: {
        pagination: { pageIndex, pageSize },
        columnFilters: [{ id: 'name', value: nameFilter }],
        columnVisibility: { name: false },
      },
      manualPagination: true,
    },
    selectNoTableState
  );

  return (
    <Card>
      <CardHeader>
        <CardTitle>
          Products ({allProducts.pagination.totalCount} in total
          {nameFilter && `, ${data.pagination.totalCount} matching filters`})
        </CardTitle>
        <CardDescription className='sr-only'>
          A list of the products of all organizations.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <DataTableCards
          table={table}
          setCurrentPageOptions={(currentPage) => ({
            to: Route.to,
            search: { ...search, page: currentPage },
          })}
          setPageSizeOptions={(size) => ({
            to: Route.to,
            search: { ...search, page: 1, pageSize: size },
          })}
        />
      </CardContent>
    </Card>
  );
};

export const Route = createFileRoute('/admin/products/')({
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
    await Promise.all([
      queryClient.prefetchQuery(productsPageOptions(page, pageSize, filter)),
      queryClient.prefetchQuery(allProductsOptions),
    ]);
  },
  component: ProductsComponent,
  pendingComponent: LoadingIndicator,
});
