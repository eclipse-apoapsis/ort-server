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

import { describe, expect, it } from 'vitest';

import type { DependencyGraph } from '@/api';
import {
  buildAdjacencyMap,
  createNodeSubtreeMatcher,
  formatDependencyGraphPackageLabel,
} from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/dependencies/-components/dependency-graph-utils';
import { packageIdTypeSchema } from '@/schemas';

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

const createGraph = (
  names: string[],
  edges: [number, number][]
): DependencyGraph => ({
  edges: edges.map(([from, to]) => ({ from, to })),
  nodes: names.map((_, index) => ({
    fragment: 0,
    linkage: 'DYNAMIC',
    packageCount: 0,
    pkg: index,
  })),
  packageCount: names.length,
  packages: names.map((name) => ({
    name,
    namespace: 'com.example',
    type: 'Maven',
    version: '1.0',
  })),
  purls: names.map(() => null as unknown as string),
  projectGroups: [],
});

const createMatcher = (graph: DependencyGraph, searchTerm: string) =>
  createNodeSubtreeMatcher(
    graph,
    buildAdjacencyMap(graph),
    searchTerm,
    packageIdTypeSchema.enum.ORT_ID
  );

describe('createNodeSubtreeMatcher', () => {
  // A -> B, B -> A, A -> M, where M matches.
  const cycleIntoMatch = createGraph(
    ['a', 'b', 'match'],
    [
      [0, 1],
      [1, 0],
      [0, 2],
    ]
  );

  it('matches every node of a cycle that leads to a match when the first node is queried first', () => {
    const matcher = createMatcher(cycleIntoMatch, 'match');

    expect(matcher(0)).toBe(true);
    expect(matcher(1)).toBe(true);
    expect(matcher(2)).toBe(true);
  });

  it('matches every node of a cycle that leads to a match when the second node is queried first', () => {
    const matcher = createMatcher(cycleIntoMatch, 'match');

    expect(matcher(1)).toBe(true);
    expect(matcher(0)).toBe(true);
    expect(matcher(2)).toBe(true);
  });

  it('matches regardless of the order of the edges', () => {
    const reorderedGraph = createGraph(
      ['a', 'b', 'match'],
      [
        [0, 2],
        [1, 0],
        [0, 1],
      ]
    );
    const matcher = createMatcher(reorderedGraph, 'match');

    expect(matcher(1)).toBe(true);
    expect(matcher(0)).toBe(true);
  });

  it('does not match a self-cycle without a match', () => {
    const matcher = createMatcher(createGraph(['a'], [[0, 0]]), 'match');

    expect(matcher(0)).toBe(false);
  });

  it('matches a self-cycle on a matching node', () => {
    const matcher = createMatcher(createGraph(['match'], [[0, 0]]), 'match');

    expect(matcher(0)).toBe(true);
  });

  it('does not match a longer cycle without a match', () => {
    const matcher = createMatcher(
      createGraph(
        ['a', 'b', 'c', 'other'],
        [
          [0, 1],
          [1, 2],
          [2, 0],
          [3, 0],
        ]
      ),
      'match'
    );

    expect(matcher(0)).toBe(false);
    expect(matcher(1)).toBe(false);
    expect(matcher(2)).toBe(false);
    expect(matcher(3)).toBe(false);
  });

  it('matches a longer cycle feeding into a match and its ancestors only', () => {
    const matcher = createMatcher(
      createGraph(
        ['root', 'a', 'b', 'c', 'match', 'sibling'],
        [
          [0, 1],
          [0, 5],
          [1, 2],
          [2, 3],
          [3, 1],
          [3, 4],
        ]
      ),
      'match'
    );

    expect(matcher(2)).toBe(true);
    expect(matcher(1)).toBe(true);
    expect(matcher(3)).toBe(true);
    expect(matcher(0)).toBe(true);
    expect(matcher(4)).toBe(true);
    expect(matcher(5)).toBe(false);
  });

  it('does not match nodes that are only reachable from a match', () => {
    const matcher = createMatcher(
      createGraph(
        ['match', 'a', 'b'],
        [
          [0, 1],
          [1, 2],
          [2, 1],
        ]
      ),
      'match'
    );

    expect(matcher(1)).toBe(false);
    expect(matcher(2)).toBe(false);
    expect(matcher(0)).toBe(true);
  });

  it('evaluates a deep dependency chain without overflowing the call stack', () => {
    const length = 100_000;
    const names = Array.from({ length }, (_, index) =>
      index === length - 1 ? 'match' : `node-${index}`
    );
    const edges = Array.from(
      { length: length - 1 },
      (_, index) => [index, index + 1] as [number, number]
    );
    const matcher = createMatcher(createGraph(names, edges), 'match');

    expect(matcher(0)).toBe(true);
    expect(matcher(length / 2)).toBe(true);
    expect(matcher(length - 1)).toBe(true);
  });

  it('matches every node for an empty search term', () => {
    const matcher = createMatcher(createGraph(['a'], []), '');

    expect(matcher(0)).toBe(true);
  });
});

describe('dependency graph helpers', () => {
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
      packageIdTypeSchema.enum.ORT_ID
    );

    expect(matcher(0)).toBe(true);
    expect(matcher(1)).toBe(true);
  });

  it('formats dependency graph package labels from identifiers', () => {
    expect(
      formatDependencyGraphPackageLabel(
        graph,
        0,
        packageIdTypeSchema.enum.ORT_ID
      )
    ).toBe('Maven:com.example:root:1.0');
  });

  it('formats dependency graph package labels from purls when preferred', () => {
    expect(
      formatDependencyGraphPackageLabel(graph, 1, packageIdTypeSchema.enum.PURL)
    ).toBe('pkg:maven/com.example/library@2.0');
    expect(
      formatDependencyGraphPackageLabel(graph, 0, packageIdTypeSchema.enum.PURL)
    ).toBe('Maven:com.example:root:1.0');
  });

  it('matches a subtree when a descendant matches the search term', () => {
    const matcher = createNodeSubtreeMatcher(
      graph,
      buildAdjacencyMap(graph),
      'leaf',
      packageIdTypeSchema.enum.ORT_ID
    );

    expect(matcher(0)).toBe(true);
    expect(matcher(1)).toBe(true);
    expect(matcher(2)).toBe(true);
  });

  it('returns an empty label for missing identifiers', () => {
    const graphWithMissingPackage: DependencyGraph = {
      ...graph,
      packages: [undefined as unknown as DependencyGraph['packages'][number]],
    };

    expect(
      formatDependencyGraphPackageLabel(
        graphWithMissingPackage,
        0,
        packageIdTypeSchema.enum.ORT_ID
      )
    ).toBe('');
  });
});
