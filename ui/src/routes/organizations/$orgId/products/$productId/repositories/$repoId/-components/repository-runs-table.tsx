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

import { Link } from '@tanstack/react-router';
import {
  createColumnHelper,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table';
import { Repeat, View } from 'lucide-react';

import { useRepositoriesServiceGetOrtRunsByRepositoryId } from '@/api/queries';
import { OrtRunSummary } from '@/api/requests';
import { DataTable } from '@/components/data-table/data-table';
import { LoadingIndicator } from '@/components/loading-indicator';
import { OrtRunJobStatus } from '@/components/ort-run-job-status';
import { RunDuration } from '@/components/run-duration';
import { TimestampWithUTC } from '@/components/timestamp-with-utc';
import { ToastError } from '@/components/toast-error';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { config } from '@/config';
import { getStatusBackgroundColor } from '@/helpers/get-status-class';
import { toast } from '@/lib/toast';

type RepositoryTableProps = {
  repoId: string;
  pageIndex: number;
  pageSize: number;
  search: {
    page?: number | undefined;
    pageSize?: number | undefined;
  };
};

const pollInterval = config.pollInterval;

const columnHelper = createColumnHelper<OrtRunSummary>();

const columns = [
  columnHelper.accessor('index', {
    header: 'Run',
    cell: ({ row }) => (
      <Link
        className='font-semibold text-blue-400 hover:underline'
        to={
          '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex'
        }
        params={{
          orgId: row.original.organizationId.toString(),
          productId: row.original.productId.toString(),
          repoId: row.original.repositoryId.toString(),
          runIndex: row.original.index.toString(),
        }}
      >
        {row.original.index}
      </Link>
    ),
    size: 50,
  }),
  columnHelper.accessor('createdAt', {
    header: 'Created At',
    cell: ({ row }) => <TimestampWithUTC timestamp={row.original.createdAt} />,
  }),
  columnHelper.accessor('status', {
    header: 'Run Status',
    cell: ({ row }) => (
      <Badge
        className={`border ${getStatusBackgroundColor(row.original.status)}`}
      >
        {row.original.status}
      </Badge>
    ),
  }),
  columnHelper.display({
    id: 'jobStatuses',
    header: () => <div>Job Status</div>,
    cell: ({ row }) => (
      <OrtRunJobStatus
        jobs={row.original.jobs}
        orgId={row.original.organizationId.toString()}
        productId={row.original.productId.toString()}
        repoId={row.original.repositoryId.toString()}
        runIndex={row.original.index.toString()}
      />
    ),
  }),
  // TODO: Write this with an accessor as soon as I know how to do it.
  columnHelper.display({
    id: 'duration',
    header: 'Duration',
    cell: ({ row }) => (
      <RunDuration
        createdAt={row.original.createdAt}
        finishedAt={row.original.finishedAt ?? undefined}
      />
    ),
  }),
  columnHelper.display({
    id: 'actions',
    header: () => <div>Actions</div>,
    cell: ({ row }) => (
      <div className='flex gap-2'>
        <Tooltip>
          <TooltipTrigger asChild>
            <Button variant='outline' asChild size='sm'>
              <Link
                to={
                  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex'
                }
                params={{
                  orgId: row.original.organizationId.toString(),
                  productId: row.original.productId.toString(),
                  repoId: row.original.repositoryId.toString(),
                  runIndex: row.original.index.toString(),
                }}
              >
                <View className='h-4 w-4' />
              </Link>
            </Button>
          </TooltipTrigger>
          <TooltipContent>View the details of this run</TooltipContent>
        </Tooltip>
        <Tooltip>
          <TooltipTrigger asChild>
            <Button variant='outline' asChild size='sm'>
              <Link
                to='/organizations/$orgId/products/$productId/repositories/$repoId/create-run'
                params={{
                  orgId: row.original.organizationId.toString(),
                  productId: row.original.productId.toString(),
                  repoId: row.original.repositoryId.toString(),
                }}
                search={{
                  rerunIndex: row.original.index,
                }}
              >
                <Repeat className='h-4 w-4' />
              </Link>
            </Button>
          </TooltipTrigger>
          <TooltipContent>
            Create a new ORT run based on this run
          </TooltipContent>
        </Tooltip>
      </div>
    ),
  }),
];

export const RepositoryRunsTable = ({
  repoId,
  pageIndex,
  pageSize,
  search,
}: RepositoryTableProps) => {
  const {
    data: runs,
    error: runsError,
    isPending: runsIsPending,
    isError: runsIsError,
  } = useRepositoriesServiceGetOrtRunsByRepositoryId(
    {
      repositoryId: Number.parseInt(repoId),
      limit: pageSize,
      offset: pageIndex * pageSize,
      sort: '-index',
    },
    undefined,
    {
      refetchInterval: pollInterval,
    }
  );

  const table = useReactTable({
    data: runs?.data || [],
    columns,
    pageCount: Math.ceil((runs?.pagination.totalCount ?? 0) / pageSize),
    state: {
      pagination: {
        pageIndex,
        pageSize,
      },
    },
    getCoreRowModel: getCoreRowModel(),
    manualPagination: true,
  });

  if (runsIsPending) {
    return <LoadingIndicator />;
  }

  if (runsIsError) {
    toast.error('Unable to load data', {
      description: <ToastError error={runsError} />,
      duration: Infinity,
      cancel: {
        label: 'Dismiss',
        onClick: () => {},
      },
    });
    return;
  }

  return (
    <DataTable
      table={table}
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
    />
  );
};
