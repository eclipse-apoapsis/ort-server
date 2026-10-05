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
import { ChevronDown, ChevronsUpDown, ChevronUp } from 'lucide-react';

import {
  getRepositoryRunOptions,
  getRunDependencyGraphOptions,
} from '@/api/@tanstack/react-query.gen';
import { LoadingIndicator } from '@/components/loading-indicator';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Toggle } from '@/components/ui/toggle';
import { convertToBackendSorting } from '@/helpers/handle-multisort';
import { cn } from '@/lib/utils';
import {
  dependencyGraphSortSearchParameterSchema,
  type DependencyGraphSortField,
} from '@/schemas';
import { ManagerDependenciesTab } from './-components/manager-dependencies-tab';
import { PackageCountBadge } from './-components/package-count-badge';

type SortDirection = 'asc' | 'desc';

const SortChip = ({
  label,
  direction,
  priority,
  onToggle,
}: {
  label: string;
  direction: SortDirection | null;
  priority?: number;
  onToggle: () => void;
}) => {
  const Icon =
    direction === 'asc'
      ? ChevronUp
      : direction === 'desc'
        ? ChevronDown
        : ChevronsUpDown;

  return (
    <Toggle
      size='sm'
      pressed={direction !== null}
      onPressedChange={onToggle}
      aria-label={`Sort by ${label}`}
      className='gap-1.5'
    >
      <Icon className={cn('size-3.5', direction !== null && 'text-blue-500')} />
      {label}
      {priority !== undefined && (
        <span className='text-muted-foreground text-xs'>{priority}</span>
      )}
    </Toggle>
  );
};

const SortControls = ({ className }: { className?: string }) => {
  const search = Route.useSearch();
  const navigate = Route.useNavigate();

  const sortFields: { id: DependencyGraphSortField; label: string }[] = [
    { id: 'name', label: 'Name' },
    { id: 'packageCount', label: 'Packages' },
  ];

  const getSortDirection = (
    id: DependencyGraphSortField
  ): SortDirection | null => {
    const entry = search.sortBy?.find((s) => s.id === id);
    if (!entry) return null;
    return entry.desc ? 'desc' : 'asc';
  };

  const getSortPriority = (
    id: DependencyGraphSortField
  ): number | undefined => {
    if (!search.sortBy || search.sortBy.length <= 1) return undefined;
    const index = search.sortBy.findIndex((s) => s.id === id);
    return index === -1 ? undefined : index + 1;
  };

  const toggleSort = (id: DependencyGraphSortField) => {
    const current = getSortDirection(id);
    let newSortBy = search.sortBy ?? [];

    if (current === null) {
      newSortBy = [...newSortBy, { id, desc: false }];
    } else if (current === 'asc') {
      newSortBy = newSortBy.map((s) =>
        s.id === id ? { ...s, desc: true } : s
      );
    } else {
      newSortBy = newSortBy.filter((s) => s.id !== id);
    }

    navigate({
      search: {
        ...search,
        sortBy: newSortBy.length === 0 ? undefined : newSortBy,
      },
    });
  };

  return (
    <div className={cn('flex items-center gap-1.5', className)}>
      <span className='text-muted-foreground text-sm'>Sort:</span>
      {sortFields.map(({ id, label }) => (
        <SortChip
          key={id}
          label={label}
          direction={getSortDirection(id)}
          priority={getSortPriority(id)}
          onToggle={() => toggleSort(id)}
        />
      ))}
    </div>
  );
};

const DependenciesComponent = () => {
  const params = Route.useParams();
  const search = Route.useSearch();

  const { data: ortRun } = useSuspenseQuery({
    ...getRepositoryRunOptions({
      path: {
        repositoryId: Number.parseInt(params.repoId),
        ortRunIndex: Number.parseInt(params.runIndex),
      },
    }),
  });

  const { data: dependencyGraphs } = useSuspenseQuery({
    ...getRunDependencyGraphOptions({
      path: {
        runId: ortRun.id,
      },
      query: {
        sort: convertToBackendSorting(search.sortBy),
      },
    }),
  });

  const managerEntries = Object.entries(dependencyGraphs.graphs).sort(
    ([a], [b]) => a.localeCompare(b)
  );
  const defaultManager = managerEntries[0]?.[0];

  return (
    <Card className='h-fit'>
      <CardHeader>
        <CardTitle>Dependency Graphs</CardTitle>
        <CardDescription>
          The dependency graphs per ecosystem as discovered during the run. The
          number of unique packages per ecosystem is shown in each tab.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {managerEntries.length === 0 ? (
          <div className='text-muted-foreground text-sm'>
            No dependency graphs are available for this run.
          </div>
        ) : (
          <Tabs defaultValue={defaultManager} className='gap-4'>
            <div className='flex flex-wrap items-center gap-4'>
              <TabsList className='h-auto justify-start gap-1 overflow-x-auto'>
                {managerEntries.map(([managerName, graph]) => (
                  <TabsTrigger
                    key={managerName}
                    value={managerName}
                    className='flex-none gap-2'
                  >
                    {managerName}
                    <PackageCountBadge count={graph.packageCount} />
                  </TabsTrigger>
                ))}
              </TabsList>
              <SortControls className='ml-auto' />
            </div>

            {managerEntries.map(([managerName, graph]) => (
              <ManagerDependenciesTab
                key={managerName}
                graph={graph}
                managerName={managerName}
              />
            ))}
          </Tabs>
        )}
      </CardContent>
    </Card>
  );
};

export const Route = createFileRoute(
  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/dependencies/'
)({
  validateSearch: dependencyGraphSortSearchParameterSchema,
  loader: async ({ context: { queryClient }, params }) => {
    const ortRun = await queryClient.fetchQuery({
      ...getRepositoryRunOptions({
        path: {
          repositoryId: Number.parseInt(params.repoId),
          ortRunIndex: Number.parseInt(params.runIndex),
        },
      }),
    });

    await queryClient.prefetchQuery({
      ...getRunDependencyGraphOptions({
        path: {
          runId: ortRun.id,
        },
      }),
    });
  },
  component: DependenciesComponent,
  pendingComponent: LoadingIndicator,
});
