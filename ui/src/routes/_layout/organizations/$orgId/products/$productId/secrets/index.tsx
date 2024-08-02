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
  useQueryClient,
  useSuspenseQueries,
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
import { useState } from 'react';

import {
  useProductsServiceGetProductByIdKey,
  useSecretsServiceDeleteSecretByProductIdAndName,
  useSecretsServiceGetSecretsByProductId,
} from '@/api/queries';
import {
  ApiError,
  ProductsService,
  Secret,
  SecretsService,
} from '@/api/requests';
import { DataTable } from '@/components/data-table/data-table';
import { DeleteDialog } from '@/components/delete-dialog';
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
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { useToast } from '@/components/ui/use-toast';
import { cn } from '@/lib/utils';
import { paginationSchema } from '@/schemas';

const defaultPageSize = 10;

const ActionCell = ({ row }: CellContext<Secret, unknown>) => {
  const params = Route.useParams();
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const [openDelDialog, setOpenDelDialog] = useState(false);

  const { data: product } = useSuspenseQuery({
    queryKey: [useProductsServiceGetProductByIdKey, params.productId],
    queryFn: async () =>
      await ProductsService.getProductById({
        productId: Number.parseInt(params.productId),
      }),
  });

  const { mutateAsync: deleteSecret, isPending: delIsPending } =
    useSecretsServiceDeleteSecretByProductIdAndName({
      onSuccess() {
        setOpenDelDialog(false);
        toast({
          title: 'Delete Secret',
          description: `Secret "${row.original.name}" deleted successfully.`,
        });
        queryClient.invalidateQueries({
          queryKey: [useSecretsServiceGetSecretsByProductId],
        });
      },
      onError(error: ApiError) {
        toast({
          title: error.message,
          description: <ToastError error={error} />,
          variant: 'destructive',
        });
      },
    });

  return (
    <div className='flex justify-end gap-1'>
      <TooltipProvider>
        <Tooltip>
          <TooltipTrigger asChild>
            <Link
              to='/organizations/$orgId/products/$productId/secrets/$secretName/edit'
              params={{
                orgId: params.orgId,
                productId: params.productId,
                secretName: row.original.name,
              }}
              className={cn(buttonVariants({ variant: 'outline' }), 'h-9 px-2')}
            >
              <span className='sr-only'>Edit</span>
              <EditIcon size={16} />
            </Link>
          </TooltipTrigger>
          <TooltipContent>Edit this secret</TooltipContent>
        </Tooltip>
      </TooltipProvider>
      <DeleteDialog
        open={openDelDialog}
        setOpen={setOpenDelDialog}
        item={{ descriptor: 'secret', name: row.original.name }}
        onDelete={() =>
          deleteSecret({
            productId: product.id,
            secretName: row.original.name,
          })
        }
        isPending={delIsPending}
      />
    </div>
  );
};

const columns: ColumnDef<Secret>[] = [
  {
    accessorKey: 'name',
    header: 'Name',
  },
  {
    accessorKey: 'description',
    header: 'Description',
  },
  {
    id: 'actions',
    cell: ActionCell,
  },
];

const ProductSecrets = () => {
  const params = Route.useParams();
  const search = Route.useSearch();
  const pageIndex = search.page ? search.page - 1 : 0;
  const pageSize = search.pageSize ? search.pageSize : defaultPageSize;

  const [{ data: product }, { data: secrets }] = useSuspenseQueries({
    queries: [
      {
        queryKey: [useProductsServiceGetProductByIdKey, params.productId],
        queryFn: async () =>
          await ProductsService.getProductById({
            productId: Number.parseInt(params.productId),
          }),
      },
      {
        queryKey: [
          useSecretsServiceGetSecretsByProductId,
          params.productId,
          pageIndex,
          pageSize,
        ],
        queryFn: async () =>
          await SecretsService.getSecretsByProductId({
            productId: Number.parseInt(params.productId),
            limit: pageSize,
            offset: pageIndex * pageSize,
          }),
      },
    ],
  });

  const table = useReactTable({
    data: secrets?.data || [],
    columns,
    pageCount: Math.ceil(secrets.pagination.totalCount / pageSize),
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
    <TooltipProvider>
      <Card className='mx-auto w-full max-w-4xl'>
        <CardHeader>
          <CardTitle>Secrets</CardTitle>
          <CardDescription>Manage secrets for {product.name}.</CardDescription>
          <div className='py-2'>
            <Tooltip>
              <TooltipTrigger asChild>
                <Button asChild size='sm' className='ml-auto gap-1'>
                  <Link
                    to='/organizations/$orgId/products/$productId/secrets/create-secret'
                    params={{
                      orgId: params.orgId,
                      productId: params.productId,
                    }}
                  >
                    New secret
                    <PlusIcon className='h-4 w-4' />
                  </Link>
                </Button>
              </TooltipTrigger>
              <TooltipContent>
                Create a new secret for this product
              </TooltipContent>
            </Tooltip>
          </div>
        </CardHeader>
        <CardContent>
          <DataTable table={table} />
        </CardContent>
      </Card>
    </TooltipProvider>
  );
};

export const Route = createFileRoute(
  '/_layout/organizations/$orgId/products/$productId/secrets/'
)({
  validateSearch: paginationSchema,
  loaderDeps: ({ search: { page, pageSize } }) => ({ page, pageSize }),
  loader: async ({ context, params, deps: { page, pageSize } }) => {
    await Promise.allSettled([
      context.queryClient.ensureQueryData({
        queryKey: [useProductsServiceGetProductByIdKey, params.orgId],
        queryFn: () =>
          ProductsService.getProductById({
            productId: Number(params.productId),
          }),
      }),
      context.queryClient.ensureQueryData({
        queryKey: [
          useSecretsServiceGetSecretsByProductId,
          params.orgId,
          page,
          pageSize,
        ],
        queryFn: () =>
          SecretsService.getSecretsByProductId({
            productId: Number(params.productId),
            limit: pageSize || defaultPageSize,
            offset: page ? (page - 1) * (pageSize || defaultPageSize) : 0,
          }),
      }),
    ]);
  },
  component: ProductSecrets,
  pendingComponent: LoadingIndicator,
});
