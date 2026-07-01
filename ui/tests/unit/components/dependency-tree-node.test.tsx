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
        isLast={true}
        matchesNodeSubtree={() => true}
        nodeIndex={1}
        packageIdType='ORT_ID'
        path={new Set<number>([0, 1])}
        searchTerm='library'
      />
    );

    expect(markup).toContain('cycle');
  });

  it('expands the path to a matching descendant', () => {
    const markup = renderToStaticMarkup(
      <DependencyTreeNode
        adjacency={new Map<number, number[]>([[0, [1]]])}
        graph={{
          ...graph,
          edges: [{ from: 0, to: 1 }],
        }}
        isLast={true}
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

  it('keeps the full graph intact while searching, collapsing the transitive dependencies of matches', () => {
    const searchGraph: DependencyGraph = {
      edges: [
        { from: 0, to: 1 },
        { from: 0, to: 3 },
        { from: 1, to: 2 },
      ],
      nodes: [
        { fragment: 0, linkage: 'PROJECT_DYNAMIC', packageCount: 2, pkg: 0 },
        { fragment: 1, linkage: 'DYNAMIC', packageCount: 1, pkg: 1 },
        { fragment: 2, linkage: 'DYNAMIC', packageCount: 0, pkg: 2 },
        { fragment: 3, linkage: 'DYNAMIC', packageCount: 0, pkg: 3 },
      ],
      packageCount: 4,
      packages: [
        {
          name: 'root',
          namespace: 'com.example',
          type: 'Maven',
          version: '1.0',
        },
        {
          name: 'library',
          namespace: 'com.example',
          type: 'Maven',
          version: '2.0',
        },
        {
          name: 'transitive',
          namespace: 'com.example',
          type: 'Maven',
          version: '3.0',
        },
        {
          name: 'other',
          namespace: 'com.example',
          type: 'Maven',
          version: '4.0',
        },
      ],
      purls: [] as unknown as string[],
      projectGroups: [],
    };
    const searchAdjacency = new Map<number, number[]>([
      [0, [1, 3]],
      [1, [2]],
    ]);

    const markup = renderToStaticMarkup(
      <DependencyTreeNode
        adjacency={searchAdjacency}
        graph={searchGraph}
        isLast={true}
        // The subtrees of the root and the match contain "library".
        matchesNodeSubtree={(nodeIndex) => nodeIndex === 0 || nodeIndex === 1}
        nodeIndex={0}
        packageIdType='ORT_ID'
        path={new Set<number>()}
        searchTerm='library'
      />
    );

    // The path to the match is expanded and the match is shown (the label is
    // split into spans by the search highlighter, so match on the term)...
    expect(markup).toContain('root');
    expect(markup).toContain('library');
    // ...a non-matching sibling branch is still present (graph stays intact)...
    expect(markup).toContain('Maven:com.example:other:4.0');
    // ...but the match's own transitive dependency stays collapsed...
    expect(markup).not.toContain('transitive');
    // ...and the match still renders as an expandable (collapsed) node.
    expect(markup).toContain('lucide-square-plus');
  });

  it('renders the node label and linkage badge', () => {
    const markup = renderToStaticMarkup(
      <DependencyTreeNode
        adjacency={adjacency}
        graph={graph}
        isLast={true}
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
        isLast={true}
        matchesNodeSubtree={() => true}
        nodeIndex={1}
        packageIdType='PURL'
        path={new Set<number>()}
      />
    );

    expect(markup).toContain('pkg:maven/com.example/library@2.0');
  });
});
