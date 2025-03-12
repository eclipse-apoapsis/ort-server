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

import { usePackagesServiceGetApiV1RunsByRunIdPackages } from '@/api/queries';
import { prefetchUseRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRunsByOrtRunIndex } from '@/api/queries/prefetch';
import {
  usePackagesServiceGetApiV1RunsByRunIdPackagesSuspense,
  useRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRunsByOrtRunIndexSuspense,
  useRunsServiceGetApiV1RunsByRunIdPackagesLicensesSuspense,
} from '@/api/queries/suspense';
import { Package } from '@/api/requests';
import { DataTable } from '@/components/data-table/data-table';
import { DependencyPaths } from '@/components/dependency-paths';
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
  convertToBackendSorting,
  updateColumnSorting,
} from '@/helpers/handle-multisort';
import { identifierToString } from '@/helpers/identifier-to-string';
import { toast } from '@/lib/toast';
import {
  declaredLicenseSearchParameterSchema,
  packageIdentifierSearchParameterSchema,
  paginationSearchParameterSchema,
  sortingSearchParameterSchema,
} from '@/schemas';

const defaultPageSize = 10;

const columnHelper = createColumnHelper<Package>();

const renderSubComponent = ({ row }: { row: Row<Package> }) => {
  const pkg = row.original;

  return (
    <div className='flex flex-col gap-4'>
      <div>
        <div className='font-semibold'>Description</div>
        <div className='ml-2 break-all'>
          {pkg.description || 'No description available.'}
        </div>
      </div>
      <div>
        <div className='font-semibold'>Repository</div>
        <div className='ml-2'>
          {pkg.vcsProcessed.url && (
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
          )}
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
        {pkg.isMetadataOnly ? (
          <div className='ml-2'>This is a metadata-only package.</div>
        ) : pkg.sourceArtifact.url.length > 0 ? (
          <a
            href={pkg.sourceArtifact.url}
            className='ml-2 text-blue-400 hover:underline'
          >
            {pkg.sourceArtifact.url}
          </a>
        ) : (
          <div className='ml-2'>No source artifact found.</div>
        )}
      </div>
      {pkg.shortestDependencyPaths.length > 0 && (
        <div>
          <div className='font-semibold'>Shortest dependency paths</div>
          <DependencyPaths pkg={pkg} className='flex flex-col gap-2' />
        </div>
      )}
    </div>
  );
};

const PackagesComponent = () => {
  const params = Route.useParams();
  const search = Route.useSearch();
  const navigate = Route.useNavigate();
  const pageIndex = search.page ? search.page - 1 : 0;
  const pageSize = search.pageSize ? search.pageSize : defaultPageSize;
  const packageId = search.pkgId;
  const declaredLicense = search.declaredLicense;

  const { data: ortRun } =
    useRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRunsByOrtRunIndexSuspense(
      {
        repositoryId: Number.parseInt(params.repoId),
        ortRunIndex: Number.parseInt(params.runIndex),
      }
    );

  const { data: totalPackages } =
    usePackagesServiceGetApiV1RunsByRunIdPackagesSuspense({
      runId: ortRun.id,
      limit: 1,
    });

  const { data: declaredLicensesOptions } =
    useRunsServiceGetApiV1RunsByRunIdPackagesLicensesSuspense({
      runId: ortRun.id,
    });

  const {
    data: packages,
    isPending,
    isError,
    error,
  } = usePackagesServiceGetApiV1RunsByRunIdPackages({
    runId: ortRun.id,
    limit: pageSize,
    offset: pageIndex * pageSize,
    sort: convertToBackendSorting(search.sortBy),
    identifier: packageId,
    processedDeclaredLicense: declaredLicense?.join(','),
  });

  const columns = [
    columnHelper.display({
      id: 'moreInfo',
      header: 'Details',
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
      enableColumnFilter: false,
    }),
    columnHelper.accessor(
      (pkg) => {
        return identifierToString(pkg.identifier);
      },
      {
        id: 'identifier',
        header: 'Package ID',
        cell: ({ row }) => {
          return (
            <div className='font-semibold'>{row.getValue('identifier')}</div>
          );
        },
        meta: {
          filter: {
            filterVariant: 'text',
            setFilterValue: (value: string | undefined) => {
              navigate({
                search: { ...search, page: 1, pkgId: value },
              });
            },
          },
        },
      }
    ),
    columnHelper.accessor(
      (row) => {
        return row.processedDeclaredLicense.spdxExpression;
      },
      {
        id: 'processedDeclaredLicense',
        header: 'Declared License',
        cell: ({ row }) => (
          <div className='break-all'>
            {row.getValue('processedDeclaredLicense')}
          </div>
        ),
        meta: {
          filter: {
            filterVariant: 'select',
            selectOptions:
              declaredLicensesOptions.processedDeclaredLicenses.map(
                (license) => ({
                  label: license,
                  value: license,
                })
              ),
            setSelected: (licenses: string[]) => {
              navigate({
                search: {
                  ...search,
                  page: 1,
                  declaredLicense: licenses.length === 0 ? undefined : licenses,
                },
              });
            },
          },
        },
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
      enableColumnFilter: false,
      enableSorting: false,
    }),
  ];

  const table = useReactTable({
    data: packages?.data || [],
    columns,
    pageCount: Math.ceil((packages?.pagination.totalCount ?? 0) / pageSize),
    state: {
      pagination: {
        pageIndex,
        pageSize,
      },
      sorting: search.sortBy,
      columnFilters: [
        { id: 'identifier', value: packageId },
        { id: 'processedDeclaredLicense', value: declaredLicense },
      ],
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
  const filtersInUse =
    totalPackages.pagination.totalCount !== packages.pagination.totalCount;
  const matching = `, ${packages.pagination.totalCount} matching filters`;

  return (
    <Card className='h-fit'>
      <CardHeader>
        <CardTitle>
          Packages ({totalPackages.pagination.totalCount} in total
          {filtersInUse && matching})
        </CardTitle>
        <CardDescription>
          This view shows the flat set of de-duplicated packages discovered for
          all projects.
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
          setSortingOptions={(sortBy) => {
            return {
              to: Route.to,
              search: {
                ...search,
                sortBy: updateColumnSorting(search.sortBy, sortBy),
              },
            };
          }}
        />
      </CardContent>
    </Card>
  );
};

export const Route = createFileRoute(
  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/packages/'
)({
  validateSearch: paginationSearchParameterSchema
    .merge(sortingSearchParameterSchema)
    .merge(packageIdentifierSearchParameterSchema)
    .merge(declaredLicenseSearchParameterSchema),
  loader: async ({ context, params }) => {
    await prefetchUseRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRunsByOrtRunIndex(
      context.queryClient,
      {
        repositoryId: Number.parseInt(params.repoId),
        ortRunIndex: Number.parseInt(params.runIndex),
      }
    );
  },
  component: PackagesComponent,
  pendingComponent: LoadingIndicator,
});
