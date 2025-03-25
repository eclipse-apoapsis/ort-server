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
import { PlusIcon } from 'lucide-react';

import { useRepositoriesServiceGetApiV1RepositoriesByRepositoryId } from '@/api/queries';
import {
  prefetchUseRepositoriesServiceGetApiV1RepositoriesByRepositoryId,
  prefetchUseRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRuns,
} from '@/api/queries/prefetch';
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
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { toast } from '@/lib/toast';
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
  } = useRepositoriesServiceGetApiV1RepositoriesByRepositoryId({
    repositoryId: Number.parseInt(params.repoId),
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
      <JobDurations
        repoId={params.repoId}
        pageIndex={pageIndex}
        pageSize={pageSize}
      />
      <Card>
        <CardHeader>
          <CardTitle>Runs</CardTitle>
          <CardDescription>All runs for {repo.url}</CardDescription>
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
    </div>
  );
};

export const Route = createFileRoute(
  '/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/runs/'
)({
  validateSearch: paginationSearchParameterSchema,
  loaderDeps: ({ search: { page, pageSize } }) => ({ page, pageSize }),
  loader: async ({ context, params, deps: { page, pageSize } }) => {
    await Promise.allSettled([
      prefetchUseRepositoriesServiceGetApiV1RepositoriesByRepositoryId(
        context.queryClient,
        {
          repositoryId: Number.parseInt(params.repoId),
        }
      ),
      prefetchUseRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRuns(
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
