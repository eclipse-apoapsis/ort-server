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

import type { DependencyGraph, Identifier } from '@/api';
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

export const formatDependencyNodeLabel = (
  pkg: Identifier | undefined
): string => {
  if (!pkg) return '';

  if (pkg.type) {
    return identifierToString(pkg);
  }

  return [pkg.namespace, pkg.name, pkg.version].filter(Boolean).join(':');
};

export const formatDependencyGraphPackageLabel = (
  graph: DependencyGraph,
  packageIndex: number,
  packageIdType: PackageIdType
): string => {
  const purl = graph.purls[packageIndex] as string | null | undefined;
  if (packageIdType === 'PURL' && purl) return purl;

  return formatDependencyNodeLabel(graph.packages[packageIndex]);
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

//
// Unit tests for dependency graph helpers.
//

if (import.meta.vitest) {
  const { describe, expect, it } = import.meta.vitest;

  const graph: DependencyGraph = {
    edges: [
      { from: 0, to: 1 },
      { from: 1, to: 2 },
    ],
    nodes: [
      { fragment: 0, linkage: 'PROJECT_DYNAMIC', packageCount: 2, pkg: 0 },
      { fragment: 1, linkage: 'DYNAMIC', packageCount: 1, pkg: 1 },
      { fragment: 2, linkage: 'DYNAMIC', packageCount: 0, pkg: 2 },
    ],
    packageCount: 3,
    packages: [
      { name: 'root', namespace: 'com.example', type: 'Maven', version: '1.0' },
      {
        name: 'library',
        namespace: 'com.example',
        type: 'Maven',
        version: '2.0',
      },
      { name: 'leaf', namespace: 'com.example', type: 'Maven', version: '3.0' },
    ],
    purls: [
      null as unknown as string,
      'pkg:maven/com.example/library@2.0',
      'pkg:maven/com.example/leaf@3.0',
    ],
    projectGroups: [],
  };

  describe('dependency graph helpers', () => {
    it('formats dependency node labels from identifiers', () => {
      expect(formatDependencyNodeLabel(graph.packages[0])).toBe(
        'Maven:com.example:root:1.0'
      );
    });

    it('returns an empty label for missing identifiers', () => {
      expect(formatDependencyNodeLabel(undefined)).toBe('');
    });

    it('matches a subtree when a descendant matches the search term', () => {
      const matcher = createNodeSubtreeMatcher(
        graph,
        buildAdjacencyMap(graph),
        'leaf',
        'ORT_ID'
      );

      expect(matcher(0)).toBe(true);
      expect(matcher(1)).toBe(true);
      expect(matcher(2)).toBe(true);
    });

    it('does not recurse forever on cyclic graphs', () => {
      const cyclicGraph: DependencyGraph = {
        ...graph,
        edges: [
          { from: 0, to: 1 },
          { from: 1, to: 0 },
        ],
      };
      const matcher = createNodeSubtreeMatcher(
        cyclicGraph,
        buildAdjacencyMap(cyclicGraph),
        'library',
        'ORT_ID'
      );

      expect(matcher(0)).toBe(true);
      expect(matcher(1)).toBe(true);
    });

    it('formats dependency graph package labels from purls when preferred', () => {
      expect(formatDependencyGraphPackageLabel(graph, 1, 'PURL')).toBe(
        'pkg:maven/com.example/library@2.0'
      );
      expect(formatDependencyGraphPackageLabel(graph, 0, 'PURL')).toBe(
        'Maven:com.example:root:1.0'
      );
    });
  });
}
