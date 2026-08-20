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
import { ExpandedState } from '@tanstack/react-table';
import {
  getCoreRowModel,
  getExpandedRowModel,
  legacyCreateColumnHelper,
  LegacyRow,
  useLegacyTable,
} from '@tanstack/react-table/legacy';
import { ChevronDown, ChevronUp } from 'lucide-react';
import { useCallback, useState } from 'react';
import z from 'zod';

import { RuleViolation, Severity } from '@/api';
import {
  getRepositoryRunOptions,
  getRunRuleViolationRulesOptions,
  getRunRuleViolationsOptions,
} from '@/api/@tanstack/react-query.gen';
import { zSeverity } from '@/api/zod.gen';
import { BreakableString } from '@/components/breakable-string';
import { CopyToClipboard } from '@/components/copy-to-clipboard';
import { DataTableCards } from '@/components/data-table-cards/data-table-cards';
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
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import {
  getResolvedBackgroundColor,
  getRuleViolationSeverityBackgroundColor,
} from '@/helpers/get-status-class';
import {
  convertToBackendSorting,
  updateColumnSorting,
} from '@/helpers/handle-multisort';
import { identifierToString } from '@/helpers/identifier-conversion';
import {
  getResolutionAccordionDefaultValue,
  getResolutionAccordionLabel,
  getResolvedStatus,
} from '@/helpers/resolutions';
import { ACTION_COLUMN_SIZE } from '@/lib/constants';
import { toastError } from '@/lib/toast';
import {
  ItemResolved,
  itemResolvedSchema,
  itemStatusSearchParameterSchema,
  markedSearchParameterSchema,
  packageIdentifierSearchParameterSchema,
  paginationSearchParameterSchema,
  ruleSearchParameterSchema,
  severitySearchParameterSchema,
  sortingSearchParameterSchema,
} from '@/schemas';
import { useUserSettingsStore } from '@/store/user-settings.store';

const defaultPageSize = 10;
const supportedSortColumns = new Set([
  'identifier',
  'purl',
  'status',
  'severity',
  'rule',
]);

const columnHelper = legacyCreateColumnHelper<RuleViolation>();

// Component to render a single rule violation card in the list.
const RuleViolationCard = ({
  ruleViolation,
}: {
  ruleViolation: RuleViolation;
}) => {
  const params = Route.useParams();
  const packageIdType = useUserSettingsStore((state) => state.packageIdType);
  const id =
    packageIdType === 'PURL' && ruleViolation.purl
      ? ruleViolation.purl
      : identifierToString(ruleViolation.id);

  return (
    <div className='flex flex-col gap-1'>
      <div className='flex items-center justify-between'>
        <div className='min-w-0 flex-1'>
          <Tooltip>
            <TooltipTrigger asChild>
              {ruleViolation.purl ? (
                <Link
                  className='text-left font-semibold text-blue-400 hover:underline'
                  to='/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/packages'
                  params={{
                    orgId: params.orgId,
                    productId: params.productId,
                    repoId: params.repoId,
                    runIndex: params.runIndex,
                  }}
                  search={{ pkgId: id, marked: '0' }}
                >
                  <BreakableString text={id} />
                </Link>
              ) : (
                <Link
                  className='text-left font-semibold text-blue-400 hover:underline'
                  to='/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/projects'
                  params={{
                    orgId: params.orgId,
                    productId: params.productId,
                    repoId: params.repoId,
                    runIndex: params.runIndex,
                  }}
                  search={{ projectId: id, marked: id }}
                >
                  <BreakableString text={id} />
                </Link>
              )}
            </TooltipTrigger>
            <TooltipContent>
              {ruleViolation.purl
                ? 'Inspect the package details in packages table'
                : 'Inspect the project details in projects table'}
            </TooltipContent>
          </Tooltip>
          <CopyToClipboard copyText={id} className='h-5 px-2 align-middle' />
        </div>
        <Badge
          className='bg-blue-300 whitespace-nowrap text-black'
          variant='small'
        >
          {ruleViolation.rule}
        </Badge>
      </div>
      <div className='flex items-center justify-between'>
        <div className='flex gap-2'>
          <Badge
            className={`${getResolvedBackgroundColor(
              getResolvedStatus(ruleViolation)
            )}`}
            variant='small'
          >
            {getResolvedStatus(ruleViolation)}
          </Badge>
          <Badge
            className={`${getRuleViolationSeverityBackgroundColor(
              ruleViolation.severity
            )}`}
            variant='small'
          >
            {ruleViolation.severity}
          </Badge>
        </div>
        <div className='text-muted-foreground flex gap-1 text-sm'>
          {ruleViolation.license && <div>{ruleViolation.license}</div>}
          {ruleViolation.licenseSources &&
            ruleViolation.licenseSources.length > 0 && (
              <div>({ruleViolation.licenseSources.join(', ')})</div>
            )}
        </div>
      </div>
    </div>
  );
};

const RuleViolationsComponent = () => {
  const params = Route.useParams();
  const search = Route.useSearch();
  const navigate = Route.useNavigate();
  const packageIdType = useUserSettingsStore((state) => state.packageIdType);

  const { data: ortRun } = useSuspenseQuery({
    ...getRepositoryRunOptions({
      path: {
        repositoryId: Number.parseInt(params.repoId),
        ortRunIndex: Number.parseInt(params.runIndex),
      },
    }),
  });

  const pageIndex = search.page ? search.page - 1 : 0;
  const pageSize = search.pageSize ? search.pageSize : defaultPageSize;
  const severity = search.severity;
  const itemStatus = search.itemResolved;
  const packageIdentifier = search.pkgId;
  const selectedRules = search.rule;
  const columnId = packageIdType === 'ORT_ID' ? 'identifier' : 'purl';
  const resolved =
    itemStatus?.length === 1 ? itemStatus[0] === 'Resolved' : undefined;
  const sortBy =
    search.sortBy?.filter((sort) => supportedSortColumns.has(sort.id)) ?? [];

  const columnFilters = [
    { id: 'severity', value: severity },
    { id: 'status', value: itemStatus },
    { id: columnId, value: packageIdentifier },
    { id: 'rule', value: selectedRules },
  ].filter(({ value }) => value !== undefined);

  const {
    data: totalRuleViolations,
    isPending: totalIsPending,
    isError: totalIsError,
    error: totalError,
  } = useQuery({
    ...getRunRuleViolationsOptions({
      path: { runId: ortRun.id },
      query: { limit: 1 },
    }),
  });

  const {
    data: availableRules,
    isPending: rulesIsPending,
    isError: rulesIsError,
    error: rulesError,
  } = useQuery({
    ...getRunRuleViolationRulesOptions({
      path: { runId: ortRun.id },
    }),
  });

  const {
    data: ruleViolations,
    isPending,
    isError,
    error,
  } = useQuery({
    ...getRunRuleViolationsOptions({
      path: { runId: ortRun.id },
      query: {
        limit: pageSize,
        offset: pageIndex * pageSize,
        sort: convertToBackendSorting(sortBy),
        resolved,
        severity: severity?.join(','),
        rule: selectedRules?.join(','),
        ...(packageIdType === 'ORT_ID'
          ? { identifier: packageIdentifier }
          : { purl: packageIdentifier }),
      },
    }),
  });

  const ruleOptions =
    availableRules?.map((rule) => ({ label: rule, value: rule })) ?? [];

  const columns = columnHelper.columns([
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
      cell: ({ row }) => <RuleViolationCard ruleViolation={row.original} />,
    }),
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
      (ruleViolation) => {
        return getResolvedStatus(ruleViolation);
      },
      {
        id: 'status',
        header: 'Status',
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
    columnHelper.accessor('severity', {
      header: 'Severity',
      meta: {
        filter: {
          filterVariant: 'select',
          selectOptions: zSeverity.options.map((severity) => ({
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

    columnHelper.accessor('rule', {
      header: 'Rule',
      meta: {
        filter: {
          filterVariant: 'select',
          selectOptions: ruleOptions,
          setSelected: (rules: string[]) => {
            navigate({
              search: {
                ...search,
                page: 1,
                rule: rules.length === 0 ? undefined : rules,
              },
            });
          },
          align: 'end',
        },
      },
    }),
  ]);

  const renderSubComponent = useCallback(
    ({ row }: { row: LegacyRow<RuleViolation> }) => {
      const ruleViolation = row.original;

      return (
        <Accordion
          type='multiple'
          className='w-full'
          defaultValue={getResolutionAccordionDefaultValue(ruleViolation)}
        >
          <AccordionItem value='resolutions'>
            <AccordionTrigger className='font-semibold'>
              {getResolutionAccordionLabel(ruleViolation)}
            </AccordionTrigger>
            <AccordionContent>
              <Resolutions
                item={ruleViolation}
                repositoryId={params.repoId}
                runId={ortRun.id}
              />
            </AccordionContent>
          </AccordionItem>
          <AccordionItem value='details'>
            <AccordionTrigger className='font-semibold'>
              Details
            </AccordionTrigger>
            <AccordionContent>
              <div className='flex flex-col gap-4'>
                <div>{ruleViolation.message}</div>
                <div className='grid grid-cols-8 gap-2'>
                  <div className='col-span-2 font-semibold'>License:</div>
                  <div className='col-span-6'>
                    <FormattedValue value={ruleViolation.license} />
                  </div>
                  <div className='col-span-2 font-semibold'>
                    License sources:
                  </div>
                  <div className='col-span-6'>
                    <FormattedValue
                      value={
                        ruleViolation.licenseSources &&
                        ruleViolation.licenseSources.length > 0
                          ? ruleViolation.licenseSources.join(', ')
                          : null
                      }
                    />
                  </div>
                  <div className='col-span-2 font-semibold'>How to fix:</div>
                </div>
                <MarkdownRenderer markdown={ruleViolation.howToFix} />
              </div>
            </AccordionContent>
          </AccordionItem>
        </Accordion>
      );
    },
    [params.repoId, ortRun.id]
  );

  const [expanded, setExpanded] = useState<ExpandedState>(
    search.marked ? { [search.marked]: true } : {}
  );

  const table = useLegacyTable({
    data: ruleViolations?.data || [],
    columns,
    pageCount: Math.ceil(
      (ruleViolations?.pagination.totalCount ?? 0) / pageSize
    ),
    state: {
      pagination: {
        pageIndex,
        pageSize,
      },
      columnFilters,
      columnVisibility: {
        [columnId]: false,
        severity: false,
        status: false,
        rule: false,
      },
      sorting: sortBy,
      expanded: expanded,
    },
    onExpandedChange: setExpanded,
    getCoreRowModel: getCoreRowModel(),
    getExpandedRowModel: getExpandedRowModel(),
    getRowCanExpand: () => true,
    manualFiltering: true,
    manualPagination: true,
    manualSorting: true,
  });

  if (isPending || totalIsPending || rulesIsPending) {
    return <LoadingIndicator />;
  }

  if (isError || totalIsError || rulesIsError) {
    toastError('Unable to load data', error || totalError || rulesError);
    return;
  }

  const filtersInUse = table.getState().columnFilters.length > 0;
  const matching = `, ${ruleViolations.pagination.totalCount} matching filters`;
  const evaluatorWasIncludedInRun = ortRun.jobs.evaluator != null;
  const noResultsContent = !evaluatorWasIncludedInRun ? (
    <div className='text-muted-foreground text-sm'>
      No rule violations are available because the evaluator job was not enabled
      for this run.
    </div>
  ) : undefined;

  return (
    <Card className='h-fit'>
      <CardHeader>
        <CardTitle>
          Rule Violations ({totalRuleViolations.pagination.totalCount} in total
          {filtersInUse && matching})
        </CardTitle>
        <CardDescription>
          This view shows all violations that go against the rules defined in
          the configured policy.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <DataTableCards
          table={table}
          noResultsContent={noResultsContent}
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
                sortBy: updateColumnSorting(
                  search.sortBy?.filter((sort) =>
                    supportedSortColumns.has(sort.id)
                  ),
                  sortBy
                ),
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
    ...ruleSearchParameterSchema.shape,
    ...sortingSearchParameterSchema.shape,
    ...markedSearchParameterSchema.shape,
  }),
  component: RuleViolationsComponent,
  pendingComponent: LoadingIndicator,
});
