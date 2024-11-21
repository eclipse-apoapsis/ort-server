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
import { EditIcon, PlusIcon } from 'lucide-react';

import {
  useRepositoriesServiceDeleteRepositoryById,
  useRepositoriesServiceGetRepositoryById,
} from '@/api/queries';
import {
  prefetchUseRepositoriesServiceGetOrtRunsByRepositoryId,
  prefetchUseRepositoriesServiceGetRepositoryById,
} from '@/api/queries/prefetch';
import { ApiError } from '@/api/requests';
import { DeleteDialog } from '@/components/delete-dialog';
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
import { RepositoryRunsTable } from '../../-components/repository-runs-table';

const defaultPageSize = 10;

const RepositoryRunsComponent = () => {
  const params = Route.useParams();
  const navigate = Route.useNavigate();
  const search = Route.useSearch();
  const pageIndex = search.page ? search.page - 1 : 0;
  const pageSize = search.pageSize ? search.pageSize : defaultPageSize;

  const {
    data: repo,
    error: repoError,
    isPending: repoIsPending,
    isError: repoIsError,
  } = useRepositoriesServiceGetRepositoryById({
    repositoryId: Number.parseInt(params.repoId),
  });

  const { mutateAsync: deleteRepository, isPending } =
    useRepositoriesServiceDeleteRepositoryById({
      onSuccess() {
        toast.info('Delete Repository', {
          description: `Repository "${repo?.url}" deleted successfully.`,
        });
        navigate({
          to: '/organizations/$orgId/products/$productId',
          params: { orgId: params.orgId, productId: params.productId },
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

  async function handleDelete() {
    await deleteRepository({
      repositoryId: Number.parseInt(params.repoId),
    });
  }

  if (repoIsPending) {
    return <LoadingIndicator />;
  }

  if (repoIsError) {
    toast.error('Unable to load data', {
      description: <ToastError error={repoError} />,
      duration: Infinity,
      cancel: {
        label: 'Dismiss',
        onClick: () => {},
      },
    });
    return;
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className='flex flex-row justify-between'>
          <div className='flex items-stretch'>
            <div className='flex items-center pb-1'>{repo.url}</div>
            <Tooltip>
              <TooltipTrigger>
                <Button
                  asChild
                  size='sm'
                  variant='outline'
                  className='ml-2 px-2'
                >
                  <Link
                    to='/organizations/$orgId/products/$productId/repositories/$repoId/edit'
                    params={{
                      orgId: params.orgId,
                      productId: params.productId,
                      repoId: params.repoId,
                    }}
                  >
                    <EditIcon className='h-4 w-4' />
                  </Link>
                </Button>
              </TooltipTrigger>
              <TooltipContent>Edit this repository</TooltipContent>
            </Tooltip>
          </div>
          <DeleteDialog
            item={{
              descriptor: 'repository',
              name: repo.url,
            }}
            onDelete={handleDelete}
            isPending={isPending}
          />
        </CardTitle>
        <CardDescription>{repo.type}</CardDescription>
        <div className='py-2'>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button asChild size='sm' className='ml-auto gap-1'>
                <Link
                  to='/organizations/$orgId/products/$productId/repositories/$repoId/create-run'
                  params={{
                    orgId: params.orgId,
                    productId: params.productId,
                    repoId: params.repoId,
                  }}
                >
                  New run
                  <PlusIcon className='h-4 w-4' />
                </Link>
              </Button>
            </TooltipTrigger>
            <TooltipContent>
              Create a new ORT run for this repository
            </TooltipContent>
          </Tooltip>
        </div>
      </CardHeader>
      <CardContent>
        <RepositoryRunsTable
          repoId={params.repoId}
          pageIndex={pageIndex}
          pageSize={pageSize}
          search={search}
        />
      </CardContent>
    </Card>
  );
};

export const Route = createFileRoute(
  '/_layout/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/runs/'
)({
  validateSearch: paginationSearchParameterSchema,
  loaderDeps: ({ search: { page, pageSize } }) => ({ page, pageSize }),
  loader: async ({ context, params, deps: { page, pageSize } }) => {
    await Promise.allSettled([
      prefetchUseRepositoriesServiceGetRepositoryById(context.queryClient, {
        repositoryId: Number.parseInt(params.repoId),
      }),
      prefetchUseRepositoriesServiceGetOrtRunsByRepositoryId(
        context.queryClient,
        {
          repositoryId: Number.parseInt(params.repoId),
          limit: pageSize || defaultPageSize,
          offset: page ? (page - 1) * (pageSize || defaultPageSize) : 0,
          sort: '-index',
        }
      ),
    ]);
  },
  component: RepositoryRunsComponent,
  pendingComponent: LoadingIndicator,
});
