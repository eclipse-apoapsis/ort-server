/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import { memo } from 'react';

import type { DependencyGraph } from '@/api';
import { Badge } from '@/components/ui/badge';
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '@/components/ui/collapsible';
import { cn } from '@/lib/utils';
import type { PackageIdType } from '@/schemas';
import {
  scopeHasSearchMatch,
  type AdjacencyMap,
} from './dependency-graph-utils';
import { DependencyTreeNode } from './dependency-tree-node';
import { HighlightedMatch } from './highlighted-match';
import { PackageCountBadge } from './package-count-badge';
import { TreeBranch } from './tree-branch';
import { TreeToggleIcon } from './tree-toggle-icon';

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

type DependencyTreeProps = {
  adjacency: AdjacencyMap;
  graph: DependencyGraph;
  matchesNodeSubtree: (nodeIndex: number) => boolean;
  packageIdType: PackageIdType;
  searchTerm: string;
};

/**
 * Renders the projects, scopes and dependencies of a graph as a tree. It is
 * memoized so that typing into the search field, which only changes the search
 * term after a delay, does not render the tree again on every keystroke.
 */
export const DependencyTree = memo(function DependencyTree({
  adjacency,
  graph,
  matchesNodeSubtree,
  packageIdType,
  searchTerm,
}: DependencyTreeProps) {
  if (graph.projectGroups.length === 0) {
    return (
      <div className='text-muted-foreground text-sm'>
        No scopes are available for this dependency graph.
      </div>
    );
  }

  return (
    <div className='space-y-2'>
      {graph.projectGroups.map(({ packageCount, projectLabel, scopes }) => {
        const projectOpen =
          searchTerm.length > 0 &&
          scopes.some((scope) =>
            scopeHasSearchMatch(scope, searchTerm, matchesNodeSubtree)
          );

        return scopes.length > 0 ? (
          // Keying on the search term remounts the tree whenever the search
          // changes, so the `defaultOpen` auto-expansion is recomputed for the
          // new matches while leaving nodes freely toggleable in between.
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
                    { packageCount, rootNodeIndexes, scopeName, scopeLabel },
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
                <HighlightedMatch searchTerm={searchTerm} text={projectLabel} />
              </span>
              <PackageCountBadge count={packageCount} />
            </div>
          </div>
        );
      })}
    </div>
  );
});
