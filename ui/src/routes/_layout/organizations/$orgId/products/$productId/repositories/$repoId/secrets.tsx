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

import { useSuspenseQueries } from '@tanstack/react-query';
import { createFileRoute, Link, redirect } from '@tanstack/react-router';
import {
  ColumnDef,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table';
import { PlusIcon } from 'lucide-react';

import {
  useRepositoriesServiceGetRepositoryByIdKey,
  useSecretsServiceGetSecretsByRepositoryId,
} from '@/api/queries';
import { RepositoriesService, Secret, SecretsService } from '@/api/requests';
import { DataTable } from '@/components/data-table/data-table';
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
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { paginationSchema } from '@/schemas';

const defaultPageSize = 10;

const columns: ColumnDef<Secret>[] = [
  {
    accessorKey: 'name',
    header: 'Name',
  },
  {
    accessorKey: 'description',
    header: 'Description',
  },
];

const RepositorySecrets = () => {
  const params = Route.useParams();
  const search = Route.useSearch();
  const pageIndex = search.page ? search.page - 1 : 0;
  const pageSize = search.pageSize ? search.pageSize : defaultPageSize;

  const [{ data: repo }, { data: secrets }] = useSuspenseQueries({
    queries: [
      {
        queryKey: [useRepositoriesServiceGetRepositoryByIdKey, params.repoId],
        queryFn: async () =>
          await RepositoriesService.getRepositoryById({
            repositoryId: Number.parseInt(params.repoId),
          }),
      },
      {
        queryKey: [
          useSecretsServiceGetSecretsByRepositoryId,
          params.repoId,
          pageIndex,
          pageSize,
        ],
        queryFn: async () =>
          await SecretsService.getSecretsByRepositoryId({
            repositoryId: Number.parseInt(params.repoId),
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
          <CardDescription>Manage secrets for {repo.url}.</CardDescription>
          <div className='py-2'>
            <Tooltip>
              <TooltipTrigger asChild>
                <Button asChild size='sm' className='ml-auto gap-1'>
                  <Link
                    to='/organizations/$orgId/products/$productId/repositories/$repoId/create-secret'
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
          <DataTable table={table} />
        </CardContent>
      </Card>
    </TooltipProvider>
  );
};

export const Route = createFileRoute(
  '/_layout/organizations/$orgId/products/$productId/repositories/$repoId/secrets'
)({
  validateSearch: paginationSchema,
  loaderDeps: ({ search: { page, pageSize } }) => ({ page, pageSize }),
  loader: async ({ context, params, deps: { page, pageSize } }) => {
    await Promise.allSettled([
      context.queryClient.ensureQueryData({
        queryKey: [useRepositoriesServiceGetRepositoryByIdKey, params.repoId],
        queryFn: async () =>
          await RepositoriesService.getRepositoryById({
            repositoryId: Number.parseInt(params.repoId),
          }),
      }),
      context.queryClient.ensureQueryData({
        queryKey: [
          useSecretsServiceGetSecretsByRepositoryId,
          params.repoId,
          page,
          pageSize,
        ],
        queryFn: async () =>
          await SecretsService.getSecretsByRepositoryId({
            repositoryId: Number(params.repoId),
            limit: pageSize || defaultPageSize,
            offset: page ? (page - 1) * (pageSize || defaultPageSize) : 0,
          }),
      }),
    ]);
  },
  component: RepositorySecrets,
  beforeLoad: ({ context, params }) => {
    if (
      !context.auth.hasRole([
        'superuser',
        `permission_repository_${params.repoId}_write_secrets`,
      ])
    ) {
      throw redirect({
        to: '/403',
      });
    }
  },
  pendingComponent: LoadingIndicator,
});
