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

import { useQuery, useSuspenseQuery } from '@tanstack/react-query';
import { createFileRoute, Link } from '@tanstack/react-router';
import {
  createColumnHelper,
  ExpandedState,
  getCoreRowModel,
  getExpandedRowModel,
  getFilteredRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  Row,
  useReactTable,
} from '@tanstack/react-table';
import { ChevronDown, ChevronUp } from 'lucide-react';
import { useMemo, useState } from 'react';
import z from 'zod';

import { VulnerabilityRating, VulnerabilityWithDetails } from '@/api';
import {
  getRepositoryRunOptions,
  getVulnerabilitiesByRunIdOptions,
} from '@/api/@tanstack/react-query.gen';
import { zVulnerabilityRating } from '@/api/zod.gen';
import { BreakableString } from '@/components/breakable-string';
import { VulnerabilityMetrics } from '@/components/charts/vulnerability-metrics';
import { DataTableCards } from '@/components/data-table-cards/data-table-cards';
import { MarkItems } from '@/components/data-table/mark-items';
import { LoadingIndicator } from '@/components/loading-indicator';
import { MarkdownRenderer } from '@/components/markdown-renderer';
import { Resolutions } from '@/components/resolutions';
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
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import {
  getResolvedBackgroundColor,
  getVulnerabilityRatingBackgroundColor,
} from '@/helpers/get-status-class';
import { updateColumnSorting } from '@/helpers/handle-multisort';
import { identifierToString } from '@/helpers/identifier-conversion';
import { getResolvedStatus } from '@/helpers/resolutions';
import { compareVulnerabilityRating } from '@/helpers/sorting-functions';
import { ACTION_COLUMN_SIZE, ALL_ITEMS } from '@/lib/constants';
import { toast } from '@/lib/toast';
import {
  ItemResolved,
  itemResolvedSchema,
  itemStatusSearchParameterSchema,
  markedSearchParameterSchema,
  packageIdentifierSearchParameterSchema,
  paginationSearchParameterSchema,
  sortingSearchParameterSchema,
  vulnerabilityRatingSearchParameterSchema,
} from '@/schemas';
import { useUserSettingsStore } from '@/store/user-settings.store';

const defaultPageSize = 10;

const columnHelper = createColumnHelper<VulnerabilityWithDetails>();

// Component to render a single vulnerability card in the list.
const VulnerabilityCard = ({
  vulnerability,
}: {
  vulnerability: VulnerabilityWithDetails;
}) => {
  const packageIdType = useUserSettingsStore((state) => state.packageIdType);
  const id =
    packageIdType === 'PURL' && vulnerability.purl
      ? vulnerability.purl
      : identifierToString(vulnerability.identifier);

  return (
    <div className='flex flex-col gap-1'>
      <div className='flex items-center justify-between'>
        <div className='font-semibold'>
          <BreakableString text={id} />
        </div>
        <Badge className='bg-blue-300 whitespace-nowrap' variant='small'>
          {vulnerability.vulnerability.externalId}
        </Badge>
      </div>
      <div className='flex items-center justify-between'>
        <div className='text-muted-foreground break-words italic'>
          {vulnerability.vulnerability.summary || 'No summary available'}
        </div>
        <div className='text-muted-foreground'>
          {vulnerability.advisor.name}
        </div>
      </div>

      <div className='flex gap-2'>
        <Badge
          className={`${getResolvedBackgroundColor(getResolvedStatus(vulnerability))}`}
          variant='small'
        >
          {getResolvedStatus(vulnerability)}
        </Badge>
        <Badge
          className={`${getVulnerabilityRatingBackgroundColor(
            vulnerability.rating
          )}`}
          variant='small'
        >
          {vulnerability.rating}
        </Badge>
      </div>
    </div>
  );
};

const renderSubComponent = ({
  row,
}: {
  row: Row<VulnerabilityWithDetails>;
}) => {
  const vulnerability = row.original.vulnerability;
  const hasResolutions = getResolvedStatus(row.original) === 'Resolved';

  return (
    <Accordion
      type='multiple'
      className='w-full'
      defaultValue={hasResolutions ? ['resolutions'] : ['details']}
    >
      <AccordionItem value='resolutions'>
        <AccordionTrigger className='font-semibold'>
          Resolutions
        </AccordionTrigger>
        <AccordionContent>
          <Resolutions item={row.original} />
        </AccordionContent>
      </AccordionItem>
      <AccordionItem value='details'>
        <AccordionTrigger className='font-semibold'>Details</AccordionTrigger>
        <AccordionContent>
          <div className='flex flex-col gap-4'>
            <VulnerabilityMetrics vulnerability={vulnerability} />
            <div className='font-semibold'>Description</div>
            <MarkdownRenderer
              markdown={vulnerability.description || 'No description available'}
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
        </AccordionContent>
      </AccordionItem>
    </Accordion>
  );
};

const VulnerabilitiesComponent = () => {
  const params = Route.useParams();
  const search = Route.useSearch();
  const navigate = Route.useNavigate();
  const packageIdType = useUserSettingsStore((state) => state.packageIdType);

  const columns = [
    columnHelper.display({
      id: 'details',
      header: 'Details',
      size: ACTION_COLUMN_SIZE,
      cell: function CellComponent({ row }) {
        return row.getCanExpand() ? (
          <div className='flex items-center gap-1'>
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
          </div>
        ) : (
          'No info'
        );
      },
    }),
    columnHelper.display({
      id: 'card',
      cell: ({ row }) => <VulnerabilityCard vulnerability={row.original} />,
    }),
    columnHelper.accessor(
      (vuln) => {
        if (packageIdType === 'PURL') {
          return vuln.purl;
        } else {
          return identifierToString(vuln.identifier);
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
      (vuln) => {
        return getResolvedStatus(vuln);
      },
      {
        id: 'itemStatus',
        header: 'Status',
        filterFn: (row, _columnId, filterValue): boolean => {
          return filterValue.includes(getResolvedStatus(row.original));
        },
        meta: {
          filter: {
            filterVariant: 'select',
            selectOptions: itemResolvedSchema.options.map((itemResolved) => ({
              label: itemResolved,
              value: itemResolved,
            })),
            setSelected: (statuses: ItemResolved[]) => {
              navigate({
                search: {
                  ...search,
                  page: 1,
                  itemResolved: statuses.length === 0 ? undefined : statuses,
                },
              });
            },
          },
        },
      }
    ),
    columnHelper.accessor('rating', {
      header: 'Rating',
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
          selectOptions: zVulnerabilityRating.options.map((rating) => ({
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
    columnHelper.accessor('vulnerability.externalId', {
      id: 'externalId',
      header: 'External ID',
      enableColumnFilter: false,
    }),
    columnHelper.accessor('advisor.name', {
      id: 'advisorName',
      header: 'Advisor',
      enableColumnFilter: false,
    }),
    columnHelper.accessor(
      (row) => {
        return row.vulnerability.summary;
      },
      {
        id: 'summary',
        header: 'Summary',
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

  const itemStatus = useMemo(
    () => (search.itemResolved ? search.itemResolved : undefined),
    [search.itemResolved]
  );

  const packageIdentifier = useMemo(
    () => (search.pkgId ? search.pkgId : undefined),
    [search.pkgId]
  );

  const rating = useMemo(
    () => (search.rating ? search.rating : undefined),
    [search.rating]
  );

  const columnId = packageIdType === 'ORT_ID' ? 'identifier' : 'purl';

  const columnFilters = useMemo(() => {
    const filters = [];
    if (itemStatus) {
      filters.push({ id: 'itemStatus', value: itemStatus });
    }
    if (packageIdentifier) {
      filters.push({ id: columnId, value: packageIdentifier });
    }
    if (rating) {
      filters.push({ id: 'rating', value: rating });
    }
    return filters;
  }, [itemStatus, packageIdentifier, columnId, rating]);

  const sortBy = useMemo(
    () => (search.sortBy ? search.sortBy : undefined),
    [search.sortBy]
  );

  const { data: ortRun } = useSuspenseQuery({
    ...getRepositoryRunOptions({
      path: {
        repositoryId: Number.parseInt(params.repoId),
        ortRunIndex: Number.parseInt(params.runIndex),
      },
    }),
  });

  const {
    data: vulnerabilities,
    isPending,
    isError,
    error,
  } = useQuery({
    ...getVulnerabilitiesByRunIdOptions({
      path: { runId: ortRun.id },
      query: { limit: ALL_ITEMS },
    }),
  });

  const [expanded, setExpanded] = useState<ExpandedState>(
    search.marked ? { [search.marked]: true } : {}
  );

  const table = useReactTable({
    data: vulnerabilities?.data || [],
    columns,
    state: {
      pagination: {
        pageIndex,
        pageSize,
      },
      columnFilters,
      columnVisibility: {
        [columnId]: false,
        rating: false,
        itemStatus: false,
        externalId: false,
        advisorName: false,
        summary: false,
      },
      sorting: sortBy,
      expanded: expanded,
    },
    onExpandedChange: setExpanded,
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
        <DataTableCards
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
  validateSearch: z.object({
    ...paginationSearchParameterSchema.shape,
    ...sortingSearchParameterSchema.shape,
    ...itemStatusSearchParameterSchema.shape,
    ...packageIdentifierSearchParameterSchema.shape,
    ...vulnerabilityRatingSearchParameterSchema.shape,
    ...markedSearchParameterSchema.shape,
  }),
  loader: async ({ context: { queryClient }, params }) => {
    await queryClient.prefetchQuery({
      ...getRepositoryRunOptions({
        path: {
          repositoryId: Number.parseInt(params.repoId),
          ortRunIndex: Number.parseInt(params.runIndex),
        },
      }),
    });
  },
  component: VulnerabilitiesComponent,
  pendingComponent: LoadingIndicator,
});
