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

import { ChevronRight } from 'lucide-react';

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
  formatDependencyGraphPackageLabel,
  matchesSearch,
  type AdjacencyMap,
} from './dependency-graph-utils';
import { HighlightedMatch } from './highlighted-match';

export const DependencyTreeNode = ({
  adjacency,
  graph,
  matchesNodeSubtree,
  nodeIndex,
  packageIdType,
  path,
  searchTerm = '',
}: {
  adjacency: AdjacencyMap;
  graph: DependencyGraph;
  matchesNodeSubtree: (nodeIndex: number) => boolean;
  nodeIndex: number;
  packageIdType: PackageIdType;
  path: Set<number>;
  searchTerm?: string;
}) => {
  const node = graph.nodes[nodeIndex];
  if (!node) return null;

  const pkg = graph.packages[node.pkg];
  if (!pkg) return null;

  const childIndexes = (adjacency.get(nodeIndex) ?? []).filter(
    (childNodeIndex) => graph.nodes[childNodeIndex]
  );
  const visibleChildIndexes = searchTerm
    ? childIndexes.filter(matchesNodeSubtree)
    : childIndexes;
  const nodeLabel = formatDependencyGraphPackageLabel(
    graph,
    node.pkg,
    packageIdType
  );
  const nodeMatches = matchesSearch(nodeLabel, searchTerm);
  const hasChildren = visibleChildIndexes.length > 0;
  const isVisible = !searchTerm || nodeMatches || hasChildren;
  const nextPath = new Set(path);
  nextPath.add(nodeIndex);

  if (!isVisible) return null;

  return (
    <div className={cn('space-y-2', path.size > 0 && 'ml-4 border-l pl-4')}>
      <Collapsible open={searchTerm ? hasChildren : undefined}>
        {hasChildren ? (
          <CollapsibleTrigger asChild>
            <button
              type='button'
              className='flex w-full items-start gap-2 rounded-sm text-left'
            >
              <ChevronRight className='text-muted-foreground mt-0.5 size-4 shrink-0 transition-transform group-data-[state=open]:rotate-90' />
              <div className='flex min-w-0 flex-1 flex-wrap items-center gap-2'>
                <span className='min-w-0 text-sm font-medium break-all'>
                  <HighlightedMatch searchTerm={searchTerm} text={nodeLabel} />
                </span>

                <Badge variant='outline'>{node.linkage}</Badge>
                <Badge variant='secondary'>
                  {node.packageCount} package
                  {node.packageCount === 1 ? '' : 's'}
                </Badge>
              </div>
            </button>
          </CollapsibleTrigger>
        ) : (
          <div className='flex items-start gap-2'>
            <div className='mt-0.5 size-4 shrink-0' />
            <div className='flex min-w-0 flex-1 flex-wrap items-center gap-2'>
              <span className='min-w-0 text-sm font-medium break-all'>
                <HighlightedMatch searchTerm={searchTerm} text={nodeLabel} />
              </span>

              <Badge variant='outline'>{node.linkage}</Badge>
            </div>
          </div>
        )}

        {hasChildren && (
          <CollapsibleContent className='space-y-2 pt-2'>
            {visibleChildIndexes.map((childNodeIndex) => {
              if (path.has(childNodeIndex)) {
                const childNode = graph.nodes[childNodeIndex];
                const childLabel = childNode
                  ? formatDependencyGraphPackageLabel(
                      graph,
                      childNode.pkg,
                      packageIdType
                    )
                  : '';

                return (
                  <div
                    key={`${nodeIndex}-${childNodeIndex}-cycle`}
                    className='ml-4 border-l pl-4'
                  >
                    <div className='flex flex-wrap items-center gap-2'>
                      <span className='min-w-0 text-sm font-medium break-all'>
                        <HighlightedMatch
                          searchTerm={searchTerm}
                          text={childLabel}
                        />
                      </span>
                      <Badge variant='outline'>cycle</Badge>
                    </div>
                  </div>
                );
              }

              return (
                <DependencyTreeNode
                  key={`${nodeIndex}-${childNodeIndex}`}
                  adjacency={adjacency}
                  graph={graph}
                  matchesNodeSubtree={matchesNodeSubtree}
                  nodeIndex={childNodeIndex}
                  packageIdType={packageIdType}
                  path={nextPath}
                  searchTerm={searchTerm}
                />
              );
            })}
          </CollapsibleContent>
        )}
      </Collapsible>
    </div>
  );
};
