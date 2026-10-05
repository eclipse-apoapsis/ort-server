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
import { identifierToString } from '@/helpers/identifier-conversion';
import { PackageIdType, packageIdTypeSchema } from '@/schemas';

export type AdjacencyMap = Map<number, number[]>;

export const buildAdjacencyMap = (graph: DependencyGraph): AdjacencyMap => {
  const adjacency = new Map<number, number[]>();

  graph.edges.forEach(({ from, to }) => {
    const targets = adjacency.get(from);

    if (targets) {
      targets.push(to);
    } else {
      adjacency.set(from, [to]);
    }
  });

  return adjacency;
};

export const formatDependencyGraphPackageLabel = (
  graph: DependencyGraph,
  packageIndex: number,
  packageIdType: PackageIdType
): string => {
  const purl = graph.purls[packageIndex] as string | null | undefined;
  if (packageIdType === packageIdTypeSchema.enum.PURL && purl) return purl;

  return identifierToString(graph.packages[packageIndex]);
};

export const normalizeSearchTerm = (searchTerm: string): string =>
  searchTerm.trim().toLowerCase();

export const matchesSearch = (
  value: string | null | undefined,
  searchTerm: string
): boolean =>
  searchTerm.length > 0 && value?.toLowerCase().includes(searchTerm) === true;

export const createNodeSubtreeMatcher = (
  graph: DependencyGraph,
  adjacency: AdjacencyMap,
  searchTerm: string,
  packageIdType: PackageIdType
): ((nodeIndex: number) => boolean) => {
  if (!searchTerm) {
    return () => true;
  }

  // Edges may reference indexes without a node entry, so size the arrays to cover them as well.
  let nodeCount = graph.nodes.length;
  adjacency.forEach((targets, from) => {
    nodeCount = Math.max(nodeCount, from + 1);
    targets.forEach((to) => (nodeCount = Math.max(nodeCount, to + 1)));
  });

  const nodeMatches = (nodeIndex: number): boolean => {
    const node = graph.nodes[nodeIndex];

    return matchesSearch(
      node
        ? formatDependencyGraphPackageLabel(graph, node.pkg, packageIdType)
        : '',
      searchTerm
    );
  };

  // Find the strongly connected components with Tarjan's algorithm (R. Tarjan, "Depth-First Search
  // and Linear Graph Algorithms", SIAM Journal on Computing 1(2), 1972, pp. 146–160,
  // https://doi.org/10.1137/0201010).
  //
  // The recursive depth-first search of the paper is replaced by an explicit stack of traversal frames,
  // each holding the node's visit index, its low link and the position of the next child to visit, so
  // deep dependency chains cannot overflow the call stack.
  //
  //A component is completed only after every component reachable from it, so its result
  // can be derived from its own nodes and the already final results of the components it points
  // to. All nodes of a cycle thereby get the same result, independent of the order of evaluation.
  const visitIndex = new Int32Array(nodeCount).fill(-1);
  const onComponentStack = new Uint8Array(nodeCount);
  const subtreeMatches = new Uint8Array(nodeCount);
  const componentStack: number[] = [];
  const traversalStack: {
    nodeIndex: number;
    visitIndex: number;
    lowLink: number;
    children: number[];
    nextChild: number;
  }[] = [];
  let nextVisitIndex = 0;

  const visit = (nodeIndex: number) => {
    visitIndex[nodeIndex] = nextVisitIndex;
    componentStack.push(nodeIndex);
    onComponentStack[nodeIndex] = 1;
    traversalStack.push({
      nodeIndex,
      visitIndex: nextVisitIndex,
      lowLink: nextVisitIndex,
      children: adjacency.get(nodeIndex) ?? [],
      nextChild: 0,
    });
    nextVisitIndex++;
  };

  const completeComponent = (rootIndex: number) => {
    const members: number[] = [];
    let member = -1;
    while (member !== rootIndex) {
      member = componentStack.pop() ?? rootIndex;
      onComponentStack[member] = 0;
      members.push(member);
    }

    // Children within the component are not marked yet, so only other components contribute here.
    const componentMatches = members.some(
      (memberIndex) =>
        nodeMatches(memberIndex) ||
        (adjacency.get(memberIndex) ?? []).some(
          (childIndex) => subtreeMatches[childIndex] === 1
        )
    );

    if (componentMatches) {
      members.forEach((memberIndex) => (subtreeMatches[memberIndex] = 1));
    }
  };

  for (let startIndex = 0; startIndex < nodeCount; startIndex++) {
    if (visitIndex[startIndex] !== -1) continue;

    visit(startIndex);

    for (
      let frame = traversalStack.at(-1);
      frame;
      frame = traversalStack.at(-1)
    ) {
      const childIndex = frame.children[frame.nextChild];

      if (childIndex !== undefined) {
        frame.nextChild++;
        const childVisitIndex = visitIndex[childIndex] ?? -1;

        if (childVisitIndex === -1) {
          visit(childIndex);
        } else if (onComponentStack[childIndex] === 1) {
          frame.lowLink = Math.min(frame.lowLink, childVisitIndex);
        }

        continue;
      }

      traversalStack.pop();

      const parent = traversalStack.at(-1);
      if (parent) {
        parent.lowLink = Math.min(parent.lowLink, frame.lowLink);
      }

      if (frame.lowLink === frame.visitIndex) {
        completeComponent(frame.nodeIndex);
      }
    }
  }

  return (nodeIndex: number) => subtreeMatches[nodeIndex] === 1;
};
