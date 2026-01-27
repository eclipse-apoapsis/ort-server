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

import {
  useMutation,
  useQuery,
  useQueryClient,
  useSuspenseQuery,
} from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import {
  createColumnHelper,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table';
import { Repeat, View } from 'lucide-react';

import { OrtRunSummary } from '@/api';
import {
  deleteRepositoryRunMutation,
  getRepositoryOptions,
  getRepositoryRunsOptions,
  getRepositoryRunsQueryKey,
} from '@/api/@tanstack/react-query.gen';
import { DataTable } from '@/components/data-table/data-table';
import { DeleteDialog } from '@/components/delete-dialog';
import { DeleteIconButton } from '@/components/delete-icon-button';
import { LoadingIndicator } from '@/components/loading-indicator';
import { OrtRunJobStatus } from '@/components/ort-run-job-status';
import { RunDuration } from '@/components/run-duration';
import { Sha1Component } from '@/components/sha1-component';
import { TimestampWithUTC } from '@/components/timestamp-with-utc';
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
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { config } from '@/config';
import { getStatusBackgroundColor } from '@/helpers/get-status-class';
import { ApiError } from '@/lib/api-error';
import { toast } from '@/lib/toast';
import { useTablePrefsStore } from '@/store/table-prefs.store';
import { ItemCounts } from './item-counts';

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

const SummaryCard = ({ summary }: { summary: OrtRunSummary }) => {
  const hasLabels = summary.labels && Object.keys(summary.labels).length > 0;

  return (
    <div className='grid grid-cols-12 gap-2'>
      {/* Left column - status, job status, duration */}
      <div className='col-span-4 flex flex-col gap-1'>
        <Badge
          className={`border ${getStatusBackgroundColor(summary.status)} w-fit`}
        >
          {summary.status}
        </Badge>
        <div className='col-span-2 flex flex-col items-start justify-center gap-2'>
          <ItemCounts summary={summary} />
        </div>
        <OrtRunJobStatus
          jobs={summary.jobs}
          orgId={summary.organizationId.toString()}
          productId={summary.productId.toString()}
          repoId={summary.repositoryId.toString()}
          runIndex={summary.index.toString()}
        />
        <RunDuration
          createdAt={summary.createdAt}
          finishedAt={summary.finishedAt ?? undefined}
        />
      </div>

      {/* Middle column - reserved for future use */}
      <div className='col-span-2'></div>

      {/* Right column - created at, revision, configuration */}
      <div className='col-span-6 flex flex-col items-end gap-1'>
        <div className='flex gap-1'>
          <div className='text-muted-foreground'>Created at</div>
          <TimestampWithUTC timestamp={summary.createdAt} />
          <div className='text-muted-foreground'>by</div>
          {summary.userDisplayName?.username ? (
            <Tooltip>
              <TooltipTrigger>
                {summary.userDisplayName.fullName ||
                  summary.userDisplayName.username}
              </TooltipTrigger>
              <TooltipContent>
                {summary.userDisplayName.username}
              </TooltipContent>
            </Tooltip>
          ) : (
            <span>{summary.userDisplayName?.fullName}</span>
          )}
        </div>
        <div className='flex gap-1 text-sm'>
          <div className='text-muted-foreground'>Revision</div>{' '}
          {summary.revision}
          {summary.resolvedRevision &&
            summary.revision !== summary.resolvedRevision && (
              <Sha1Component sha1={summary.resolvedRevision} />
            )}
        </div>
        <Accordion type='multiple' className='w-full rounded-sm'>
          <AccordionItem value='configuration'>
            <AccordionTrigger className='justify-end gap-2'>
              <div className='text-sm'>Configuration</div>
            </AccordionTrigger>
            <AccordionContent>
              <div className='flex flex-col items-end gap-1'>
                <div className='flex gap-2 text-sm'>
                  <div className='text-muted-foreground'>Context:</div>{' '}
                  {summary.jobConfigContext}
                  {summary.resolvedJobConfigContext &&
                    summary.jobConfigContext !==
                      summary.resolvedJobConfigContext && (
                      <Sha1Component sha1={summary.resolvedJobConfigContext} />
                    )}
                </div>
                {hasLabels && (
                  <div className='flex flex-wrap justify-end gap-2'>
                    {Object.entries(summary.labels!).map(([key, value]) => (
                      <Badge
                        key={key}
                        className='bg-gray-200 text-gray-800 dark:bg-gray-700 dark:text-gray-200'
                      >
                        {key}: {value}
                      </Badge>
                    ))}
                  </div>
                )}
              </div>
            </AccordionContent>
          </AccordionItem>
        </Accordion>
      </div>
    </div>
  );
};

const columns = [
  columnHelper.accessor('index', {
    header: 'Index',
    size: 50,
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
    enableColumnFilter: false,
  }),
  columnHelper.display({
    id: 'card',
    header: 'Run Details',
    cell: ({ row }) => <SummaryCard summary={row.original} />,
    meta: {
      isGrow: true,
    },
    enableColumnFilter: false,
  }),
  columnHelper.display({
    id: 'actions',
    header: 'Actions',
    size: 70,
    cell: function Row({ row }) {
      const queryClient = useQueryClient();

      const repository = useSuspenseQuery({
        ...getRepositoryOptions({
          path: {
            repositoryId: row.original.repositoryId,
          },
        }),
      });

      const { mutateAsync: deleteRun } = useMutation({
        ...deleteRepositoryRunMutation(),
        onSuccess() {
          toast.info('Delete Run', {
            description: `Run "${row.original.index}" deleted successfully.`,
          });
          queryClient.invalidateQueries({
            queryKey: getRepositoryRunsQueryKey({
              path: {
                repositoryId: row.original.repositoryId,
              },
            }),
          });
        },
        onError(error: ApiError) {
          toast.error(error.message, {
            description: <ToastError error={error} />,
            duration: Infinity,
            cancel: {
              label: 'Dismiss',
              onClick: () => {},
            },
          });
        },
      });

      async function handleDelete() {
        await deleteRun({
          path: {
            ortRunIndex: row.original.index,
            repositoryId: row.original.repositoryId,
          },
        });
      }

      return (
        <div className='flex flex-col items-center gap-2'>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button variant='outline' asChild size='sm' className='w-10'>
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
              <Button variant='outline' asChild size='sm' className='w-10'>
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
            <TooltipContent>Create a new run based on this run</TooltipContent>
          </Tooltip>
          <DeleteDialog
            thingName={
              <>
                run with index{' '}
                <span className='font-bold'>{row.original.index}</span>
                from repository{' '}
                <span className='font-bold'>{repository.data.url}</span>
              </>
            }
            uiComponent={<DeleteIconButton />}
            onDelete={handleDelete}
          />
        </div>
      );
    },
    enableColumnFilter: false,
  }),
];

export const RepositoryRunsTable = ({
  repoId,
  pageIndex,
  pageSize,
  search,
}: RepositoryTableProps) => {
  const setRunPageSize = useTablePrefsStore((state) => state.setRunPageSize);

  const {
    data: runs,
    error: runsError,
    isPending: runsIsPending,
    isError: runsIsError,
  } = useQuery({
    ...getRepositoryRunsOptions({
      path: {
        repositoryId: Number.parseInt(repoId),
      },
      query: { limit: pageSize, offset: pageIndex * pageSize, sort: '-index' },
    }),
    refetchInterval: pollInterval,
  });

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
        setRunPageSize(size);
        return {
          search: { ...search, page: 1, pageSize: size },
        };
      }}
    />
  );
};
