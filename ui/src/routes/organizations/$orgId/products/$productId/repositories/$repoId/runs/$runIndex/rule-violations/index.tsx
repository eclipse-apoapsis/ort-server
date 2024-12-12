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
  getFilteredRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  Row,
  useReactTable,
} from '@tanstack/react-table';
import { ChevronDown, ChevronUp } from 'lucide-react';
import { useMemo } from 'react';

import { prefetchUseRepositoriesServiceGetOrtRunByIndex } from '@/api/queries/prefetch';
import {
  useRepositoriesServiceGetOrtRunByIndexSuspense,
  useRuleViolationsServiceGetRuleViolationsByRunIdSuspense,
} from '@/api/queries/suspense';
import { RuleViolation } from '@/api/requests';
import { DataTable } from '@/components/data-table/data-table';
import { DataTableToolbar } from '@/components/data-table/data-table-toolbar';
import { FilterMultiSelect } from '@/components/data-table/filter-multi-select';
import { LoadingIndicator } from '@/components/loading-indicator';
import { MarkdownRenderer } from '@/components/markdown-renderer';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { getRuleViolationSeverityBackgroundColor } from '@/helpers/get-status-class';
import { updateColumnSorting } from '@/helpers/handle-multisort';
import { identifierToString } from '@/helpers/identifier-to-string';
import { compareSeverity } from '@/helpers/sorting-functions';
import { ALL_ITEMS } from '@/lib/constants';
import {
  paginationSearchParameterSchema,
  severitySchema,
  severitySearchParameterSchema,
  sortingSearchParameterSchema,
} from '@/schemas';

const defaultPageSize = 10;

const columnHelper = createColumnHelper<RuleViolation>();

const columns = [
  columnHelper.display({
    id: 'moreInfo',
    header: 'Details',
    size: 50,
    cell: function CellComponent({ row }) {
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
  }),
  columnHelper.accessor(
    (ruleViolation) => {
      return identifierToString(ruleViolation.packageId);
    },
    {
      header: 'Package',
      cell: ({ getValue }) => {
        return <div className='font-semibold'>{getValue()}</div>;
      },
    }
  ),
  columnHelper.accessor('rule', {
    header: 'Rule',
    cell: ({ row }) => (
      <Badge className='whitespace-nowrap bg-blue-300'>
        {row.original.rule}
      </Badge>
    ),
  }),
];

const renderSubComponent = ({ row }: { row: Row<RuleViolation> }) => {
  const ruleViolation = row.original;

  return (
    <div className='flex flex-col gap-4'>
      <div>{ruleViolation.message}</div>
      <div className='grid grid-cols-8 gap-2'>
        <div className='col-span-2 font-semibold'>License:</div>
        <div className='col-span-6'>{ruleViolation.license}</div>
        <div className='col-span-2 font-semibold'>License source:</div>
        <div className='col-span-6'>{ruleViolation.licenseSource}</div>
        <div className='col-span-2 font-semibold'>How to fix:</div>
      </div>
      <MarkdownRenderer markdown={ruleViolation.howToFix} />
    </div>
  );
};

const RuleViolationsComponent = () => {
  const params = Route.useParams();
  const search = Route.useSearch();
  const navigate = Route.useNavigate();

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

  const columnFilters = useMemo(
    () => (severity ? [{ id: 'severity', value: severity }] : []),
    [severity]
  );
  const sortBy = useMemo(
    () => (search.sortBy ? search.sortBy : undefined),
    [search.sortBy]
  );

  const { data: ortRun } = useRepositoriesServiceGetOrtRunByIndexSuspense({
    repositoryId: Number.parseInt(params.repoId),
    ortRunIndex: Number.parseInt(params.runIndex),
  });

  const { data: ruleViolations } =
    useRuleViolationsServiceGetRuleViolationsByRunIdSuspense({
      runId: ortRun.id,
      limit: ALL_ITEMS,
    });

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
    },
    getCoreRowModel: getCoreRowModel(),
    getExpandedRowModel: getExpandedRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getRowCanExpand: () => true,
  });

  return (
    <Card className='h-fit'>
      <CardHeader>
        <CardTitle>Rule violations (ORT run global ID: {ortRun.id})</CardTitle>
        <CardDescription>
          These are the rule violations found from the ORT run. Please note that
          the status may change over time, as your project dependencies change.
          Therefore, your project should be scanned for rule violations
          regularly.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <DataTableToolbar
          filters={
            <FilterMultiSelect
              title='Rule Violation Severity'
              options={severitySchema.options.map((severity) => ({
                label: severity,
                value: severity,
              }))}
              selected={severity || []}
              setSelected={(severities) => {
                navigate({
                  search: {
                    ...search,
                    page: 1,
                    severity: severities.length === 0 ? undefined : severities,
                  },
                });
              }}
            />
          }
          resetFilters={() => {
            navigate({
              search: { ...search, page: 1, severity: undefined },
            });
          }}
          resetBtnVisible={severity !== undefined}
          className='mb-2'
        />
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
  validateSearch: paginationSearchParameterSchema
    .merge(severitySearchParameterSchema)
    .merge(sortingSearchParameterSchema),
  loader: async ({ context, params }) => {
    await prefetchUseRepositoriesServiceGetOrtRunByIndex(context.queryClient, {
      repositoryId: Number.parseInt(params.repoId),
      ortRunIndex: Number.parseInt(params.runIndex),
    });
  },
  component: RuleViolationsComponent,
  pendingComponent: LoadingIndicator,
});
