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
import {
  createColumnHelper,
  getCoreRowModel,
  getExpandedRowModel,
  getFilteredRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  Row,
  useReactTable,
} from '@tanstack/react-table';
import { ChevronDown, ChevronUp } from 'lucide-react';
import { useMemo } from 'react';

import { useRunsServiceGetApiV1RunsByRunIdVulnerabilities } from '@/api/queries';
import { prefetchUseRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRunsByOrtRunIndex } from '@/api/queries/prefetch';
import { useRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRunsByOrtRunIndexSuspense } from '@/api/queries/suspense';
import {
  VulnerabilityRating,
  VulnerabilityWithIdentifier,
} from '@/api/requests';
import { VulnerabilityMetrics } from '@/components/charts/vulnerability-metrics';
import { DataTable } from '@/components/data-table/data-table';
import { LoadingIndicator } from '@/components/loading-indicator';
import { MarkdownRenderer } from '@/components/markdown-renderer';
import { ToastError } from '@/components/toast-error';
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
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { getVulnerabilityRatingBackgroundColor } from '@/helpers/get-status-class';
import { updateColumnSorting } from '@/helpers/handle-multisort';
import { identifierToString } from '@/helpers/identifier-to-string';
import { compareVulnerabilityRating } from '@/helpers/sorting-functions';
import { ALL_ITEMS } from '@/lib/constants';
import { toast } from '@/lib/toast';
import {
  packageIdentifierSearchParameterSchema,
  paginationSearchParameterSchema,
  sortingSearchParameterSchema,
  vulnerabilityRatingSchema,
  vulnerabilityRatingSearchParameterSchema,
} from '@/schemas';

const defaultPageSize = 10;

const columnHelper = createColumnHelper<VulnerabilityWithIdentifier>();

const renderSubComponent = ({
  row,
}: {
  row: Row<VulnerabilityWithIdentifier>;
}) => {
  const vulnerability = row.original.vulnerability;

  return (
    <div className='flex flex-col gap-4'>
      <VulnerabilityMetrics vulnerability={vulnerability} />
      <div className='text-lg font-semibold'>Description</div>
      <MarkdownRenderer
        markdown={vulnerability.description || 'No description.'}
      />
      <div className='mt-2 text-lg font-semibold'>
        Links to vulnerability references
      </div>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Severity</TableHead>
            <TableHead>Scoring system</TableHead>
            <TableHead>Score</TableHead>
            <TableHead>Vector</TableHead>
            <TableHead>Link</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {vulnerability.references
            .sort((refA, refB) => (refB.score ?? 0) - (refA.score ?? 0))
            .map((reference, index) => (
              <TableRow key={index}>
                <TableCell>{reference.severity || '-'}</TableCell>
                <TableCell>{reference.scoringSystem || '-'}</TableCell>
                <TableCell>{reference.score || '-'}</TableCell>
                <TableCell>{reference.vector || '-'}</TableCell>
                <TableCell>
                  {
                    <Link
                      className='font-semibold break-all text-blue-400 hover:underline'
                      to={reference.url}
                      target='_blank'
                    >
                      {reference.url}
                    </Link>
                  }
                </TableCell>
              </TableRow>
            ))}
        </TableBody>
      </Table>
    </div>
  );
};

const VulnerabilitiesComponent = () => {
  const params = Route.useParams();
  const search = Route.useSearch();
  const navigate = Route.useNavigate();

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
            {...{
              onClick: row.getToggleExpandedHandler(),
              style: { cursor: 'pointer' },
            }}
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
    columnHelper.accessor('rating', {
      header: 'Rating',
      cell: ({ getValue }) => {
        return (
          <Badge
            className={`${getVulnerabilityRatingBackgroundColor(getValue())}`}
          >
            {getValue()}
          </Badge>
        );
      },
      filterFn: (row, _columnId, filterValue): boolean => {
        return filterValue.includes(row.original.rating);
      },
      sortingFn: (rowA, rowB) => {
        return compareVulnerabilityRating(
          rowA.getValue('rating'),
          rowB.getValue('rating')
        );
      },
      meta: {
        filter: {
          filterVariant: 'select',
          selectOptions: vulnerabilityRatingSchema.options.map((rating) => ({
            label: rating,
            value: rating,
          })),
          setSelected: (ratings: VulnerabilityRating[]) => {
            navigate({
              search: {
                ...search,
                page: 1,
                rating: ratings.length === 0 ? undefined : ratings,
              },
            });
          },
        },
      },
    }),
    columnHelper.accessor(
      (vuln) => {
        return identifierToString(vuln.identifier);
      },
      {
        id: 'packageIdentifier',
        header: 'Package ID',
        cell: ({ getValue }) => {
          return <div className='font-semibold'>{getValue()}</div>;
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
    columnHelper.accessor('vulnerability.externalId', {
      id: 'externalId',
      header: 'External ID',
      cell: ({ row }) => (
        <Badge className='bg-blue-300 whitespace-nowrap'>
          {row.getValue('externalId')}
        </Badge>
      ),
      enableColumnFilter: false,
    }),
    columnHelper.accessor(
      (row) => {
        return row.vulnerability.summary;
      },
      {
        id: 'summary',
        header: 'Summary',
        cell: ({ row }) => {
          return (
            <div className='text-muted-foreground italic'>
              {row.original.vulnerability.summary}
            </div>
          );
        },
        enableSorting: false,
        enableColumnFilter: false,
      }
    ),
  ];

  // All of these need to be memoized to prevent unnecessary re-renders
  // and (at least for Firefox) the browser freezing up.
  const pageIndex = useMemo(
    () => (search.page ? search.page - 1 : 0),
    [search.page]
  );

  const pageSize = useMemo(
    () => (search.pageSize ? search.pageSize : defaultPageSize),
    [search.pageSize]
  );

  const packageIdentifier = useMemo(
    () => (search.pkgId ? search.pkgId : undefined),
    [search.pkgId]
  );

  const rating = useMemo(
    () => (search.rating ? search.rating : undefined),
    [search.rating]
  );

  const columnFilters = useMemo(() => {
    const filters = [];
    if (packageIdentifier) {
      filters.push({ id: 'packageIdentifier', value: packageIdentifier });
    }
    if (rating) {
      filters.push({ id: 'rating', value: rating });
    }
    return filters;
  }, [packageIdentifier, rating]);

  const sortBy = useMemo(
    () => (search.sortBy ? search.sortBy : undefined),
    [search.sortBy]
  );

  const { data: ortRun } =
    useRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRunsByOrtRunIndexSuspense(
      {
        repositoryId: Number.parseInt(params.repoId),
        ortRunIndex: Number.parseInt(params.runIndex),
      }
    );

  const {
    data: vulnerabilities,
    isPending,
    isError,
    error,
  } = useRunsServiceGetApiV1RunsByRunIdVulnerabilities({
    runId: ortRun.id,
    limit: ALL_ITEMS,
  });

  const table = useReactTable({
    data: vulnerabilities?.data || [],
    columns,
    state: {
      pagination: {
        pageIndex,
        pageSize,
      },
      columnFilters,
      sorting: sortBy,
    },
    getCoreRowModel: getCoreRowModel(),
    getExpandedRowModel: getExpandedRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getRowCanExpand: () => true,
    enableMultiSort: false,
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
  const filtersInUse = table.getState().columnFilters.length > 0;
  const matching = `, ${table.getPrePaginationRowModel().rows.length} matching filters`;

  return (
    <Card className='h-fit'>
      <CardHeader>
        <CardTitle>
          Vulnerabilities ({vulnerabilities.pagination.totalCount} in total
          {filtersInUse && matching})
        </CardTitle>
        <CardDescription>
          This view shows the vulnerabilities found in any of the packages used
          as dependencies of a project. As vulnerabilities may be only
          discovered over time, new vulnerabilities may arise even without
          changes to the any of the projects or their dependencies. Therefore,
          it is important to check for vulnerabilities on a regular basis.
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
  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/vulnerabilities/'
)({
  validateSearch: paginationSearchParameterSchema
    .merge(sortingSearchParameterSchema)
    .merge(packageIdentifierSearchParameterSchema)
    .merge(vulnerabilityRatingSearchParameterSchema),
  loader: async ({ context, params }) => {
    await prefetchUseRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRunsByOrtRunIndex(
      context.queryClient,
      {
        repositoryId: Number.parseInt(params.repoId),
        ortRunIndex: Number.parseInt(params.runIndex),
      }
    );
  },
  component: VulnerabilitiesComponent,
  pendingComponent: LoadingIndicator,
});
