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
import {
  createColumnHelper,
  ExpandedState,
  getCoreRowModel,
  getExpandedRowModel,
  Row,
  useReactTable,
} from '@tanstack/react-table';
import { ChevronDown, ChevronUp } from 'lucide-react';
import { useMemo, useState } from 'react';
import z from 'zod';

import { ProductVulnerability, VulnerabilityRating } from '@/api';
import {
  getProductByIdOptions,
  getVulnerabilitiesAcrossRepositoriesByProductIdOptions,
} from '@/api/@tanstack/react-query.gen';
import { zVulnerabilityRating } from '@/api/zod.gen';
import { BreakableString } from '@/components/breakable-string';
import { VulnerabilityMetrics } from '@/components/charts/vulnerability-metrics';
import { DataTableCards } from '@/components/data-table-cards/data-table-cards';
import { MarkItems } from '@/components/data-table/mark-items';
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
import {
  convertToBackendSorting,
  updateColumnSorting,
} from '@/helpers/handle-multisort';
import { identifierToString } from '@/helpers/identifier-conversion';
import { ACTION_COLUMN_SIZE } from '@/lib/constants';
import { toast } from '@/lib/toast';
import {
  externalIdSearchParameterSchema,
  markedSearchParameterSchema,
  packageIdentifierSearchParameterSchema,
  paginationSearchParameterSchema,
  sortingSearchParameterSchema,
  vulnerabilityRatingSearchParameterSchema,
} from '@/schemas';
import { useUserSettingsStore } from '@/store/user-settings.store';

const defaultPageSize = 10;

const columnHelper = createColumnHelper<ProductVulnerability>();

// Component to render a single vulnerability card in the list.
const VulnerabilityCard = ({
  vulnerability,
}: {
  vulnerability: ProductVulnerability;
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
        <div className='flex gap-1'>
          <div className='font-semibold'>{vulnerability.repositoriesCount}</div>
          <div className='text-muted-foreground'>
            {vulnerability.repositoriesCount === 1
              ? 'repository'
              : 'repositories'}
          </div>
        </div>
      </div>

      <div className='flex gap-2'>
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

const renderSubComponent = ({ row }: { row: Row<ProductVulnerability> }) => {
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

const ProductVulnerabilitiesComponent = () => {
  const params = Route.useParams();
  const search = Route.useSearch();
  const navigate = Route.useNavigate();
  const packageIdType = useUserSettingsStore((state) => state.packageIdType);

  // Prevent infinite rerenders by providing a stable reference to columns via memoization.
  // https://tanstack.com/table/latest/docs/faq#solution-1-stable-references-with-usememo-or-usestate
  const columns = useMemo(
    () => [
      columnHelper.display({
        id: 'moreInfo',
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
        enableSorting: false,
        enableColumnFilter: false,
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
          id: packageIdType === 'ORT_ID' ? 'identifier' : 'purl',
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
      columnHelper.accessor('rating', {
        id: 'rating',
        header: 'Rating',
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
      columnHelper.accessor('repositoriesCount', {
        id: 'repositoriesCount',
        header: 'Repositories',
        enableColumnFilter: false,
      }),
      columnHelper.accessor('vulnerability.externalId', {
        id: 'externalId',
        header: 'External ID',
        meta: {
          filter: {
            filterVariant: 'text',
            setFilterValue: (value: string | undefined) => {
              navigate({
                search: { ...search, page: 1, externalId: value },
              });
            },
          },
        },
      }),
      columnHelper.accessor('vulnerability.summary', {
        id: 'summary',
        header: 'Summary',
        enableSorting: false,
        enableColumnFilter: false,
      }),
    ],
    [navigate, packageIdType, search]
  );

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

  const externalId = useMemo(
    () => (search.externalId ? search.externalId : undefined),
    [search.externalId]
  );

  const columnFilters = useMemo(() => {
    const filters = [];
    if (packageIdentifier) {
      filters.push({
        id: packageIdType === 'ORT_ID' ? 'identifier' : 'purl',
        value: packageIdentifier,
      });
    }
    if (rating) {
      filters.push({ id: 'rating', value: rating });
    }
    if (externalId) {
      filters.push({ id: 'externalId', value: externalId });
    }
    return filters;
  }, [packageIdentifier, rating, packageIdType, externalId]);

  const sortBy = useMemo(
    () => (search.sortBy ? search.sortBy : undefined),
    [search.sortBy]
  );

  const {
    data: totalVulnerabilities,
    isPending: totIsPending,
    isError: totIsError,
    error: totError,
  } = useQuery({
    ...getVulnerabilitiesAcrossRepositoriesByProductIdOptions({
      path: { productId: Number.parseInt(params.productId) },
      query: { limit: 1 },
    }),
  });

  const {
    data: vulnerabilities,
    isPending,
    isError,
    error,
  } = useQuery({
    ...getVulnerabilitiesAcrossRepositoriesByProductIdOptions({
      path: { productId: Number.parseInt(params.productId) },
      query: {
        limit: pageSize,
        offset: pageIndex * pageSize,
        sort: convertToBackendSorting(sortBy),
        rating: rating?.join(','),
        ...(packageIdType === 'ORT_ID'
          ? { identifier: packageIdentifier }
          : { purl: packageIdentifier }),
        externalId: externalId,
      },
    }),
  });

  const [expanded, setExpanded] = useState<ExpandedState>(
    search.marked ? { [search.marked]: true } : {}
  );

  const columnId = packageIdType === 'ORT_ID' ? 'identifier' : 'purl';

  const table = useReactTable({
    data: vulnerabilities?.data || [],
    columns,
    pageCount: Math.ceil(
      (vulnerabilities?.pagination.totalCount ?? 0) / pageSize
    ),
    state: {
      pagination: {
        pageIndex,
        pageSize,
      },
      columnFilters,
      columnVisibility: {
        [columnId]: false,
        rating: false,
        repositoriesCount: false,
        externalId: false,
        summary: false,
      },
      sorting: sortBy,
      expanded: expanded,
    },
    onExpandedChange: setExpanded,
    getCoreRowModel: getCoreRowModel(),
    getExpandedRowModel: getExpandedRowModel(),
    getRowCanExpand: () => true,
    manualPagination: true,
  });

  if (isPending || totIsPending) {
    return <LoadingIndicator />;
  }

  if (isError || totIsError) {
    toast.error('Unable to load data', {
      description: <ToastError error={error || totError} />,
      duration: Infinity,
      cancel: {
        label: 'Dismiss',
        onClick: () => {},
      },
    });
    return;
  }
  const filtersInUse =
    totalVulnerabilities.pagination.totalCount !==
    vulnerabilities.pagination.totalCount;
  const matching = `, ${vulnerabilities.pagination.totalCount} matching filters`;

  return (
    <Card className='h-fit'>
      <CardHeader>
        <CardTitle>
          Vulnerabilities ({totalVulnerabilities.pagination.totalCount} in total
          {filtersInUse && matching})
        </CardTitle>
        <CardDescription>
          These are the vulnerabilities found currently from this product.
          Please note that the vulnerability status may change over time, as
          your dependencies change. Therefore, your product repositories should
          be scanned for vulnerabilities regularly.
        </CardDescription>
        <CardDescription>
          By clicking on "References" you can see more information about the
          vulnerability. The overall severity rating is calculated based on the
          highest severity rating found in the references.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <DataTableCards
          table={table}
          renderSubComponent={renderSubComponent}
          setCurrentPageOptions={(currentPage) => {
            return {
              search: { ...search, page: currentPage },
            };
          }}
          setPageSizeOptions={(size) => {
            return {
              search: { ...search, page: 1, pageSize: size },
            };
          }}
          setSortingOptions={(sortBy) => {
            return {
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
  '/organizations/$orgId/products/$productId/vulnerabilities/'
)({
  validateSearch: z.object({
    ...paginationSearchParameterSchema.shape,
    ...sortingSearchParameterSchema.shape,
    ...packageIdentifierSearchParameterSchema.shape,
    ...vulnerabilityRatingSearchParameterSchema.shape,
    ...externalIdSearchParameterSchema.shape,
    ...markedSearchParameterSchema.shape,
  }),
  loader: async ({ context: { queryClient }, params }) => {
    await queryClient.prefetchQuery({
      ...getProductByIdOptions({
        path: { productId: Number.parseInt(params.productId) },
      }),
    });
  },
  component: ProductVulnerabilitiesComponent,
  pendingComponent: LoadingIndicator,
});
