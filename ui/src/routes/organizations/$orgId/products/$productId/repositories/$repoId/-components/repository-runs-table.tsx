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
import { GitCompare, Repeat, View } from 'lucide-react';
import { useCallback, useMemo, useState } from 'react';

import {
  JobSummary,
  Organization,
  OrtRunSummary,
  Product,
  Repository,
} from '@/api';
import {
  deleteRepositoryRunMutation,
  getOrganizationOptions,
  getProductOptions,
  getRepositoryOptions,
  getRepositoryRunOptions,
  getRepositoryRunsOptions,
  getRepositoryRunsQueryKey,
  getRunStatisticsOptions,
} from '@/api/@tanstack/react-query.gen';
import { DataTable } from '@/components/data-table/data-table';
import { DeleteDialog } from '@/components/delete-dialog';
import { DeleteIconButton } from '@/components/delete-icon-button';
import { FavoriteButton } from '@/components/favorite-button';
import { ItemCounts } from '@/components/item-counts';
import { LoadingIndicator } from '@/components/loading-indicator';
import { OrtRunJobStatus } from '@/components/ort-run-job-status';
import { RunDuration } from '@/components/run-duration';
import { Sha1Component } from '@/components/sha1-component';
import { TimestampWithUTC } from '@/components/timestamp-with-utc';
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Toggle } from '@/components/ui/toggle';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { config } from '@/config';
import { diffResolvedJobConfigs } from '@/helpers/config-diff';
import { getStatusBackgroundColor } from '@/helpers/get-status-class';
import { isJobFinished } from '@/helpers/job-helpers';
import { useRepositoryPermission } from '@/hooks/use-authorization';
import { ApiError } from '@/lib/api-error';
import { toast, toastError } from '@/lib/toast';
import { buildRunFavorite } from '@/providers/home-data';
import { useTablePrefsStore } from '@/store/table-prefs.store';
import { RunConfigurationDiffDialog } from './run-configuration-diff-dialog';

type RepositoryTableProps = {
  orgId: string;
  productId: string;
  repoId: string;
  pageIndex: number;
  pageSize: number;
  search: {
    page?: number | undefined;
    pageSize?: number | undefined;
  };
};

type RunComparisonSelection = {
  baseRun?: OrtRunSummary;
  comparedRun?: OrtRunSummary;
  isDialogOpen: boolean;
};

type RunComparisonContext = {
  selection: RunComparisonSelection;
  onSelectRun: (summary: OrtRunSummary) => void;
};

const emptyRunComparisonSelection: RunComparisonSelection = {
  isDialogOpen: false,
};

const selectRunForComparison = (
  selection: RunComparisonSelection,
  summary: OrtRunSummary
): RunComparisonSelection => {
  if (!selection.baseRun) {
    return { baseRun: summary, isDialogOpen: false };
  }

  if (selection.baseRun.index === summary.index) {
    return resetRunComparisonSelection();
  }

  if (!selection.comparedRun) {
    return {
      baseRun: selection.baseRun,
      comparedRun: summary,
      isDialogOpen: true,
    };
  }

  return selection;
};

const resetRunComparisonSelection = (): RunComparisonSelection => ({
  isDialogOpen: false,
});

const pollInterval = config.pollInterval;

const columnHelper = createColumnHelper<OrtRunSummary>();

const showBadge = (jobSummary: JobSummary | null | undefined) => {
  return (
    jobSummary !== undefined &&
    jobSummary !== null &&
    isJobFinished(jobSummary.status)
  );
};

const SummaryCard = ({
  summary,
  comparison,
}: {
  summary: OrtRunSummary;
  comparison: RunComparisonContext;
}) => {
  const isSelectedForComparison =
    comparison.selection.baseRun?.index === summary.index ||
    comparison.selection.comparedRun?.index === summary.index;

  const comparisonTooltip = isSelectedForComparison
    ? 'Selected for comparison'
    : comparison.selection.baseRun
      ? 'Select the run to compare against the base run'
      : 'Select the base run for comparison';

  const handleComparisonPressedChange = () => {
    comparison.onSelectRun(summary);
  };

  const hasLabels = summary.labels && Object.keys(summary.labels).length > 0;

  const statistics = useQuery({
    ...getRunStatisticsOptions({
      path: { runId: summary.id },
    }),
    refetchInterval: pollInterval,
  });

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
          <ItemCounts
            statistics={statistics.data}
            wide
            showIssues={showBadge(summary.jobs.analyzer)}
            showVulnerabilities={showBadge(summary.jobs.advisor)}
            showRuleViolations={showBadge(summary.jobs.evaluator)}
            link={{
              params: {
                orgId: summary.organizationId.toString(),
                productId: summary.productId.toString(),
                repoId: summary.repositoryId.toString(),
                runIndex: summary.index.toString(),
              },
              issuesSearch: {
                sortBy: [{ id: 'severity', desc: true }],
                itemResolved: ['Unresolved'],
              },
              vulnerabilitiesSearch: {
                sortBy: [{ id: 'rating', desc: true }],
                itemResolved: ['Unresolved'],
              },
              ruleViolationsSearch: {
                sortBy: [{ id: 'severity', desc: true }],
                itemResolved: ['Unresolved'],
              },
            }}
          />
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
            <AccordionTrigger
              className='w-auto flex-none justify-end gap-2'
              headerClassName='items-center justify-end gap-2'
              beforeTrigger={
                <Tooltip>
                  <TooltipTrigger asChild>
                    <Toggle
                      aria-label={comparisonTooltip}
                      className={
                        isSelectedForComparison
                          ? 'border-yellow-500 dark:border-yellow-400'
                          : undefined
                      }
                      pressed={isSelectedForComparison}
                      variant='outline'
                      size='xs'
                      onPressedChange={handleComparisonPressedChange}
                    >
                      <GitCompare className='h-4 w-4' />
                    </Toggle>
                  </TooltipTrigger>
                  <TooltipContent>{comparisonTooltip}</TooltipContent>
                </Tooltip>
              }
            >
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
                  <div className='flex w-full flex-wrap justify-end gap-2'>
                    {Object.entries(summary.labels!).map(([key, value]) => (
                      <Badge
                        key={key}
                        className='max-w-full min-w-0 shrink bg-gray-200 break-words whitespace-normal text-gray-800 dark:bg-gray-700 dark:text-gray-200'
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

type FavoriteContext = {
  organization?: Organization;
  product?: Product;
  repository?: Repository;
};

const RunIndexCell = ({
  favoriteContext,
  summary,
}: {
  favoriteContext: FavoriteContext;
  summary: OrtRunSummary;
}) => {
  const { organization, product, repository } = favoriteContext;

  return (
    <div className='flex items-center gap-1.5'>
      <Link
        className='font-semibold text-blue-400 hover:underline'
        to={
          '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex'
        }
        params={{
          orgId: summary.organizationId.toString(),
          productId: summary.productId.toString(),
          repoId: summary.repositoryId.toString(),
          runIndex: summary.index.toString(),
        }}
      >
        {summary.index}
      </Link>
      {organization && product && repository && (
        <FavoriteButton
          favorite={buildRunFavorite(
            organization,
            product,
            repository,
            summary
          )}
          size='xs'
          variant='ghost'
          className='size-6 p-0'
        />
      )}
    </div>
  );
};

const createColumns = (
  favoriteContext: FavoriteContext,
  comparison: RunComparisonContext
) => [
  columnHelper.accessor('index', {
    header: 'Index',
    size: 50,
    cell: ({ row }) => (
      <RunIndexCell favoriteContext={favoriteContext} summary={row.original} />
    ),
    enableColumnFilter: false,
  }),
  columnHelper.display({
    id: 'card',
    header: 'Run Details',
    cell: ({ row }) => (
      <SummaryCard summary={row.original} comparison={comparison} />
    ),
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

      const { isAllowed: canTriggerRun } = useRepositoryPermission(
        row.original.repositoryId,
        'TRIGGER_ORT_RUN'
      );

      const { isAllowed: canDeleteRun } = useRepositoryPermission(
        row.original.repositoryId,
        'DELETE'
      );

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
          toastError(error.message, error);
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
              <Button
                variant='outline'
                asChild
                size='sm'
                className='w-10'
                disabled={canTriggerRun === false}
              >
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
              {canTriggerRun !== false
                ? 'Create a new run based on this run'
                : 'Insufficient permissions.'}
            </TooltipContent>
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
            disabled={canDeleteRun === false}
          />
        </div>
      );
    },
    enableColumnFilter: false,
  }),
];

export const RepositoryRunsTable = ({
  orgId,
  productId,
  repoId,
  pageIndex,
  pageSize,
  search,
}: RepositoryTableProps) => {
  const setRunPageSize = useTablePrefsStore((state) => state.setRunPageSize);
  const repositoryId = Number.parseInt(repoId);
  const [comparisonSelection, setComparisonSelection] = useState(
    emptyRunComparisonSelection
  );

  const handleSelectRunForComparison = useCallback((summary: OrtRunSummary) => {
    setComparisonSelection((selection) =>
      selectRunForComparison(selection, summary)
    );
  }, []);

  const handleComparisonDialogOpenChange = (open: boolean) => {
    if (!open) {
      setComparisonSelection(resetRunComparisonSelection());
    }
  };

  const { data: organization } = useQuery({
    ...getOrganizationOptions({
      path: { organizationId: Number.parseInt(orgId) },
    }),
  });

  const { data: product } = useQuery({
    ...getProductOptions({
      path: { productId: Number.parseInt(productId) },
    }),
  });

  const { data: repository } = useQuery({
    ...getRepositoryOptions({
      path: { repositoryId },
    }),
  });

  const selectedBaseRunIndex = comparisonSelection.baseRun?.index;
  const selectedComparedRunIndex = comparisonSelection.comparedRun?.index;
  const hasSelectedRunPair =
    selectedBaseRunIndex !== undefined &&
    selectedComparedRunIndex !== undefined;

  const baseRunQuery = useQuery({
    ...getRepositoryRunOptions({
      path: {
        repositoryId,
        ortRunIndex: selectedBaseRunIndex ?? 0,
      },
    }),
    enabled: hasSelectedRunPair,
  });

  const comparedRunQuery = useQuery({
    ...getRepositoryRunOptions({
      path: {
        repositoryId,
        ortRunIndex: selectedComparedRunIndex ?? 0,
      },
    }),
    enabled: hasSelectedRunPair,
  });

  const columns = useMemo(
    () =>
      createColumns(
        { organization, product, repository },
        {
          selection: comparisonSelection,
          onSelectRun: handleSelectRunForComparison,
        }
      ),
    [
      organization,
      product,
      repository,
      comparisonSelection,
      handleSelectRunForComparison,
    ]
  );

  const {
    data: runs,
    error: runsError,
    isPending: runsIsPending,
    isError: runsIsError,
  } = useQuery({
    ...getRepositoryRunsOptions({
      path: {
        repositoryId,
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

  const comparisonDiff = useMemo(() => {
    const baseConfig = baseRunQuery.data?.resolvedJobConfigs;
    const comparedConfig = comparedRunQuery.data?.resolvedJobConfigs;

    if (!baseConfig || !comparedConfig) {
      return undefined;
    }

    return diffResolvedJobConfigs(baseConfig, comparedConfig);
  }, [
    baseRunQuery.data?.resolvedJobConfigs,
    comparedRunQuery.data?.resolvedJobConfigs,
  ]);

  if (runsIsPending) {
    return <LoadingIndicator />;
  }

  if (runsIsError) {
    toastError('Unable to load data', runsError);
    return;
  }

  const selectedBaseRun = comparisonSelection.baseRun;
  const selectedComparedRun = comparisonSelection.comparedRun;
  const canRenderComparisonDialog =
    selectedBaseRun !== undefined && selectedComparedRun !== undefined;

  const isComparisonConfigurationMissing = Boolean(
    baseRunQuery.data &&
    comparedRunQuery.data &&
    (!baseRunQuery.data.resolvedJobConfigs ||
      !comparedRunQuery.data.resolvedJobConfigs)
  );

  return (
    <>
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
      {canRenderComparisonDialog && (
        <RunConfigurationDiffDialog
          open={comparisonSelection.isDialogOpen}
          onOpenChange={handleComparisonDialogOpenChange}
          baseRunIndex={selectedBaseRun.index}
          comparedRunIndex={selectedComparedRun.index}
          diff={comparisonDiff}
          isLoading={baseRunQuery.isPending || comparedRunQuery.isPending}
          isError={baseRunQuery.isError || comparedRunQuery.isError}
          isConfigurationMissing={isComparisonConfigurationMissing}
        />
      )}
    </>
  );
};

if (import.meta.vitest) {
  const { describe, expect, it } = import.meta.vitest;

  const runSummary = (index: number) => ({ index }) as OrtRunSummary;

  describe('selectRunForComparison', () => {
    it('selects the first run as the base run', () => {
      expect(
        selectRunForComparison(emptyRunComparisonSelection, runSummary(1))
      ).toEqual({
        baseRun: runSummary(1),
        isDialogOpen: false,
      });
    });

    it('selects the second run and opens the dialog', () => {
      expect(
        selectRunForComparison(
          { baseRun: runSummary(1), isDialogOpen: false },
          runSummary(2)
        )
      ).toEqual({
        baseRun: runSummary(1),
        comparedRun: runSummary(2),
        isDialogOpen: true,
      });
    });

    it('clears the selection when selecting the base run again', () => {
      expect(
        selectRunForComparison(
          { baseRun: runSummary(1), isDialogOpen: false },
          runSummary(1)
        )
      ).toEqual({
        isDialogOpen: false,
      });
    });
  });

  describe('resetRunComparisonSelection', () => {
    it('clears selected runs and closes the dialog', () => {
      expect(resetRunComparisonSelection()).toEqual({
        isDialogOpen: false,
      });
    });
  });
}
