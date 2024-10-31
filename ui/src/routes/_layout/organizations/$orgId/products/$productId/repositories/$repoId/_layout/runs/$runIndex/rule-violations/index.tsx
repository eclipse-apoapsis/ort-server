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
  ColumnDef,
  getCoreRowModel,
  getExpandedRowModel,
  getGroupedRowModel,
  getPaginationRowModel,
  useReactTable,
} from '@tanstack/react-table';
import { ChevronRight } from 'lucide-react';
import { useMemo } from 'react';

import { prefetchUseRepositoriesServiceGetOrtRunByIndex } from '@/api/queries/prefetch';
import {
  useRepositoriesServiceGetOrtRunByIndexSuspense,
  useRuleViolationsServiceGetRuleViolationsByRunIdSuspense,
} from '@/api/queries/suspense';
import { RuleViolation } from '@/api/requests';
import { DataTable } from '@/components/data-table/data-table';
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
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { getRuleViolationSeverityBackgroundColor } from '@/helpers/get-status-class';
import { paginationSchema, tableGroupingSchema } from '@/schemas';

const defaultPageSize = 10;

const columns: ColumnDef<RuleViolation>[] = [
  {
    accessorFn: (ruleViolation) => ruleViolation.severity,
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
  },
  {
    accessorFn: (ruleViolation) => {
      return `${ruleViolation.packageId?.type ? ruleViolation.packageId?.type.concat(':') : ''}
        ${ruleViolation.packageId?.namespace ? ruleViolation.packageId?.namespace.concat('/') : ''}
        ${ruleViolation.packageId?.name ? ruleViolation.packageId?.name : ''}
        ${ruleViolation.packageId?.version ? '@'.concat(ruleViolation.packageId?.version) : ''}`;
    },
    header: 'Package',
    cell: ({ getValue }) => {
      // TypeScript gets confused, but type casting to string is safe here,
      // because the accessor function returns a string.
      return <div className='font-semibold'>{getValue() as string}</div>;
    },
  },
  {
    accessorFn: (ruleViolation) => ruleViolation.rule,
    header: 'Rule',
    cell: ({ row }) => (
      <Badge className='whitespace-nowrap bg-blue-300'>
        {row.original.rule}
      </Badge>
    ),
  },
  {
    id: 'moreInfo',
    header: () => null,
    enableGrouping: false,
    size: 50,
    cell: ({ row }) => (
      <Tooltip>
        <TooltipTrigger>
          <Dialog>
            <DialogTrigger asChild>
              <Button variant='outline' size='sm'>
                <ChevronRight className='h-4 w-4' />
              </Button>
            </DialogTrigger>
            <RuleViolationDetailsComponent details={row.original} />
          </Dialog>
        </TooltipTrigger>
        <TooltipContent>Show the details of the rule violation</TooltipContent>
      </Tooltip>
    ),
  },
];

// TODO: This is a temporary solution, which will be replaced with
// unique subpages to show the rule violation details.
type Props = {
  details: RuleViolation;
};
const RuleViolationDetailsComponent = ({ details }: Props) => {
  return (
    <DialogContent className={'max-h-96 overflow-y-scroll lg:max-w-screen-lg'}>
      <DialogHeader>
        <DialogTitle>{details.rule}</DialogTitle>
        <DialogDescription>{details?.message}</DialogDescription>
      </DialogHeader>
      <div className='grid grid-cols-8 gap-2 text-sm'>
        <div className='col-span-2 font-semibold'>License:</div>
        <div className='col-span-6'>{details.license}</div>
        <div className='col-span-2 font-semibold'>License source:</div>
        <div className='col-span-6'>{details.licenseSource}</div>
        <div className='col-span-2 font-semibold'>How to fix:</div>
      </div>
      <MarkdownRenderer markdown={details.howToFix} />
      <DialogFooter>
        <DialogClose asChild>
          <Button type='button' variant='secondary'>
            Close
          </Button>
        </DialogClose>
      </DialogFooter>
    </DialogContent>
  );
};

const RuleViolationsComponent = () => {
  const params = Route.useParams();
  const search = Route.useSearch();

  // Memoize the search parameters to prevent unnecessary re-rendering

  const pageIndex = useMemo(
    () => (search.page ? search.page - 1 : 0),
    [search.page]
  );

  const pageSize = useMemo(
    () => (search.pageSize ? search.pageSize : defaultPageSize),
    [search.pageSize]
  );

  const groups = useMemo(
    () => (search.groups ? search.groups : ['Severity']),
    [search.groups]
  );

  const { data: ortRun } = useRepositoriesServiceGetOrtRunByIndexSuspense({
    repositoryId: Number.parseInt(params.repoId),
    ortRunIndex: Number.parseInt(params.runIndex),
  });

  const { data: ruleViolations } =
    useRuleViolationsServiceGetRuleViolationsByRunIdSuspense({
      runId: ortRun.id,
      // Fetch all data at once, as we need to do both grouping and
      // pagination in front-end for consistency in data handling.
      limit: 100000,
      sort: 'rule',
    });

  const table = useReactTable({
    data: ruleViolations?.data || [],
    columns,
    state: {
      pagination: {
        pageIndex,
        pageSize,
      },
      grouping: groups,
    },
    getCoreRowModel: getCoreRowModel(),
    getExpandedRowModel: getExpandedRowModel(),
    getGroupedRowModel: getGroupedRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
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
        <CardContent>
          <DataTable
            table={table}
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
            setGroupingOptions={(groups) => {
              return {
                to: Route.to,
                search: { ...search, groups: groups },
              };
            }}
            enableGrouping={true}
          />
        </CardContent>
      </CardContent>
    </Card>
  );
};

export const Route = createFileRoute(
  '/_layout/organizations/$orgId/products/$productId/repositories/$repoId/_layout/runs/$runIndex/rule-violations/'
)({
  validateSearch: paginationSchema.merge(tableGroupingSchema),
  loader: async ({ context, params }) => {
    await prefetchUseRepositoriesServiceGetOrtRunByIndex(context.queryClient, {
      repositoryId: Number.parseInt(params.repoId),
      ortRunIndex: Number.parseInt(params.runIndex),
    });
  },
  component: RuleViolationsComponent,
  pendingComponent: LoadingIndicator,
});
