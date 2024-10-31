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
  ColumnDef,
  getCoreRowModel,
  getExpandedRowModel,
  Row,
  useReactTable,
} from '@tanstack/react-table';
import { Minus, Plus } from 'lucide-react';

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
import { toast } from '@/lib/toast';
import { paginationSchema } from '@/schemas';

const defaultPageSize = 10;

const columns: ColumnDef<VulnerabilityWithIdentifier>[] = [
  {
    accessorKey: 'package',
    header: 'Package',
    cell: ({ row }) => {
      const { type, namespace, name, version } = row.original.identifier;

      return (
        <div className='font-semibold'>
          {type ? type.concat(':') : ''}
          {namespace ? namespace.concat('/') : ''}
          {name ? name : ''}
          {version ? '@'.concat(version) : ''}
        </div>
      );
    },
  },
  {
    accessorKey: 'externalId',
    header: 'External ID',
    cell: ({ row }) => (
      <Badge className='whitespace-nowrap bg-blue-300'>
        {row.original.vulnerability.externalId}
      </Badge>
    ),
  },
  {
    accessorKey: 'rating',
    header: 'Rating',
    cell: ({ row }) => {
      // Calculate the overall vulnerability rating based on the individual ratings
      const ratings = row.original.vulnerability.references.map(
        (reference) => reference.severity
      );
      const overallRating = calcOverallVulnerability(ratings);

      return (
        <Badge
          className={`${getVulnerabilityRatingBackgroundColor(overallRating)}`}
        >
          {overallRating}
        </Badge>
      );
    },
  },
  {
    accessorKey: 'summary',
    header: 'Summary',
    cell: ({ row }) => {
      return (
        <div className='italic text-muted-foreground'>
          {row.original.vulnerability.summary}
        </div>
      );
    },
  },
  {
    id: 'moreInfo',
    header: () => null,
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
            <Minus className='h-4 w-4' />
          ) : (
            <Plus className='h-4 w-4' />
          )}
        </Button>
      ) : (
        'No info'
      );
    },
  },
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
  const pageIndex = search.page ? search.page - 1 : 0;
  const pageSize = search.pageSize ? search.pageSize : defaultPageSize;

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
    limit: pageSize,
    offset: pageIndex * pageSize,
  });

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
    },
    getCoreRowModel: getCoreRowModel(),
    getRowCanExpand: () => true,
    getExpandedRowModel: getExpandedRowModel(),
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
          />
        </CardContent>
      </CardContent>
    </Card>
  );
};

export const Route = createFileRoute(
  '/_layout/organizations/$orgId/products/$productId/repositories/$repoId/_layout/runs/$runIndex/vulnerabilities/'
)({
  validateSearch: paginationSchema,
  loader: async ({ context, params }) => {
    await prefetchUseRepositoriesServiceGetOrtRunByIndex(context.queryClient, {
      repositoryId: Number.parseInt(params.repoId),
      ortRunIndex: Number.parseInt(params.runIndex),
    });
  },
  component: VulnerabilitiesComponent,
  pendingComponent: LoadingIndicator,
});
