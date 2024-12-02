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
  createColumnHelper,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table';
import { Boxes, Bug, EditIcon, Scale, ShieldQuestion } from 'lucide-react';
import { Suspense } from 'react';

import {
  useProductsServiceDeleteProductById,
  useProductsServiceGetProductById,
  useRepositoriesServiceGetRepositoriesByProductId,
} from '@/api/queries';
import {
  prefetchUseProductsServiceGetProductById,
  prefetchUseRepositoriesServiceGetRepositoriesByProductId,
} from '@/api/queries/prefetch';
import { ApiError, Repository } from '@/api/requests';
import { DataTable } from '@/components/data-table/data-table';
import { DeleteDialog } from '@/components/delete-dialog';
import { DeleteIconButton } from '@/components/delete-icon-button';
import { LoadingIndicator } from '@/components/loading-indicator';
import { StatisticsCard } from '@/components/statistics-card';
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
  paginationSearchParameterSchema,
  sortingSearchParameterSchema,
} from '@/schemas';
import { LastJobStatus } from './-components/last-job-status';
import { LastRunDate } from './-components/last-run-date';
import { LastRunStatus } from './-components/last-run-status';
import { ProductIssuesStatisticsCard } from './-components/product-issues-statistics-card';
import { ProductPackagesStatisticsCard } from './-components/product-packages-statistics-card';
import { ProductRepositoriesStatisticsCard } from './-components/product-repositories-statistics-card';
import { ProductViolationsStatisticsCard } from './-components/product-violations-statistics-card';
import { ProductVulnerabilitiesStatisticsCard } from './-components/product-vulnerabilities-statistics-card';
import { TotalRuns } from './-components/total-runs';

const defaultPageSize = 5;

const columnHelper = createColumnHelper<Repository>();

const columns = [
  columnHelper.accessor(
    ({ url, type }) => {
      return url + type;
    },
    {
      id: 'repository',
      header: 'Repositories',
      cell: ({ row }) => (
        <>
          <Link
            className='block font-semibold text-blue-400 hover:underline'
            to={
              '/organizations/$orgId/products/$productId/repositories/$repoId'
            }
            params={{
              orgId: row.original.organizationId.toString(),
              productId: row.original.productId.toString(),
              repoId: row.original.id.toString(),
            }}
          >
            {row.original.url}
          </Link>
          <div className='text-sm text-muted-foreground md:inline'>
            {row.original.type}
          </div>
        </>
      ),
    }
  ),
  columnHelper.display({
    id: 'runs',
    header: 'Runs',
    size: 60,
    cell: ({ row }) => (
      <Link
        to='/organizations/$orgId/products/$productId/repositories/$repoId/runs'
        params={{
          orgId: row.original.organizationId.toString(),
          productId: row.original.productId.toString(),
          repoId: row.original.id.toString(),
        }}
        className='font-semibold text-blue-400 hover:underline'
      >
        <TotalRuns repoId={row.original.id} />
      </Link>
    ),
  }),
  columnHelper.display({
    id: 'runStatus',
    header: 'Last Run Status',
    cell: ({ row }) => <LastRunStatus repoId={row.original.id} />,
  }),
  columnHelper.display({
    id: 'lastRunDate',
    header: 'Last Run Date',
    cell: ({ row }) => <LastRunDate repoId={row.original.id} />,
  }),
  columnHelper.display({
    id: 'jobStatus',
    header: 'Last Job Status',
    cell: ({ row }) => <LastJobStatus repoId={row.original.id} />,
  }),
];

const ProductComponent = () => {
  const params = Route.useParams();
  const search = Route.useSearch();
  const pageIndex = search.page ? search.page - 1 : 0;
  const pageSize = search.pageSize ? search.pageSize : defaultPageSize;
  const navigate = Route.useNavigate();

  const {
    data: product,
    error: prodError,
    isPending: prodIsPending,
    isError: prodIsError,
  } = useProductsServiceGetProductById({
    productId: Number.parseInt(params.productId),
  });

  const {
    data: repositories,
    error: reposError,
    isPending: reposIsPending,
    isError: reposIsError,
  } = useRepositoriesServiceGetRepositoriesByProductId({
    productId: Number.parseInt(params.productId),
    limit: pageSize,
    offset: pageIndex * pageSize,
  });

  const { mutateAsync: deleteProduct } = useProductsServiceDeleteProductById({
    onSuccess() {
      toast.info('Delete Product', {
        description: `Product "${product?.name}" deleted successfully.`,
      });
      navigate({
        to: '/organizations/$orgId',
        params: { orgId: params.orgId },
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

  const table = useReactTable({
    data: repositories?.data || [],
    columns,
    pageCount: Math.ceil((repositories?.pagination.totalCount ?? 0) / pageSize),
    state: {
      pagination: {
        pageIndex,
        pageSize,
      },
    },
    getCoreRowModel: getCoreRowModel(),
    manualPagination: true,
  });

  async function handleDelete() {
    await deleteProduct({
      productId: Number.parseInt(params.productId),
    });
  }

  if (prodIsPending || reposIsPending) {
    return <LoadingIndicator />;
  }

  if (prodIsError || reposIsError) {
    toast.error('Unable to load data', {
      description: <ToastError error={prodError || reposError} />,
      duration: Infinity,
      cancel: {
        label: 'Dismiss',
        onClick: () => {},
      },
    });
    return;
  }

  return (
    <div className='flex flex-col gap-2'>
      <div className='grid grid-cols-4 gap-2'>
        <Card className='col-span-2'>
          <CardHeader>
            <CardTitle className='flex flex-row justify-between'>
              <div className='flex items-stretch'>
                <div className='flex items-center pb-1'>{product.name}</div>
                <Tooltip>
                  <TooltipTrigger>
                    <Button
                      asChild
                      size='sm'
                      variant='outline'
                      className='ml-2 px-2'
                    >
                      <Link
                        to='/organizations/$orgId/products/$productId/edit'
                        params={{
                          orgId: params.orgId,
                          productId: params.productId,
                        }}
                      >
                        <EditIcon className='h-4 w-4' />
                      </Link>
                    </Button>
                  </TooltipTrigger>
                  <TooltipContent>Edit this product</TooltipContent>
                </Tooltip>
              </div>
              <DeleteDialog
                description={
                  <>
                    Are you sure you want to delete the product{' '}
                    <span className='font-bold'>{product.name}</span>?
                  </>
                }
                confirmationText={product.name}
                onDelete={handleDelete}
                trigger={<DeleteIconButton />}
              />
            </CardTitle>
            <CardDescription>{product.description}</CardDescription>
          </CardHeader>
        </Card>
        <ProductRepositoriesStatisticsCard
          className='col-span-2'
          orgId={params.orgId}
          productId={product.id.toString()}
        />
      </div>
      <div className='grid grid-cols-4 gap-2'>
        <Link
          to='/organizations/$orgId/products/$productId/vulnerabilities'
          params={{
            orgId: params.orgId,
            productId: params.productId,
          }}
          search={{
            sortBy: { id: 'rating', desc: true },
          }}
        >
          <Suspense
            fallback={
              <StatisticsCard
                title='Vulnerabilities'
                icon={() => (
                  <ShieldQuestion className='h-4 w-4 text-orange-500' />
                )}
                value={<LoadingIndicator />}
                className='h-full hover:bg-muted/50'
              />
            }
          >
            <ProductVulnerabilitiesStatisticsCard productId={product.id} />
          </Suspense>
        </Link>
        <Suspense
          fallback={
            <StatisticsCard
              title='Issues'
              icon={() => <Bug className='h-4 w-4 text-orange-500' />}
              value={<LoadingIndicator />}
              className='h-full hover:bg-muted/50'
            />
          }
        >
          <ProductIssuesStatisticsCard productId={product.id} />
        </Suspense>
        <Suspense
          fallback={
            <StatisticsCard
              title='Rule Violations'
              icon={() => <Scale className='h-4 w-4 text-orange-500' />}
              value={<LoadingIndicator />}
              className='h-full hover:bg-muted/50'
            />
          }
        >
          <ProductViolationsStatisticsCard productId={product.id} />
        </Suspense>
        <Suspense
          fallback={
            <StatisticsCard
              title='Packages'
              icon={() => <Boxes className='h-4 w-4 text-orange-500' />}
              value={<LoadingIndicator />}
              className='h-full hover:bg-muted/50'
            />
          }
        >
          <ProductPackagesStatisticsCard productId={product.id} />
        </Suspense>
      </div>
      <Card>
        <CardContent className='my-4'>
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
      </Card>
    </div>
  );
};

export const Route = createFileRoute(
  '/organizations/$orgId/products/$productId/'
)({
  validateSearch: paginationSearchParameterSchema.merge(
    sortingSearchParameterSchema
  ),
  loader: async ({ context, params }) => {
    await Promise.allSettled([
      prefetchUseProductsServiceGetProductById(context.queryClient, {
        productId: Number.parseInt(params.productId),
      }),
      prefetchUseRepositoriesServiceGetRepositoriesByProductId(
        context.queryClient,
        {
          productId: Number.parseInt(params.productId),
        }
      ),
    ]);
  },
  component: ProductComponent,
  pendingComponent: LoadingIndicator,
});
