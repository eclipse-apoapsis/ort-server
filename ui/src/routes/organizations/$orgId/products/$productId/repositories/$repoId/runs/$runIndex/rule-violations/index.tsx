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
  getFilteredRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  Row,
  useReactTable,
} from '@tanstack/react-table';
import { ChevronDown, ChevronUp } from 'lucide-react';
import { useMemo, useState } from 'react';
import z from 'zod';

import { RuleViolation, Severity } from '@/api';
import {
  getOrtRunByIndexOptions,
  getRuleViolationsByRunIdOptions,
} from '@/api/@tanstack/react-query.gen';
import { BreakableString } from '@/components/breakable-string';
import { DataTable } from '@/components/data-table/data-table';
import { MarkItems } from '@/components/data-table/mark-items';
import { FormattedValue } from '@/components/formatted-value';
import { LoadingIndicator } from '@/components/loading-indicator';
import { MarkdownRenderer } from '@/components/markdown-renderer';
import { Resolutions } from '@/components/resolutions';
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
  getResolvedBackgroundColor,
  getRuleViolationSeverityBackgroundColor,
} from '@/helpers/get-status-class';
import { updateColumnSorting } from '@/helpers/handle-multisort';
import { identifierToString } from '@/helpers/identifier-conversion';
import { getResolvedStatus } from '@/helpers/resolutions';
import { compareSeverity } from '@/helpers/sorting-functions';
import { ALL_ITEMS } from '@/lib/constants';
import {
  ItemResolved,
  itemResolvedSchema,
  itemStatusSearchParameterSchema,
  markedSearchParameterSchema,
  packageIdentifierSearchParameterSchema,
  paginationSearchParameterSchema,
  severitySchema,
  severitySearchParameterSchema,
  sortingSearchParameterSchema,
} from '@/schemas';
import { useUserSettingsStore } from '@/store/user-settings.store';

const defaultPageSize = 10;

const columnHelper = createColumnHelper<RuleViolation>();

const renderSubComponent = ({ row }: { row: Row<RuleViolation> }) => {
  const ruleViolation = row.original;
  const hasResolutions = getResolvedStatus(ruleViolation) === 'Resolved';

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
            <div>{ruleViolation.message}</div>
            <div className='grid grid-cols-8 gap-2'>
              <div className='col-span-2 font-semibold'>License:</div>
              <div className='col-span-6'>
                <FormattedValue value={ruleViolation.license} />
              </div>
              <div className='col-span-2 font-semibold'>License source:</div>
              <div className='col-span-6'>
                <FormattedValue value={ruleViolation.licenseSource} />
              </div>
              <div className='col-span-2 font-semibold'>How to fix:</div>
            </div>
            <MarkdownRenderer markdown={ruleViolation.howToFix} />
          </div>
        </AccordionContent>
      </AccordionItem>
    </Accordion>
  );
};

const RuleViolationsComponent = () => {
  const params = Route.useParams();
  const search = Route.useSearch();
  const navigate = Route.useNavigate();
  const packageIdType = useUserSettingsStore((state) => state.packageIdType);

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
    columnHelper.accessor('severity', {
      header: 'Severity',
      cell: ({ row }) => {
        return (
          <Badge
            className={`${getRuleViolationSeverityBackgroundColor(row.original.severity)}`}
          >
            {row.original.severity}
          </Badge>
        );
      },
      filterFn: (row, _columnId, filterValue): boolean => {
        return filterValue.includes(row.original.severity);
      },
      sortingFn: (rowA, rowB) => {
        return compareSeverity(rowA.original.severity, rowB.original.severity);
      },
      meta: {
        filter: {
          filterVariant: 'select',
          selectOptions: severitySchema.options.map((severity) => ({
            label: severity,
            value: severity,
          })),
          setSelected: (severities: Severity[]) => {
            navigate({
              search: {
                ...search,
                page: 1,
                severity: severities.length === 0 ? undefined : severities,
              },
            });
          },
        },
      },
    }),
    columnHelper.accessor(
      (ruleViolation) => {
        return getResolvedStatus(ruleViolation);
      },
      {
        id: 'itemStatus',
        header: 'Status',
        size: 50,
        cell: ({ getValue }) => {
          return (
            <Badge
              className={`border ${getResolvedBackgroundColor(getValue())}`}
            >
              {getValue()}
            </Badge>
          );
        },
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
    columnHelper.accessor(
      (ruleViolation) => {
        // Return purl only if the rule violation has been reported for a package
        if (packageIdType === 'PURL' && ruleViolation.purl) {
          return ruleViolation.purl;
        } else {
          return identifierToString(ruleViolation.id);
        }
      },
      {
        id: `${packageIdType === 'PURL' ? 'purl' : 'identifier'}`,
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
    columnHelper.accessor('rule', {
      header: 'Rule',
      cell: ({ row }) => (
        <Badge className='bg-blue-300 whitespace-nowrap'>
          {row.original.rule}
        </Badge>
      ),
      enableColumnFilter: false,
    }),
  ];

  // Memoize the search parameters to prevent unnecessary re-rendering

  const pageIndex = useMemo(
    () => (search.page ? search.page - 1 : 0),
    [search.page]
  );

  const pageSize = useMemo(
    () => (search.pageSize ? search.pageSize : defaultPageSize),
    [search.pageSize]
  );

  const severity = useMemo(
    () => (search.severity ? search.severity : undefined),
    [search.severity]
  );

  const itemStatus = useMemo(
    () => (search.itemResolved ? search.itemResolved : undefined),
    [search.itemResolved]
  );

  const packageIdentifier = useMemo(
    () => (search.pkgId ? search.pkgId : undefined),
    [search.pkgId]
  );

  const columnId = packageIdType === 'ORT_ID' ? 'identifier' : 'purl';

  const columnFilters = useMemo(() => {
    const filters = [];
    if (severity) {
      filters.push({ id: 'severity', value: severity });
    }
    if (itemStatus) {
      filters.push({ id: 'itemStatus', value: itemStatus });
    }
    if (packageIdentifier) {
      filters.push({ id: columnId, value: packageIdentifier });
    }
    return filters;
  }, [severity, itemStatus, packageIdentifier, columnId]);

  const sortBy = useMemo(
    () => (search.sortBy ? search.sortBy : undefined),
    [search.sortBy]
  );

  const { data: ortRun } = useSuspenseQuery({
    ...getOrtRunByIndexOptions({
      path: {
        repositoryId: Number.parseInt(params.repoId),
        ortRunIndex: Number.parseInt(params.runIndex),
      },
    }),
  });

  const { data: ruleViolations } = useSuspenseQuery({
    ...getRuleViolationsByRunIdOptions({
      path: { runId: ortRun.id },
      query: { limit: ALL_ITEMS },
    }),
  });

  const [expanded, setExpanded] = useState<ExpandedState>(
    search.marked ? { [search.marked]: true } : {}
  );

  const table = useReactTable({
    data: ruleViolations?.data || [],
    columns,
    state: {
      pagination: {
        pageIndex,
        pageSize,
      },
      columnFilters,
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
  });
  const filtersInUse = table.getState().columnFilters.length > 0;
  const matching = `, ${table.getPrePaginationRowModel().rows.length} matching filters`;

  return (
    <Card className='h-fit'>
      <CardHeader>
        <CardTitle>
          Rule Violations ({ruleViolations.pagination.totalCount} in total
          {filtersInUse && matching})
        </CardTitle>
        <CardDescription>
          This view shows all violations that go against the rules defined in
          the configured policy.
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
  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/rule-violations/'
)({
  validateSearch: z.object({
    ...paginationSearchParameterSchema.shape,
    ...severitySearchParameterSchema.shape,
    ...itemStatusSearchParameterSchema.shape,
    ...packageIdentifierSearchParameterSchema.shape,
    ...sortingSearchParameterSchema.shape,
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
  component: RuleViolationsComponent,
  pendingComponent: LoadingIndicator,
});
