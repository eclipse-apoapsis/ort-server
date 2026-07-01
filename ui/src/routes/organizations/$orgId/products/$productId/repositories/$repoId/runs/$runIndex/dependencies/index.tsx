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
import { ChevronDown, ChevronsUpDown, ChevronUp, X } from 'lucide-react';
import { useState } from 'react';

import type { DependencyGraph, DependencyGraphScope } from '@/api';
import {
  getRepositoryRunOptions,
  getRunDependencyGraphOptions,
} from '@/api/@tanstack/react-query.gen';
import { LoadingIndicator } from '@/components/loading-indicator';
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
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '@/components/ui/collapsible';
import { Input } from '@/components/ui/input';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Toggle } from '@/components/ui/toggle';
import { convertToBackendSorting } from '@/helpers/handle-multisort';
import { useDebounce } from '@/hooks/use-debounce';
import { cn } from '@/lib/utils';
import {
  dependencyGraphSortSearchParameterSchema,
  type DependencyGraphSortField,
} from '@/schemas';
import { useUserSettingsStore } from '@/store/user-settings.store';
import {
  buildAdjacencyMap,
  createNodeSubtreeMatcher,
  matchesSearch,
  normalizeSearchTerm,
} from './-components/dependency-graph-utils';
import { DependencyTreeNode } from './-components/dependency-tree-node';
import { HighlightedMatch } from './-components/highlighted-match';
import { TreeBranch } from './-components/tree-branch';
import { TreeToggleIcon } from './-components/tree-toggle-icon';

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

const PackageCountBadge = ({ count }: { count?: number | null }) => {
  if (count == null) return null;

  return (
    <Badge variant='secondary'>
      {count} package
      {count === 1 ? '' : 's'}
    </Badge>
  );
};

const TreeToggle = ({
  children,
  className,
}: {
  children: React.ReactNode;
  className?: string;
}) => (
  <CollapsibleTrigger asChild>
    <button
      type='button'
      className={cn(
        'group/toggle flex w-full items-start gap-2 rounded-sm text-left',
        className
      )}
    >
      <TreeToggleIcon />
      <div className='min-w-0 flex-1'>{children}</div>
    </button>
  </CollapsibleTrigger>
);

const ManagerDependenciesTab = ({
  graph,
  managerName,
}: {
  graph: DependencyGraph;
  managerName: string;
}) => {
  const [searchValue, setSearchValue] = useState('');
  const packageIdType = useUserSettingsStore((state) => state.packageIdType);
  const debouncedSearchValue = useDebounce(searchValue);
  const searchTerm = normalizeSearchTerm(debouncedSearchValue);
  const adjacency = buildAdjacencyMap(graph);
  const matchesNodeSubtree = createNodeSubtreeMatcher(
    graph,
    adjacency,
    searchTerm,
    packageIdType
  );

  // The search never hides parts of the graph; it only decides which branches
  // are auto-expanded to reveal the matches. A scope is considered to contain a
  // match when its own label matches or any of its node subtrees does.
  const scopeHasMatch = ({
    rootNodeIndexes,
    scopeLabel,
  }: DependencyGraphScope) =>
    matchesSearch(scopeLabel, searchTerm) ||
    rootNodeIndexes.some(matchesNodeSubtree);

  const hasMatches =
    !searchTerm ||
    graph.projectGroups.some(
      ({ projectLabel, scopes }) =>
        matchesSearch(projectLabel, searchTerm) || scopes.some(scopeHasMatch)
    );

  return (
    <TabsContent value={managerName} className='space-y-4'>
      <div className='flex items-center gap-2'>
        <Input
          value={searchValue}
          onChange={(event) => setSearchValue(event.target.value)}
          placeholder='Search package ID or PURL...'
        />
        {searchValue && (
          <Button
            type='button'
            variant='secondary'
            size='icon'
            className='shrink-0'
            onClick={() => setSearchValue('')}
            aria-label='Clear search'
          >
            <X className='size-4' />
          </Button>
        )}
      </div>

      {searchTerm && !hasMatches && (
        <div className='text-muted-foreground text-sm'>
          No packages match your search. Showing the full graph.
        </div>
      )}

      {graph.projectGroups.length === 0 ? (
        <div className='text-muted-foreground text-sm'>
          No scopes are available for this dependency graph.
        </div>
      ) : (
        <div className='space-y-2'>
          {graph.projectGroups.map(({ packageCount, projectLabel, scopes }) => {
            const projectOpen =
              searchTerm.length > 0 && scopes.some(scopeHasMatch);

            return scopes.length > 0 ? (
              // Keying on the search term remounts the tree whenever the search
              // changes, so the `defaultOpen` auto-expansion is recomputed for
              // the new matches while leaving nodes freely toggleable in
              // between.
              <Collapsible
                key={`${projectLabel}-${searchTerm}`}
                className='space-y-2'
                defaultOpen={projectOpen}
              >
                <TreeToggle>
                  <div className='flex min-w-0 flex-wrap items-center gap-2'>
                    <span className='block min-w-0 text-sm font-semibold break-all'>
                      <HighlightedMatch
                        searchTerm={searchTerm}
                        text={projectLabel}
                      />
                    </span>
                    <PackageCountBadge count={packageCount} />
                  </div>
                </TreeToggle>

                <CollapsibleContent>
                  <div className='space-y-2'>
                    {scopes.map(
                      (
                        {
                          packageCount,
                          rootNodeIndexes,
                          scopeName,
                          scopeLabel,
                        },
                        scopePosition
                      ) => {
                        const scopeOpen =
                          searchTerm.length > 0 &&
                          rootNodeIndexes.some(matchesNodeSubtree);

                        return (
                          <TreeBranch
                            key={scopeName}
                            isLast={scopePosition === scopes.length - 1}
                          >
                            <Collapsible
                              className='space-y-2'
                              defaultOpen={scopeOpen}
                            >
                              <TreeToggle>
                                <div className='flex min-w-0 flex-wrap items-center gap-2'>
                                  {scopeLabel && (
                                    <Badge variant='outline'>
                                      <HighlightedMatch
                                        searchTerm={searchTerm}
                                        text={scopeLabel}
                                      />
                                    </Badge>
                                  )}
                                  <PackageCountBadge count={packageCount} />
                                </div>
                              </TreeToggle>

                              <CollapsibleContent className='pt-2'>
                                <div className='space-y-2'>
                                  {rootNodeIndexes.map(
                                    (nodeIndex, nodePosition) => (
                                      <DependencyTreeNode
                                        key={`${scopeName}-${nodeIndex}`}
                                        adjacency={adjacency}
                                        graph={graph}
                                        isLast={
                                          nodePosition ===
                                          rootNodeIndexes.length - 1
                                        }
                                        matchesNodeSubtree={matchesNodeSubtree}
                                        nodeIndex={nodeIndex}
                                        packageIdType={packageIdType}
                                        path={new Set<number>()}
                                        searchTerm={searchTerm}
                                      />
                                    )
                                  )}
                                </div>
                              </CollapsibleContent>
                            </Collapsible>
                          </TreeBranch>
                        );
                      }
                    )}
                  </div>
                </CollapsibleContent>
              </Collapsible>
            ) : (
              <div key={projectLabel} className='flex items-start gap-2'>
                <div className='mt-[3px] size-4 shrink-0' />
                <div className='flex min-w-0 flex-wrap items-center gap-2'>
                  <span className='block min-w-0 text-sm font-semibold break-all'>
                    <HighlightedMatch
                      searchTerm={searchTerm}
                      text={projectLabel}
                    />
                  </span>
                  <PackageCountBadge count={packageCount} />
                </div>
              </div>
            );
          })}
        </div>
      )}
    </TabsContent>
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
