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

import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import type { DependencyGraph } from '@/api';
import { DependencyTreeNode } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/dependencies/-components/dependency-tree-node';

const graph: DependencyGraph = {
  edges: [
    { from: 0, to: 1 },
    { from: 1, to: 0 },
  ],
  nodes: [
    { fragment: 0, linkage: 'PROJECT_DYNAMIC', packageCount: 1, pkg: 0 },
    { fragment: 1, linkage: 'DYNAMIC', packageCount: 1, pkg: 1 },
  ],
  packageCount: 2,
  packages: [
    { name: 'root', namespace: 'com.example', type: 'Maven', version: '1.0' },
    {
      name: 'library',
      namespace: 'com.example',
      type: 'Maven',
      version: '2.0',
    },
  ],
  purls: [null as unknown as string, 'pkg:maven/com.example/library@2.0'],
  projectGroups: [],
};
const adjacency = new Map<number, number[]>([
  [0, [1]],
  [1, [0]],
]);

describe('DependencyTreeNode', () => {
  it('renders a cycle badge for cyclic child nodes', () => {
    const markup = renderToStaticMarkup(
      <DependencyTreeNode
        adjacency={adjacency}
        graph={graph}
        matchesNodeSubtree={() => true}
        nodeIndex={1}
        packageIdType='ORT_ID'
        path={new Set<number>([0, 1])}
        searchTerm='library'
      />
    );

    expect(markup).toContain('cycle');
  });

  it('renders only matching branches when children are filtered', () => {
    const markup = renderToStaticMarkup(
      <DependencyTreeNode
        adjacency={new Map<number, number[]>([[0, [1]]])}
        graph={{
          ...graph,
          edges: [{ from: 0, to: 1 }],
        }}
        matchesNodeSubtree={(nodeIndex) => nodeIndex === 1}
        nodeIndex={0}
        packageIdType='ORT_ID'
        path={new Set<number>()}
        searchTerm='library'
      />
    );

    expect(markup).toContain('Maven:com.example:root:1.0');
    expect(markup).toContain('library');
    expect(markup).toContain('DYNAMIC');
  });

  it('renders the node label and linkage badge', () => {
    const markup = renderToStaticMarkup(
      <DependencyTreeNode
        adjacency={adjacency}
        graph={graph}
        matchesNodeSubtree={() => true}
        nodeIndex={0}
        packageIdType='ORT_ID'
        path={new Set<number>()}
      />
    );

    expect(markup).toContain('Maven:com.example:root:1.0');
    expect(markup).toContain('PROJECT_DYNAMIC');
  });

  it('renders the purl when purls are preferred', () => {
    const markup = renderToStaticMarkup(
      <DependencyTreeNode
        adjacency={new Map<number, number[]>([[0, [1]]])}
        graph={{
          ...graph,
          edges: [{ from: 0, to: 1 }],
        }}
        matchesNodeSubtree={() => true}
        nodeIndex={1}
        packageIdType='PURL'
        path={new Set<number>()}
      />
    );

    expect(markup).toContain('pkg:maven/com.example/library@2.0');
  });
});
