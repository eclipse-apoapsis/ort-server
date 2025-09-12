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
  ExpandedState,
  getCoreRowModel,
  getExpandedRowModel,
  Row,
  useReactTable,
} from '@tanstack/react-table';
import { ChevronDown, ChevronUp, UserPen } from 'lucide-react';
import { useState } from 'react';
import z from 'zod';

import { useRunsServiceGetApiV1RunsByRunIdPackages } from '@/api/queries';
import { prefetchUseRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRunsByOrtRunIndex } from '@/api/queries/prefetch';
import {
  useRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRunsByOrtRunIndexSuspense,
  useRunsServiceGetApiV1RunsByRunIdPackagesLicensesSuspense,
  useRunsServiceGetApiV1RunsByRunIdPackagesSuspense,
} from '@/api/queries/suspense';
import { Package, RepositoryType } from '@/api/requests';
import { BreakableString } from '@/components/breakable-string';
import { DataTable } from '@/components/data-table/data-table';
import { MarkItems } from '@/components/data-table/mark-items';
import { DependencyPaths } from '@/components/dependency-paths';
import { LoadingIndicator } from '@/components/loading-indicator';
import { PackageCuration } from '@/components/package-curation';
import { RenderProperty } from '@/components/render-property';
import { ToastError } from '@/components/toast-error';
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion';
import { Badge } from '@/components/ui/badge';
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
import { getIssueSeverityBackgroundColor } from '@/helpers/get-status-class';
import {
  convertToBackendSorting,
  updateColumnSorting,
} from '@/helpers/handle-multisort';
import { identifierToString } from '@/helpers/identifier-conversion';
import { toast } from '@/lib/toast';
import { getRepositoryTypeLabel } from '@/lib/types';
import {
  declaredLicenseSearchParameterSchema,
  markedSearchParameterSchema,
  packageIdentifierSearchParameterSchema,
  PackageIdType,
  paginationSearchParameterSchema,
  sortingSearchParameterSchema,
} from '@/schemas';
import { useUserSettingsStore } from '@/store/user-settings.store';

const defaultPageSize = 10;

const columnHelper = createColumnHelper<Package>();

const renderSubComponent = ({
  row,
  packageIdType,
}: {
  row: Row<Package>;
  packageIdType?: PackageIdType;
}) => {
  const pkg = row.original;
  const hasCurations = pkg.curations.length > 0;

  return (
    <div className='flex flex-col gap-4'>
      {pkg.isModified && (
        <div>
          <Tooltip>
            <TooltipTrigger>
              <Badge
                className={`border ${getIssueSeverityBackgroundColor('HINT')}`}
              >
                MODIFIED
              </Badge>
            </TooltipTrigger>
            <TooltipContent>
              The source code of the package has been modified compared to the
              original source code, e.g., in case of a fork of an upstream Open
              Source project.
            </TooltipContent>
          </Tooltip>
        </div>
      )}
      <RenderProperty label='Authors' value={pkg.authors} />
      <RenderProperty
        label='Description'
        value={pkg.description}
        type='textblock'
      />
      <RenderProperty label='CPE' value={pkg.cpe} />
      <div>
        <div className='font-semibold'>
          {getRepositoryTypeLabel(pkg.vcsProcessed.type as RepositoryType)}{' '}
          Repository
        </div>
        <div className='ml-2'>
          <RenderProperty label='URL' value={pkg.vcsProcessed.url} type='url' />
          <RenderProperty label='Revision' value={pkg.vcsProcessed.revision} />
          <RenderProperty label='Path' value={pkg.vcsProcessed.path} />
        </div>
      </div>
      <div>
        <div className='font-semibold'>Binary Artifact</div>
        <div className='ml-2'>
          <RenderProperty
            label='URL'
            value={pkg.binaryArtifact.url}
            type='url'
          />
          <RenderProperty
            label='Hash value'
            value={pkg.binaryArtifact.hashValue}
          />
          <RenderProperty
            label='Hash algorithm'
            value={pkg.binaryArtifact.hashAlgorithm}
          />
        </div>
      </div>
      <div>
        <div className='font-semibold'>Source Artifact</div>
        <div className='ml-2'>
          {pkg.isMetadataOnly ? (
            <div>This is a metadata-only package.</div>
          ) : (
            <>
              <RenderProperty
                label='URL'
                value={pkg.sourceArtifact.url}
                type='url'
              />
              <RenderProperty
                label='Hash value'
                value={pkg.sourceArtifact.hashValue}
              />
              <RenderProperty
                label='Hash algorithm'
                value={pkg.sourceArtifact.hashAlgorithm}
              />
            </>
          )}
        </div>
      </div>
      {pkg.shortestDependencyPaths.length > 0 && (
        <div>
          <div className='font-semibold'>Shortest dependency paths</div>
          <DependencyPaths
            pkg={pkg}
            pkgIdType={packageIdType}
            className='flex flex-col gap-2'
          />
        </div>
      )}
      {hasCurations && (
        <Accordion type='multiple' className='w-full'>
          <AccordionItem value='curations'>
            <AccordionTrigger>
              Curations ({pkg.curations.length})
            </AccordionTrigger>
            <AccordionContent>
              <div className='mb-4'>
                The curations appear in the order in which they were applied to
                the original metadata, resulting in the metadata as shown above.
              </div>
              {pkg.curations.map((curation, idx) => (
                <div key={idx} className=''>
                  <PackageCuration curation={curation} />
                </div>
              ))}
            </AccordionContent>
          </AccordionItem>
        </Accordion>
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
  const packageIdType = useUserSettingsStore((state) => state.packageIdType);

  const { data: ortRun } =
    useRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRunsByOrtRunIndexSuspense(
      {
        repositoryId: Number.parseInt(params.repoId),
        ortRunIndex: Number.parseInt(params.runIndex),
      }
    );

  const { data: totalPackages } =
    useRunsServiceGetApiV1RunsByRunIdPackagesSuspense({
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
  } = useRunsServiceGetApiV1RunsByRunIdPackages({
    runId: ortRun.id,
    limit: pageSize,
    offset: pageIndex * pageSize,
    sort: convertToBackendSorting(search.sortBy),
    ...(packageIdType === 'ORT_ID'
      ? { identifier: packageId }
      : { purl: packageId }),
    processedDeclaredLicense: declaredLicense?.join(','),
  });

  const columns = [
    columnHelper.display({
      id: 'moreInfo',
      header: 'Details',
      size: 50,
      cell: function CellComponent({ row }) {
        return row.getCanExpand() ? (
          <div className='flex items-center gap-1'>
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
            <MarkItems
              row={row}
              setMarked={(marked) => {
                return {
                  to: Route.to,
                  search: {
                    ...search,
                    // If no items are marked for inspection, remove the "marked" parameter
                    // from search parameters.
                    marked: marked === '' ? undefined : marked,
                  },
                };
              }}
            />
            {row.original.curations.length > 0 && (
              <Tooltip>
                <TooltipTrigger>
                  <UserPen className='size-4 text-green-600' />
                </TooltipTrigger>
                <TooltipContent>
                  This package has {row.original.curations.length} metadata
                  curation
                  {row.original.curations.length > 1 ? 's' : ''}.
                </TooltipContent>
              </Tooltip>
            )}
          </div>
        ) : (
          'No info'
        );
      },
      enableSorting: false,
      enableColumnFilter: false,
    }),
    columnHelper.accessor(
      (pkg) => {
        if (packageIdType === 'ORT_ID') {
          return identifierToString(pkg.identifier);
        } else {
          return pkg.purl;
        }
      },
      {
        id: `${packageIdType === 'ORT_ID' ? 'identifier' : 'purl'}`,
        header: 'Package ID',
        cell: ({ getValue }) => {
          return (
            <BreakableString text={getValue()} className='font-semibold' />
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
    columnHelper.accessor(
      (row) => {
        return row.curations
          .map((curation) => curation.concludedLicense)
          .join(', ');
      },
      {
        header: 'Concluded License',
        cell: ({ getValue }) => <div className='break-all'>{getValue()}</div>,
        enableColumnFilter: false,
        enableSorting: false,
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

  // Match the column id properly when ORT ID or PURL is used for the column data.
  const columnId = packageIdType === 'ORT_ID' ? 'identifier' : 'purl';

  const [expanded, setExpanded] = useState<ExpandedState>(
    search.marked ? { [search.marked]: true } : {}
  );

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
      expanded: expanded,
      columnFilters: [
        { id: columnId, value: packageId },
        { id: 'processedDeclaredLicense', value: declaredLicense },
      ],
    },
    onExpandedChange: setExpanded,
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
          renderSubComponent={({ row }) =>
            renderSubComponent({ row, packageIdType })
          }
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
  validateSearch: z.object({
    ...paginationSearchParameterSchema.shape,
    ...sortingSearchParameterSchema.shape,
    ...packageIdentifierSearchParameterSchema.shape,
    ...declaredLicenseSearchParameterSchema.shape,
    ...markedSearchParameterSchema.shape,
  }),
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
