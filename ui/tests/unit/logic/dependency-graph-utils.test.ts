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

  it('formats dependency graph package labels from identifiers', () => {
    expect(formatDependencyGraphPackageLabel(graph, 0, 'ORT_ID')).toBe(
      'Maven:com.example:root:1.0'
    );
  });

  it('formats dependency graph package labels from purls when preferred', () => {
    expect(formatDependencyGraphPackageLabel(graph, 1, 'PURL')).toBe(
      'pkg:maven/com.example/library@2.0'
    );
    expect(formatDependencyGraphPackageLabel(graph, 0, 'PURL')).toBe(
      'Maven:com.example:root:1.0'
    );
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

  it('returns an empty label for missing identifiers', () => {
    const graphWithMissingPackage: DependencyGraph = {
      ...graph,
      packages: [undefined as unknown as DependencyGraph['packages'][number]],
    };

    expect(
      formatDependencyGraphPackageLabel(graphWithMissingPackage, 0, 'ORT_ID')
    ).toBe('');
  });
});
