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

import { createFileRoute } from '@tanstack/react-router';
import {
  createColumnHelper,
  getCoreRowModel,
  getExpandedRowModel,
  Row,
  useReactTable,
} from '@tanstack/react-table';
import { ChevronDown, ChevronUp } from 'lucide-react';

import { usePackagesServiceGetPackagesByRunId } from '@/api/queries';
import { prefetchUseRepositoriesServiceGetOrtRunByIndex } from '@/api/queries/prefetch';
import { useRepositoriesServiceGetOrtRunByIndexSuspense } from '@/api/queries/suspense';
import { Package } from '@/api/requests';
import { DataTable } from '@/components/data-table/data-table';
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
import { paginationSchema } from '@/schemas';

const defaultPageSize = 10;

const columnHelper = createColumnHelper<Package>();

const columns = [
  columnHelper.accessor(
    (pkg) => {
      const { type, namespace, name, version } = pkg.identifier;
      return `${type ? type.concat(':') : ''}${namespace ? namespace.concat('/') : ''}${name ? name : ''}${version ? '@'.concat(version) : ''}`;
    },
    {
      id: 'package',
      header: 'Package ID',
      cell: ({ row }) => {
        return <div className='font-semibold'>{row.getValue('package')}</div>;
      },
    }
  ),
  columnHelper.accessor(
    (row) => {
      return row.processedDeclaredLicense.spdxExpression;
    },
    {
      id: 'declaredLicense',
      header: 'Declared License',
      cell: ({ row }) => (
        <div className='break-all'>{row.getValue('declaredLicense')}</div>
      ),
    }
  ),
  columnHelper.accessor('homepageUrl', {
    header: 'Homepage',
    cell: ({ row }) => (
      <a
        href={row.getValue('homepageUrl')}
        target='_blank'
        rel='noopener noreferrer'
        className='font-semibold text-blue-400 hover:underline'
      >
        {row.getValue('homepageUrl')}
      </a>
    ),
  }),
  columnHelper.display({
    id: 'moreInfo',
    header: () => null,
    size: 50,
    cell: ({ row }) => {
      return row.getCanExpand() ? (
        <Button
          variant='outline'
          size='sm'
          onClick={row.getToggleExpandedHandler()}
          style={{ cursor: 'pointer' }}
        >
          {row.getIsExpanded() ? (
            <ChevronUp className='h-4 w-4' />
          ) : (
            <ChevronDown className='h-4 w-4' />
          )}
        </Button>
      ) : (
        'No info'
      );
    },
    enableSorting: false,
  }),
];

const renderSubComponent = ({ row }: { row: Row<Package> }) => {
  const pkg = row.original;

  return (
    <div className='flex flex-col gap-4'>
      <div className='text-lg font-semibold'>Details</div>
      <div className='flex flex-col gap-2'>
        <div>
          <div className='font-semibold'>Description</div>
          <div className='break-all'>
            {pkg.description || 'No description available'}
          </div>
        </div>
        <div>
          <div className='font-semibold'>Repository</div>
          <div className='ml-2'>
            <div className='flex gap-2'>
              <div className='font-semibold'>URL:</div>
              <a
                href={pkg.vcsProcessed.url}
                target='_blank'
                rel='noopener noreferrer'
                className='text-blue-400 hover:underline'
              >
                {pkg.vcsProcessed.url}
              </a>
            </div>
            {pkg.vcsProcessed.type && (
              <div className='flex gap-2'>
                <div className='font-semibold'>Type:</div>
                <div>{pkg.vcsProcessed.type}</div>
              </div>
            )}
            {pkg.vcsProcessed.revision && (
              <div className='flex gap-2'>
                <div className='font-semibold'>Revision:</div>
                <div>{pkg.vcsProcessed.revision}</div>
              </div>
            )}
            {pkg.vcsProcessed.path && (
              <div className='flex gap-2'>
                <div className='font-semibold'>Path:</div>
                <div>{pkg.vcsProcessed.path} </div>
              </div>
            )}
          </div>
        </div>
        <div>
          <div className='font-semibold'>Source Artifact</div>
          <a
            href={pkg.sourceArtifact.url}
            className='text-blue-400 hover:underline'
          >
            {pkg.sourceArtifact.url}
          </a>
        </div>
      </div>
    </div>
  );
};

const PackagesComponent = () => {
  const params = Route.useParams();
  const search = Route.useSearch();
  const pageIndex = search.page ? search.page - 1 : 0;
  const pageSize = search.pageSize ? search.pageSize : defaultPageSize;

  const { data: ortRun } = useRepositoriesServiceGetOrtRunByIndexSuspense({
    repositoryId: Number.parseInt(params.repoId),
    ortRunIndex: Number.parseInt(params.runIndex),
  });

  const {
    data: packages,
    isPending,
    isError,
    error,
  } = usePackagesServiceGetPackagesByRunId({
    runId: ortRun.id,
    limit: pageSize,
    offset: pageIndex * pageSize,
  });

  const table = useReactTable({
    data: packages?.data || [],
    columns,
    pageCount: Math.ceil((packages?.pagination.totalCount ?? 0) / pageSize),
    state: {
      pagination: {
        pageIndex,
        pageSize,
      },
    },
    getCoreRowModel: getCoreRowModel(),
    getExpandedRowModel: getExpandedRowModel(),
    getRowCanExpand: () => true,
    manualPagination: true,
  });

  if (isPending) {
    return <LoadingIndicator />;
  }

  if (isError) {
    toast.error('Unable to load data', {
      description: <ToastError error={error} />,
      duration: Infinity,
      cancel: {
        label: 'Dismiss',
        onClick: () => {},
      },
    });
    return;
  }

  return (
    <Card className='h-fit'>
      <CardHeader>
        <CardTitle>Packages (ORT run global ID: {ortRun.id})</CardTitle>
        <CardDescription>
          Table of deduplicated packages found as dependencies of the project.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <DataTable
          table={table}
          renderSubComponent={renderSubComponent}
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
  );
};

export const Route = createFileRoute(
  '/_layout/organizations/$orgId/products/$productId/repositories/$repoId/_layout/runs/$runIndex/packages/'
)({
  validateSearch: paginationSchema,
  loader: async ({ context, params }) => {
    await prefetchUseRepositoriesServiceGetOrtRunByIndex(context.queryClient, {
      repositoryId: Number.parseInt(params.repoId),
      ortRunIndex: Number.parseInt(params.runIndex),
    });
  },
  component: PackagesComponent,
  pendingComponent: LoadingIndicator,
});
