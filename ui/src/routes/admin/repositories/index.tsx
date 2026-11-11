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

import { Repository } from '@/api';
import {
  getOrganizationOptions,
  getProductOptions,
  getRepositoriesOptions,
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
import { getRepositoryTypeLabel } from '@/lib/types';
import {
  filterByNameSearchParameterSchema,
  paginationSearchParameterSchema,
} from '@/schemas';

const defaultPageSize = 10;

const repositoriesPageOptions = (
  page?: number,
  pageSize?: number,
  filter?: string
) => {
  const limit = pageSize ?? defaultPageSize;

  return getRepositoriesOptions({
    query: {
      limit,
      offset: page ? (page - 1) * limit : 0,
      filter: filter || undefined,
    },
  });
};

// Only the total count of this query is used.
const allRepositoriesOptions = () =>
  getRepositoriesOptions({ query: { limit: 1 } });

const columnHelper = createAppColumnHelper<Repository>();

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

const ProductLink = ({
  organizationId,
  productId,
}: {
  organizationId: number;
  productId: number;
}) => {
  const { data: product } = useQuery({
    ...getProductOptions({ path: { productId } }),
  });

  return (
    <Link
      className='hover:underline'
      to='/organizations/$orgId/products/$productId'
      params={{
        orgId: organizationId.toString(),
        productId: productId.toString(),
      }}
    >
      {product?.name}
    </Link>
  );
};

// Component to render a single repository card in the list.
const RepositoryCard = ({ repository }: { repository: Repository }) => (
  <div className='flex flex-col gap-1'>
    <div className='flex items-center justify-between gap-2'>
      <div className='flex flex-wrap items-baseline gap-x-2'>
        <Link
          className='font-semibold wrap-anywhere text-blue-400 hover:underline'
          to='/organizations/$orgId/products/$productId/repositories/$repoId'
          params={{
            orgId: repository.organizationId.toString(),
            productId: repository.productId.toString(),
            repoId: repository.id.toString(),
          }}
        >
          {repository.url}
        </Link>
        {repository.name && (
          <span className='text-muted-foreground text-sm wrap-anywhere'>
            {repository.name}
          </span>
        )}
      </div>
      <div className='text-muted-foreground shrink-0 text-sm'>
        {getRepositoryTypeLabel(repository.type)} repository
      </div>
    </div>
    <div className='text-muted-foreground text-sm'>
      in <OrganizationLink organizationId={repository.organizationId} /> /{' '}
      <ProductLink
        organizationId={repository.organizationId}
        productId={repository.productId}
      />
    </div>
    {repository.description ? (
      <div className='text-sm wrap-anywhere'>{repository.description}</div>
    ) : (
      <div className='text-muted-foreground text-sm italic'>No description</div>
    )}
  </div>
);

const RepositoriesComponent = () => {
  const search = Route.useSearch();
  const navigate = Route.useNavigate();
  const pageIndex = search.page ? search.page - 1 : 0;
  const pageSize = search.pageSize ?? defaultPageSize;
  const urlFilter = search.filter || undefined;

  const { data: allRepositories } = useSuspenseQuery(allRepositoriesOptions());
  const { data } = useSuspenseQuery(
    repositoriesPageOptions(search.page, search.pageSize, urlFilter)
  );

  const columns = columnHelper.columns([
    columnHelper.display({
      id: 'card',
      cell: ({ row }) => <RepositoryCard repository={row.original} />,
    }),
    // A hidden column whose filter is shown in the header of the cards. The filter also matches
    // the repository name.
    columnHelper.accessor('url', {
      id: 'url',
      header: 'Name or URL',
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
        columnFilters: [{ id: 'url', value: urlFilter }],
        columnVisibility: { url: false },
      },
      manualPagination: true,
    },
    selectNoTableState
  );

  return (
    <Card>
      <CardHeader>
        <CardTitle>
          Repositories ({allRepositories.pagination.totalCount} in total
          {urlFilter && `, ${data.pagination.totalCount} matching filters`})
        </CardTitle>
        <CardDescription className='sr-only'>
          A list of the repositories of all products.
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

export const Route = createFileRoute('/admin/repositories/')({
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
      queryClient.prefetchQuery(
        repositoriesPageOptions(page, pageSize, filter)
      ),
      queryClient.prefetchQuery(allRepositoriesOptions()),
    ]);
  },
  component: RepositoriesComponent,
  pendingComponent: LoadingIndicator,
});
