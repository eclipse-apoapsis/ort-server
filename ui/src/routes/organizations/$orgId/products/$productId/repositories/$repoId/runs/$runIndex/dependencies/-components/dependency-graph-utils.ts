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
import type { PackageIdType } from '@/schemas';

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
  if (packageIdType === 'PURL' && purl) return purl;

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

  const resultCache = new Map<number, boolean>();
  const nodesInProgress = new Set<number>();

  const matchesNodeSubtree = (nodeIndex: number): boolean => {
    const cachedResult = resultCache.get(nodeIndex);
    if (cachedResult !== undefined) return cachedResult;

    if (nodesInProgress.has(nodeIndex)) {
      return false;
    }

    nodesInProgress.add(nodeIndex);

    const node = graph.nodes[nodeIndex];
    const nodeMatches = matchesSearch(
      node
        ? formatDependencyGraphPackageLabel(graph, node.pkg, packageIdType)
        : '',
      searchTerm
    );
    const childMatches = (adjacency.get(nodeIndex) ?? []).some(
      matchesNodeSubtree
    );
    const result = nodeMatches || childMatches;

    nodesInProgress.delete(nodeIndex);
    resultCache.set(nodeIndex, result);

    return result;
  };

  return matchesNodeSubtree;
};
