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
  getPaginationRowModel,
  getSortedRowModel,
  Row,
  useReactTable,
} from '@tanstack/react-table';
import { ChevronDown, ChevronUp } from 'lucide-react';
import { useMemo } from 'react';

import { useVulnerabilitiesServiceGetVulnerabilitiesByRunId } from '@/api/queries';
import { prefetchUseRepositoriesServiceGetOrtRunByIndex } from '@/api/queries/prefetch';
import { useRepositoriesServiceGetOrtRunByIndexSuspense } from '@/api/queries/suspense';
import { VulnerabilityWithIdentifier } from '@/api/requests';
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
import { calcOverallVulnerability } from '@/helpers/calc-overall-vulnerability';
import { getVulnerabilityRatingBackgroundColor } from '@/helpers/get-status-class';
import { updateColumnSorting } from '@/helpers/handle-multisort';
import { identifierToString } from '@/helpers/identifier-to-string';
import { compareVulnerabilityRating } from '@/helpers/sorting-functions';
import { ALL_ITEMS } from '@/lib/constants';
import { toast } from '@/lib/toast';
import {
  paginationSearchParameterSchema,
  sortingSearchParameterSchema,
} from '@/schemas';

const defaultPageSize = 10;

const columnHelper = createColumnHelper<VulnerabilityWithIdentifier>();

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
  }),
  columnHelper.accessor(
    (vuln) => {
      const ratings = vuln.vulnerability.references.map(
        (reference) => reference.severity
      );
      return calcOverallVulnerability(ratings);
    },
    {
      id: 'rating',
      header: 'Rating',
      cell: ({ row }) => {
        return (
          <Badge
            className={`${getVulnerabilityRatingBackgroundColor(row.getValue('rating'))}`}
          >
            {row.getValue('rating')}
          </Badge>
        );
      },
      sortingFn: (rowA, rowB) => {
        return compareVulnerabilityRating(
          rowA.getValue('rating'),
          rowB.getValue('rating')
        );
      },
    }
  ),
  columnHelper.accessor(
    (vuln) => {
      return identifierToString(vuln.identifier);
    },
    {
      id: 'package',
      header: 'Package',
      cell: ({ row }) => {
        return <div className='font-semibold'>{row.getValue('package')}</div>;
      },
    }
  ),
  columnHelper.accessor('vulnerability.externalId', {
    id: 'externalId',
    header: 'External ID',
    cell: ({ row }) => (
      <Badge className='whitespace-nowrap bg-blue-300'>
        {row.getValue('externalId')}
      </Badge>
    ),
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
          <div className='italic text-muted-foreground'>
            {row.getValue('summary')}
          </div>
        );
      },
      enableSorting: false,
    }
  ),
];

const renderSubComponent = ({
  row,
}: {
  row: Row<VulnerabilityWithIdentifier>;
}) => {
  const vulnerability = row.original.vulnerability;

  return (
    <div className='flex flex-col gap-4'>
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
          {vulnerability.references.map((reference, index) => (
            <TableRow key={index}>
              <TableCell>{reference.severity || '-'}</TableCell>
              <TableCell>{reference.scoringSystem || '-'}</TableCell>
              <TableCell>{reference.score || '-'}</TableCell>
              <TableCell>{reference.vector || '-'}</TableCell>
              <TableCell>
                {
                  <Link
                    className='break-all font-semibold text-blue-400 hover:underline'
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

  const sortBy = useMemo(
    () => (search.sortBy ? search.sortBy : undefined),
    [search.sortBy]
  );

  const { data: ortRun } = useRepositoriesServiceGetOrtRunByIndexSuspense({
    repositoryId: Number.parseInt(params.repoId),
    ortRunIndex: Number.parseInt(params.runIndex),
  });

  const {
    data: vulnerabilities,
    isPending,
    isError,
    error,
  } = useVulnerabilitiesServiceGetVulnerabilitiesByRunId({
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
      sorting: sortBy,
    },
    getCoreRowModel: getCoreRowModel(),
    getExpandedRowModel: getExpandedRowModel(),
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

  return (
    <Card className='h-fit'>
      <CardHeader>
        <CardTitle>Vulnerabilities (ORT run global ID: {ortRun.id})</CardTitle>
        <CardDescription>
          These are the vulnerabilities found currently in the project. Please
          note that the vulnerability status may change over time, as your
          project dependencies change. Therefore, your project should be scanned
          for vulnerabilities regularly.
        </CardDescription>
        <CardDescription>
          By clicking on "References" you can see more information about the
          vulnerability. The overall severity rating is calculated based on the
          highest severity rating found in the references.
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
  validateSearch: paginationSearchParameterSchema.merge(
    sortingSearchParameterSchema
  ),
  loader: async ({ context, params }) => {
    await prefetchUseRepositoriesServiceGetOrtRunByIndex(context.queryClient, {
      repositoryId: Number.parseInt(params.repoId),
      ortRunIndex: Number.parseInt(params.runIndex),
    });
  },
  component: VulnerabilitiesComponent,
  pendingComponent: LoadingIndicator,
});
