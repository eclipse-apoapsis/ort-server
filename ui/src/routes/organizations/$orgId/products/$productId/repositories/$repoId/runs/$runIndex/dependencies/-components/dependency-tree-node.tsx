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

import type { DependencyGraph } from '@/api';
import { Badge } from '@/components/ui/badge';
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '@/components/ui/collapsible';
import type { PackageIdType } from '@/schemas';
import {
  formatDependencyGraphPackageLabel,
  type AdjacencyMap,
} from './dependency-graph-utils';
import { HighlightedMatch } from './highlighted-match';
import { TreeBranch } from './tree-branch';
import { TreeToggleIcon } from './tree-toggle-icon';

export const DependencyTreeNode = ({
  adjacency,
  graph,
  isLast,
  matchesNodeSubtree,
  nodeIndex,
  packageIdType,
  path,
  searchTerm = '',
}: {
  adjacency: AdjacencyMap;
  graph: DependencyGraph;
  isLast: boolean;
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
  const nodeLabel = formatDependencyGraphPackageLabel(
    graph,
    node.pkg,
    packageIdType
  );
  const hasChildren = childIndexes.length > 0;
  // While searching, the full graph stays intact: a node only starts out
  // expanded when one of its descendants matches, so the path to a finding is
  // revealed while the finding's own subtree (and unrelated branches) stay
  // collapsed. This is only the initial state (`defaultOpen`) — the node stays
  // uncontrolled so it can still be toggled by hand. The tree is remounted when
  // the search term changes (see the keys in the route), which recomputes these
  // defaults for the new search.
  const defaultOpen =
    searchTerm.length > 0 && childIndexes.some(matchesNodeSubtree);
  const nextPath = new Set(path);
  nextPath.add(nodeIndex);

  return (
    <TreeBranch isLast={isLast}>
      <Collapsible defaultOpen={defaultOpen}>
        {hasChildren ? (
          <CollapsibleTrigger asChild>
            <button
              type='button'
              className='group/toggle flex w-full items-start gap-2 rounded-sm text-left'
            >
              <TreeToggleIcon />
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
            <div className='mt-[3px] size-4 shrink-0' />
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
            {childIndexes.map((childNodeIndex, childPosition) => {
              const childIsLast = childPosition === childIndexes.length - 1;

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
                  <TreeBranch
                    key={`${nodeIndex}-${childNodeIndex}-cycle`}
                    isLast={childIsLast}
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
                  </TreeBranch>
                );
              }

              return (
                <DependencyTreeNode
                  key={`${nodeIndex}-${childNodeIndex}`}
                  adjacency={adjacency}
                  graph={graph}
                  isLast={childIsLast}
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
    </TreeBranch>
  );
};
