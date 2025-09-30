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

import { useSuspenseQuery } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';
import {
  createColumnHelper,
  ExpandedState,
  getCoreRowModel,
  getExpandedRowModel,
  Row,
  useReactTable,
} from '@tanstack/react-table';
import { ChevronDown, ChevronUp } from 'lucide-react';
import { useState } from 'react';
import z from 'zod';

import { Package } from '@/api';
import {
  getLicensesForPackagesByRunIdOptions,
  getOrtRunByIndexOptions,
  getPackagesByRunIdOptions,
} from '@/api/@tanstack/react-query.gen';
import { BreakableString } from '@/components/breakable-string';
import { DataTableCards } from '@/components/data-table-cards/data-table-cards';
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

// Component to render a single package card in the list.
const PackageCard = ({ pkg }: { pkg: Package }) => {
  const packageIdType = useUserSettingsStore((state) => state.packageIdType);
  const id =
    packageIdType === 'PURL' && pkg.purl
      ? pkg.purl
      : identifierToString(pkg.identifier);
  const declaredLicenses = [
    ...(pkg.processedDeclaredLicense.spdxExpression
      ? [pkg.processedDeclaredLicense.spdxExpression]
      : []),
    ...(pkg.processedDeclaredLicense.unmappedLicenses ?? []),
  ];

  return (
    <div className='flex flex-col gap-1'>
      <div className='flex items-center justify-between'>
        <div className='font-semibold'>
          <BreakableString text={id} />
        </div>
        <a
          href={pkg.homepageUrl}
          target='_blank'
          rel='noopener noreferrer'
          className='text-blue-400 hover:underline'
        >
          {pkg.homepageUrl}
        </a>
      </div>
      {declaredLicenses.length > 0 ? (
        <div className='flex gap-2 text-sm'>
          <div className='text-muted-foreground'>Declared License:</div>
          <div className='break-words'>{declaredLicenses.join(',')}</div>
        </div>
      ) : (
        <div className='text-muted-foreground italic'>No declared license</div>
      )}

      <div className='flex gap-2'>
        {pkg.curations.length > 0 && (
          <Tooltip>
            <TooltipTrigger>
              <Badge variant='small' className='bg-green-600'>
                CURATED
              </Badge>
            </TooltipTrigger>
            <TooltipContent>
              This package has {pkg.curations.length} metadata curation
              {pkg.curations.length > 1 ? 's' : ''}.
            </TooltipContent>
          </Tooltip>
        )}
        {pkg.isMetadataOnly && (
          <div>
            <Tooltip>
              <TooltipTrigger>
                <Badge
                  variant='small'
                  className={`${getIssueSeverityBackgroundColor('HINT')}`}
                >
                  METADATA ONLY
                </Badge>
              </TooltipTrigger>
              <TooltipContent>
                This is a metadata-only package that has no source code
                associated to it.
              </TooltipContent>
            </Tooltip>
          </div>
        )}
        {pkg.isModified && (
          <div>
            <Tooltip>
              <TooltipTrigger>
                <Badge
                  variant='small'
                  className={`${getIssueSeverityBackgroundColor('WARNING')}`}
                >
                  MODIFIED
                </Badge>
              </TooltipTrigger>
              <TooltipContent>
                The package has been modified compared to the original package,
                e.g. in case of a fork of an upstream Open Source project.
              </TooltipContent>
            </Tooltip>
          </div>
        )}
      </div>
    </div>
  );
};

// Component to ender the expanded subrow of a package card, showing additional details.
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
      <RenderProperty label='Authors' value={pkg.authors} />
      <RenderProperty
        label='Description'
        value={pkg.description}
        type='textblock'
      />
      <RenderProperty label='CPE' value={pkg.cpe} />
      {pkg.vcsProcessed.type ||
      pkg.vcsProcessed.url ||
      pkg.vcsProcessed.revision ||
      pkg.vcsProcessed.path ? (
        <div>
          <div className='font-semibold'>
            {getRepositoryTypeLabel(pkg.vcsProcessed.type)} Repository
          </div>
          <div className='ml-2'>
            <RenderProperty
              label='URL'
              value={pkg.vcsProcessed.url}
              type='url'
            />
            <RenderProperty
              label='Revision'
              value={pkg.vcsProcessed.revision}
            />
            <RenderProperty label='Path' value={pkg.vcsProcessed.path} />
          </div>
        </div>
      ) : (
        <RenderProperty label='Repository' value={null} />
      )}
      {pkg.binaryArtifact.url ||
      pkg.binaryArtifact.hashValue ||
      pkg.binaryArtifact.hashAlgorithm ? (
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
      ) : (
        <RenderProperty label='Binary Artifact' value={null} />
      )}
      {pkg.isMetadataOnly ? null : pkg.sourceArtifact.url ||
        pkg.sourceArtifact.hashValue ||
        pkg.sourceArtifact.hashAlgorithm ? (
        <div>
          <div className='font-semibold'>Source Artifact</div>
          <div className='ml-2'>
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
          </div>
        </div>
      ) : (
        <RenderProperty label='Source Artifact' value={null} />
      )}
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

  const { data: ortRun } = useSuspenseQuery({
    ...getOrtRunByIndexOptions({
      path: {
        repositoryId: Number.parseInt(params.repoId),
        ortRunIndex: Number.parseInt(params.runIndex),
      },
    }),
  });

  const { data: totalPackages } = useSuspenseQuery({
    ...getPackagesByRunIdOptions({
      path: { runId: ortRun.id },
      query: { limit: 1 },
    }),
  });

  const { data: declaredLicensesOptions } = useSuspenseQuery({
    ...getLicensesForPackagesByRunIdOptions({
      path: { runId: ortRun.id },
    }),
  });

  const {
    data: packages,
    isPending,
    isError,
    error,
  } = useSuspenseQuery({
    ...getPackagesByRunIdOptions({
      path: {
        runId: ortRun.id,
      },
      query: {
        limit: pageSize,
        offset: pageIndex * pageSize,
        sort: convertToBackendSorting(search.sortBy),
        ...(packageIdType === 'ORT_ID'
          ? { identifier: packageId }
          : { purl: packageId }),
        processedDeclaredLicense: declaredLicense?.join(','),
      },
    }),
  });

  const columns = [
    // Leftmost action column.
    columnHelper.display({
      id: 'details',
      header: 'Details',
      size: 20,
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
                    marked: marked === '' ? undefined : marked,
                  },
                };
              }}
            />
          </div>
        ) : (
          'No info'
        );
      },
    }),
    // Main column that presents all data.
    columnHelper.display({
      id: 'card',
      cell: ({ row }) => <PackageCard pkg={row.original} />,
    }),
    // All (hidden) columns that are used for filtering/sorting the main column.
    // They don't render any data, but need to retain their accessor logic, and
    // any special sorting and filtering logic.
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
        meta: {
          filter: {
            filterVariant: 'select',
            align: 'end',
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
  ];

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
      // Always hide the columns that are used for filtering/sorting only.
      columnVisibility: {
        [columnId]: false,
        processedDeclaredLicense: false,
      },
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
        <DataTableCards
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
  loader: async ({ context: { queryClient }, params }) => {
    await queryClient.prefetchQuery({
      ...getOrtRunByIndexOptions({
        path: {
          repositoryId: Number.parseInt(params.repoId),
          ortRunIndex: Number.parseInt(params.runIndex),
        },
      }),
    });
  },
  component: PackagesComponent,
  pendingComponent: LoadingIndicator,
});
