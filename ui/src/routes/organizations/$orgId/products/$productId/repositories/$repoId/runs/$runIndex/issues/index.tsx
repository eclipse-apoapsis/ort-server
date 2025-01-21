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

import { useIssuesServiceGetIssuesByRunId } from '@/api/queries';
import { prefetchUseRepositoriesServiceGetOrtRunByIndex } from '@/api/queries/prefetch';
import { useRepositoriesServiceGetOrtRunByIndexSuspense } from '@/api/queries/suspense';
import { Issue, Severity } from '@/api/requests';
import { DataTable } from '@/components/data-table/data-table';
import { LoadingIndicator } from '@/components/loading-indicator';
import { TimestampWithUTC } from '@/components/timestamp-with-utc';
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
import { getIssueSeverityBackgroundColor } from '@/helpers/get-status-class';
import { updateColumnSorting } from '@/helpers/handle-multisort';
import { identifierToString } from '@/helpers/identifier-to-string';
import { compareSeverity } from '@/helpers/sorting-functions';
import { ALL_ITEMS } from '@/lib/constants';
import { toast } from '@/lib/toast';
import {
  paginationSearchParameterSchema,
  severitySchema,
  severitySearchParameterSchema,
  sortingSearchParameterSchema,
} from '@/schemas';

const defaultPageSize = 10;

const columnHelper = createColumnHelper<Issue>();

const renderSubComponent = ({ row }: { row: Row<Issue> }) => {
  const issue = row.original;

  return (
    <div className='flex flex-col gap-4'>
      <div className='flex gap-1 text-sm'>
        <div className='font-semibold'>Created at</div>
        <TimestampWithUTC timestamp={issue.timestamp} />
        <div>by</div>
        <div className='font-semibold'>{issue.worker}</div>
      </div>
      <div className='whitespace-pre-line break-all italic text-muted-foreground'>
        {issue.message || 'No details.'}
      </div>
    </div>
  );
};

const IssuesComponent = () => {
  const params = Route.useParams();
  const search = Route.useSearch();
  const navigate = Route.useNavigate();

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
      enableColumnFilter: false,
    }),
    columnHelper.accessor('severity', {
      header: 'Severity',
      cell: ({ row }) => (
        <Badge
          className={`border ${getIssueSeverityBackgroundColor(row.original.severity)}`}
        >
          {row.original.severity}
        </Badge>
      ),
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
      (issue) => {
        return identifierToString(issue.identifier);
      },
      {
        id: 'package',
        header: 'Package ID',
        cell: ({ row }) => {
          return <div className='font-semibold'>{row.getValue('package')}</div>;
        },
        enableColumnFilter: false,
      }
    ),
    columnHelper.accessor('affectedPath', {
      header: 'Affected Path',
      cell: ({ row }) => (
        <div className='break-all'>{row.original.affectedPath}</div>
      ),
      enableSorting: false,
      enableColumnFilter: false,
    }),
    columnHelper.accessor('source', {
      header: 'Source',
      cell: ({ row }) => row.original.source,
      enableColumnFilter: false,
    }),
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

  const {
    data: issues,
    isPending,
    isError,
    error,
  } = useIssuesServiceGetIssuesByRunId({
    runId: ortRun.id,
    limit: ALL_ITEMS,
  });

  const table = useReactTable({
    data: issues?.data || [],
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
        <CardTitle>Issues ({issues.pagination.totalCount} in total)</CardTitle>
        <CardDescription>
          This view shows any technical issues that were discovered by the
          respective source. As technical issues might have an impact on the
          completeness of metadata, it is highly recommended to resolve any
          technical issues before acting on any compliance related findings.
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
  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/issues/'
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
  component: IssuesComponent,
  pendingComponent: LoadingIndicator,
});
