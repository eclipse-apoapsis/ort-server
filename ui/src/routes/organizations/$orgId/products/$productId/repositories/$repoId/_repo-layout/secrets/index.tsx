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
  useQuery,
  useQueryClient,
  useSuspenseQuery,
} from '@tanstack/react-query';
import { createFileRoute, Link } from '@tanstack/react-router';
import {
  CellContext,
  ColumnDef,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table';
import { EditIcon, PlusIcon } from 'lucide-react';
import z from 'zod';

import { ApiError } from '@/api/requests';
import { DataTable } from '@/components/data-table/data-table';
import { DeleteDialog } from '@/components/delete-dialog';
import { DeleteIconButton } from '@/components/delete-icon-button';
import { LoadingIndicator } from '@/components/loading-indicator';
import { ToastError } from '@/components/toast-error';
import { Button } from '@/components/ui/button';
import { buttonVariants } from '@/components/ui/button-variants';
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
import { Secret } from '@/hey-api';
import {
  deleteSecretByRepositoryIdAndNameMutation,
  getOrganizationByIdOptions,
  getProductByIdOptions,
  getRepositoryByIdOptions,
  getSecretsByOrganizationIdOptions,
  getSecretsByProductIdOptions,
  getSecretsByRepositoryIdOptions,
  getSecretsByRepositoryIdQueryKey,
} from '@/hey-api/@tanstack/react-query.gen';
import { toast } from '@/lib/toast';
import { cn } from '@/lib/utils';
import {
  orgPaginationSearchParameterSchema,
  paginationSearchParameterSchema,
  productPaginationSearchParameterSchema,
} from '@/schemas';

const defaultPageSize = 5;

const ActionCell = ({ row }: CellContext<Secret, unknown>) => {
  const params = Route.useParams();
  const queryClient = useQueryClient();

  const { data: repo } = useSuspenseQuery({
    ...getRepositoryByIdOptions({
      path: { repositoryId: Number.parseInt(params.repoId) },
    }),
  });

  const { mutateAsync: deleteSecret } = useMutation({
    ...deleteSecretByRepositoryIdAndNameMutation(),
    onSuccess() {
      toast.info('Delete Secret', {
        description: `Secret "${row.original.name}" deleted successfully.`,
      });
      queryClient.invalidateQueries({
        queryKey: getSecretsByRepositoryIdQueryKey({
          path: { repositoryId: repo.id },
        }),
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
    <div className='flex items-center justify-end gap-1'>
      <Tooltip>
        <TooltipTrigger asChild>
          <Link
            to='/organizations/$orgId/products/$productId/repositories/$repoId/secrets/$secretName/edit'
            params={{
              orgId: params.orgId,
              productId: params.productId,
              repoId: params.repoId,
              secretName: row.original.name,
            }}
            className={cn(buttonVariants({ variant: 'outline' }), 'h-8 px-2')}
          >
            <span className='sr-only'>Edit</span>
            <EditIcon size={16} />
          </Link>
        </TooltipTrigger>
        <TooltipContent>Edit this secret</TooltipContent>
      </Tooltip>
      <DeleteDialog
        thingName={'secret'}
        uiComponent={<DeleteIconButton />}
        onDelete={() =>
          deleteSecret({
            path: { repositoryId: repo.id, secretName: row.original.name },
          })
        }
      />
    </div>
  );
};

const baseColumns: ColumnDef<Secret>[] = [
  {
    accessorKey: 'name',
    header: 'Name',
    enableColumnFilter: false,
  },
  {
    accessorKey: 'description',
    header: 'Description',
    enableColumnFilter: false,
  },
];

const columns: ColumnDef<Secret>[] = [
  ...baseColumns,
  {
    id: 'actions',
    cell: ActionCell,
    enableColumnFilter: false,
  },
];

const RepositorySecrets = () => {
  const params = Route.useParams();
  const search = Route.useSearch();
  const pageIndex = search.page ? search.page - 1 : 0;
  const pageSize = search.pageSize ? search.pageSize : defaultPageSize;
  const productPageIndex = search.productPage ? search.productPage - 1 : 0;
  const productPageSize = search.productPageSize
    ? search.productPageSize
    : defaultPageSize;
  const orgPageIndex = search.orgPage ? search.orgPage - 1 : 0;
  const orgPageSize = search.orgPageSize ? search.orgPageSize : defaultPageSize;

  const {
    data: repo,
    error: repoError,
    isPending: repoIsPending,
    isError: repoIsError,
  } = useQuery({
    ...getRepositoryByIdOptions({
      path: { repositoryId: Number.parseInt(params.repoId) },
    }),
  });

  const {
    data: product,
    error: productError,
    isPending: productIsPending,
    isError: productIsError,
  } = useQuery({
    ...getProductByIdOptions({
      path: { productId: Number.parseInt(params.productId) },
    }),
  });

  const {
    data: organization,
    error: orgError,
    isPending: orgIsPending,
    isError: orgIsError,
  } = useQuery({
    ...getOrganizationByIdOptions({
      path: { organizationId: Number.parseInt(params.orgId) },
    }),
  });

  const {
    data: secrets,
    error: secretsError,
    isPending: secretsIsPending,
    isError: secretsIsError,
  } = useQuery({
    ...getSecretsByRepositoryIdOptions({
      path: { repositoryId: Number.parseInt(params.repoId) },
      query: { limit: pageSize, offset: pageIndex * pageSize },
    }),
  });

  const {
    data: productSecrets,
    error: productSecretsError,
    isPending: productSecretsIsPending,
    isError: productSecretsIsError,
  } = useQuery({
    ...getSecretsByProductIdOptions({
      path: { productId: Number.parseInt(params.productId) },
      query: {
        limit: productPageSize,
        offset: productPageIndex * productPageSize,
      },
    }),
  });

  const {
    data: orgSecrets,
    error: orgSecretsError,
    isPending: orgSecretsIsPending,
    isError: orgSecretsIsError,
  } = useQuery({
    ...getSecretsByOrganizationIdOptions({
      path: { organizationId: Number.parseInt(params.orgId) },
      query: {
        limit: orgPageSize,
        offset: orgPageIndex * orgPageSize,
      },
    }),
  });

  const table = useReactTable({
    data: secrets?.data || [],
    columns,
    pageCount: Math.ceil((secrets?.pagination.totalCount ?? 0) / pageSize),
    state: {
      pagination: {
        pageIndex,
        pageSize,
      },
    },
    getCoreRowModel: getCoreRowModel(),
    manualPagination: true,
  });

  const productTable = useReactTable({
    data: productSecrets?.data || [],
    columns: baseColumns,
    pageCount: Math.ceil(
      (productSecrets?.pagination.totalCount ?? 0) / orgPageSize
    ),
    state: {
      pagination: {
        pageIndex: productPageIndex,
        pageSize: productPageSize,
      },
    },
    getCoreRowModel: getCoreRowModel(),
    manualPagination: true,
  });

  const orgTable = useReactTable({
    data: orgSecrets?.data || [],
    columns: baseColumns,
    pageCount: Math.ceil(
      (orgSecrets?.pagination.totalCount ?? 0) / orgPageSize
    ),
    state: {
      pagination: {
        pageIndex: orgPageIndex,
        pageSize: orgPageSize,
      },
    },
    getCoreRowModel: getCoreRowModel(),
    manualPagination: true,
  });

  if (
    repoIsPending ||
    productIsPending ||
    orgIsPending ||
    secretsIsPending ||
    productSecretsIsPending ||
    orgSecretsIsPending
  ) {
    return <LoadingIndicator />;
  }

  if (
    repoIsError ||
    productIsError ||
    orgIsError ||
    secretsIsError ||
    productSecretsIsError ||
    orgSecretsIsError
  ) {
    toast.error('Unable to load data', {
      description: (
        <ToastError
          error={
            repoError ||
            productError ||
            orgError ||
            secretsError ||
            productSecretsError ||
            orgSecretsError
          }
        />
      ),
      duration: Infinity,
      cancel: {
        label: 'Dismiss',
        onClick: () => {},
      },
    });
    return;
  }

  return (
    <>
      <Card>
        <CardHeader>
          <CardTitle>Repository Secrets</CardTitle>
          <CardDescription>Manage secrets for {repo.url}.</CardDescription>
          <div className='py-2'>
            <Tooltip>
              <TooltipTrigger asChild>
                <Button asChild size='sm' className='ml-auto gap-1'>
                  <Link
                    to='/organizations/$orgId/products/$productId/repositories/$repoId/secrets/create-secret'
                    params={{
                      orgId: params.orgId,
                      productId: params.productId,
                      repoId: params.repoId,
                    }}
                  >
                    New secret
                    <PlusIcon className='h-4 w-4' />
                  </Link>
                </Button>
              </TooltipTrigger>
              <TooltipContent>
                Create a new secret for this repository
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
              return {
                to: Route.to,
                search: { ...search, page: 1, pageSize: size },
              };
            }}
          />
        </CardContent>
      </Card>
      <Card className='mt-4'>
        <CardHeader>
          <CardTitle>Product Secrets</CardTitle>
          <CardDescription>
            Inherited secrets from {product.name}.
          </CardDescription>
          <div className='py-2'>
            <Button asChild size='sm' className='ml-auto gap-1'>
              <Link
                to='/organizations/$orgId/products/$productId/secrets'
                params={{
                  orgId: params.orgId,
                  productId: params.productId,
                }}
              >
                Manage Product Secrets
              </Link>
            </Button>
          </div>
        </CardHeader>
        <CardContent>
          <DataTable
            table={productTable}
            setCurrentPageOptions={(currentPage) => {
              return {
                to: Route.to,
                search: { ...search, productPage: currentPage },
              };
            }}
            setPageSizeOptions={(size) => {
              return {
                to: Route.to,
                search: { ...search, productPage: 1, productPageSize: size },
              };
            }}
          />
        </CardContent>
      </Card>
      <Card className='mt-4'>
        <CardHeader>
          <CardTitle>Organization Secrets</CardTitle>
          <CardDescription>
            Inherited secrets from {organization.name}.
          </CardDescription>
          <div className='py-2'>
            <Button asChild size='sm' className='ml-auto gap-1'>
              <Link
                to='/organizations/$orgId/secrets'
                params={{
                  orgId: params.orgId,
                }}
              >
                Manage Organization Secrets
              </Link>
            </Button>
          </div>
        </CardHeader>
        <CardContent>
          <DataTable
            table={orgTable}
            setCurrentPageOptions={(currentPage) => {
              return {
                to: Route.to,
                search: { ...search, orgPage: currentPage },
              };
            }}
            setPageSizeOptions={(size) => {
              return {
                to: Route.to,
                search: { ...search, orgPage: 1, orgPageSize: size },
              };
            }}
          />
        </CardContent>
      </Card>
    </>
  );
};

export const Route = createFileRoute(
  '/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/secrets/'
)({
  validateSearch: z.object({
    ...paginationSearchParameterSchema.shape,
    ...productPaginationSearchParameterSchema.shape,
    ...orgPaginationSearchParameterSchema.shape,
  }),
  loaderDeps: ({
    search: {
      page,
      pageSize,
      productPage,
      productPageSize,
      orgPage,
      orgPageSize,
    },
  }) => ({
    page,
    pageSize,
    productPage,
    productPageSize,
    orgPage,
    orgPageSize,
  }),
  loader: async ({
    context: { queryClient },
    params,
    deps: {
      page,
      pageSize,
      productPage,
      productPageSize,
      orgPage,
      orgPageSize,
    },
  }) => {
    await Promise.allSettled([
      queryClient.prefetchQuery({
        ...getRepositoryByIdOptions({
          path: { repositoryId: Number.parseInt(params.repoId) },
        }),
      }),
      queryClient.prefetchQuery({
        ...getProductByIdOptions({
          path: { productId: Number.parseInt(params.productId) },
        }),
      }),
      queryClient.prefetchQuery({
        ...getOrganizationByIdOptions({
          path: { organizationId: Number.parseInt(params.orgId) },
        }),
      }),
      queryClient.prefetchQuery({
        ...getSecretsByRepositoryIdOptions({
          path: { repositoryId: Number.parseInt(params.repoId) },
          query: {
            limit: pageSize || defaultPageSize,
            offset: page ? (page - 1) * (pageSize || defaultPageSize) : 0,
          },
        }),
      }),
      queryClient.prefetchQuery({
        ...getSecretsByProductIdOptions({
          path: { productId: Number.parseInt(params.productId) },
          query: {
            limit: productPageSize || defaultPageSize,
            offset: productPage
              ? (productPage - 1) * (productPageSize || defaultPageSize)
              : 0,
          },
        }),
      }),
      queryClient.prefetchQuery({
        ...getSecretsByOrganizationIdOptions({
          path: { organizationId: Number.parseInt(params.orgId) },
          query: {
            limit: orgPageSize || defaultPageSize,
            offset: orgPage
              ? (orgPage - 1) * (orgPageSize || defaultPageSize)
              : 0,
          },
        }),
      }),
    ]);
  },
  component: RepositorySecrets,
  pendingComponent: LoadingIndicator,
});
