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
import { ChevronRight, X } from 'lucide-react';
import { useState } from 'react';

import type { DependencyGraph } from '@/api';
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
import { useDebounce } from '@/hooks/use-debounce';
import { cn } from '@/lib/utils';
import { useUserSettingsStore } from '@/store/user-settings.store';
import {
  buildAdjacencyMap,
  createNodeSubtreeMatcher,
  matchesSearch,
  normalizeSearchTerm,
} from './-components/dependency-graph-utils';
import { DependencyTreeNode } from './-components/dependency-tree-node';
import { HighlightedMatch } from './-components/highlighted-match';

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
  isOpen,
  className,
}: {
  children: React.ReactNode;
  isOpen?: boolean;
  className?: string;
}) => (
  <CollapsibleTrigger asChild>
    <button
      type='button'
      className={cn(
        'flex w-full items-start gap-2 rounded-sm text-left',
        className
      )}
    >
      <ChevronRight
        className={cn(
          'text-muted-foreground mt-0.5 size-4 shrink-0 transition-transform',
          isOpen && 'rotate-90'
        )}
      />
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

  const visibleProjectGroups = graph.projectGroups.filter(
    ({ projectLabel, scopes }) => {
      if (!searchTerm) return true;
      if (matchesSearch(projectLabel, searchTerm)) return true;

      return scopes.some(({ rootNodeIndexes, scopeLabel }) => {
        if (matchesSearch(scopeLabel, searchTerm)) return true;

        return rootNodeIndexes.some(matchesNodeSubtree);
      });
    }
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

      {visibleProjectGroups.length === 0 ? (
        <div className='text-muted-foreground text-sm'>
          {searchTerm
            ? 'No matching dependencies found for this package manager.'
            : 'No scopes are available for this dependency graph.'}
        </div>
      ) : (
        <div className='space-y-2'>
          {visibleProjectGroups.map(
            ({ packageCount, projectLabel, scopes }) => {
              const visibleScopes = scopes.filter(
                ({ rootNodeIndexes, scopeLabel }) => {
                  if (!searchTerm) return true;
                  if (matchesSearch(scopeLabel, searchTerm)) return true;

                  return rootNodeIndexes.some(matchesNodeSubtree);
                }
              );

              const projectHasVisibleScopes = visibleScopes.length > 0;
              const projectOpen = Boolean(
                searchTerm &&
                projectHasVisibleScopes &&
                !matchesSearch(projectLabel, searchTerm)
              );

              return projectHasVisibleScopes ? (
                <Collapsible
                  key={projectLabel}
                  className='group space-y-2'
                  open={searchTerm ? projectOpen : undefined}
                >
                  <TreeToggle isOpen={searchTerm ? projectOpen : undefined}>
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

                  <CollapsibleContent className='ml-4 border-l pl-4'>
                    <div className='space-y-2'>
                      {visibleScopes.map(
                        ({
                          packageCount,
                          rootNodeIndexes,
                          scopeName,
                          scopeLabel,
                        }) => {
                          const scopeOpen = Boolean(
                            searchTerm &&
                            rootNodeIndexes.some(matchesNodeSubtree) &&
                            !matchesSearch(scopeLabel, searchTerm)
                          );

                          return (
                            <Collapsible
                              key={scopeName}
                              className='group space-y-2'
                              open={searchTerm ? scopeOpen : undefined}
                            >
                              <TreeToggle
                                isOpen={searchTerm ? scopeOpen : undefined}
                              >
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

                              <CollapsibleContent className='ml-4 border-l pt-2 pl-4'>
                                <div className='space-y-2'>
                                  {rootNodeIndexes.map((nodeIndex) => (
                                    <DependencyTreeNode
                                      key={`${scopeName}-${nodeIndex}`}
                                      adjacency={adjacency}
                                      graph={graph}
                                      matchesNodeSubtree={matchesNodeSubtree}
                                      nodeIndex={nodeIndex}
                                      packageIdType={packageIdType}
                                      path={new Set<number>()}
                                      searchTerm={searchTerm}
                                    />
                                  ))}
                                </div>
                              </CollapsibleContent>
                            </Collapsible>
                          );
                        }
                      )}
                    </div>
                  </CollapsibleContent>
                </Collapsible>
              ) : (
                <div key={projectLabel} className='flex items-start gap-2'>
                  <div className='mt-0.5 size-4 shrink-0' />
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
            }
          )}
        </div>
      )}
    </TabsContent>
  );
};

const DependenciesComponent = () => {
  const params = Route.useParams();

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
        <CardDescription>Dependency graphs of the ORT run.</CardDescription>
      </CardHeader>
      <CardContent>
        {managerEntries.length === 0 ? (
          <div className='text-muted-foreground text-sm'>
            No dependency graphs are available for this run.
          </div>
        ) : (
          <Tabs defaultValue={defaultManager} className='gap-4'>
            <TabsList className='h-auto w-full justify-start gap-1 overflow-x-auto'>
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
