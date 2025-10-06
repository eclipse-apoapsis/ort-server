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
import { createFileRoute, Link } from '@tanstack/react-router';
import { PlusIcon } from 'lucide-react';

import {
  getRepositoryOptions,
  getRepositoryRunsOptions,
} from '@/api/@tanstack/react-query.gen';
import { JobDurations } from '@/components/charts/job-durations';
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
import { toast } from '@/lib/toast';
import { getRepositoryTypeLabel } from '@/lib/types';
import { paginationSearchParameterSchema } from '@/schemas';
import { useTablePrefsStore } from '@/store/table-prefs.store';
import { RepositoryRunsTable } from '../../-components/repository-runs-table';

const defaultPageSize = useTablePrefsStore.getState().repoPageSize;

const RepositoryRunsComponent = () => {
  const runPageSize = useTablePrefsStore.getState().runPageSize;
  const params = Route.useParams();
  const search = Route.useSearch();
  const pageIndex = search.page ? search.page - 1 : 0;
  const pageSize = search.pageSize ? search.pageSize : runPageSize;

  const {
    data: repo,
    error: repoError,
    isPending: repoIsPending,
    isError: repoIsError,
  } = useQuery({
    ...getRepositoryOptions({
      path: { repositoryId: Number.parseInt(params.repoId) },
    }),
  });

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
    <div className='flex flex-col gap-4'>
      <div className='grid grid-cols-1 gap-4 md:grid-cols-4'>
        <Card className='col-span-1 md:col-span-3'>
          <CardHeader>
            <CardTitle>
              <span className='font-normal'>
                {getRepositoryTypeLabel(repo.type)} repository
              </span>{' '}
              <Link
                className='font-semibold break-all hover:text-blue-400 hover:underline'
                to={repo.url}
                target='_blank'
              >
                {repo.url}
              </Link>
            </CardTitle>
            <CardDescription>{repo.description}</CardDescription>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader>
            <CardDescription>
              Repository ID: <span className='font-bold'>{repo.id}</span>
            </CardDescription>
          </CardHeader>
        </Card>
      </div>
      <JobDurations
        repoId={params.repoId}
        pageIndex={pageIndex}
        pageSize={pageSize}
      />
      <Card>
        <CardHeader>
          <CardTitle>Runs</CardTitle>
          <div className='py-2'>
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
    </div>
  );
};

export const Route = createFileRoute(
  '/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/runs/'
)({
  validateSearch: paginationSearchParameterSchema,
  loaderDeps: ({ search: { page, pageSize } }) => ({ page, pageSize }),
  loader: async ({
    context: { queryClient },
    params,
    deps: { page, pageSize },
  }) => {
    await Promise.allSettled([
      queryClient.prefetchQuery({
        ...getRepositoryOptions({
          path: { repositoryId: Number.parseInt(params.repoId) },
        }),
      }),
      queryClient.prefetchQuery({
        ...getRepositoryRunsOptions({
          path: { repositoryId: Number.parseInt(params.repoId) },
          query: {
            limit: pageSize || defaultPageSize,
            offset: page ? (page - 1) * (pageSize || defaultPageSize) : 0,
            sort: '-index',
          },
        }),
      }),
    ]);
  },
  component: RepositoryRunsComponent,
  pendingComponent: LoadingIndicator,
});
