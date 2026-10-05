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

import { describe, expect, it, vi } from 'vitest';

import type { DependencyGraph } from '@/api';
import {
  buildAdjacencyMap,
  createNodeSubtreeMatcher,
} from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/dependencies/-components/dependency-graph-utils';
import {
  ancestorLastFlags,
  collapseRow,
  createDependencyTreeContext,
  expandRow,
  expansionForSearch,
  generateRows,
  generateRowsAfter,
  insertRows,
  OccurrenceIds,
  recordResolvedExpansion,
  setRowExpanded,
  takeRows,
  type DependencyTreeContext,
  type DependencyTreeRow,
  type ExpandableRow,
  type TreeExpansion,
} from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/dependencies/-components/dependency-tree-model';
import { packageIdTypeSchema } from '@/schemas';
import {
  createDependencyGraph,
  createGraph,
  createSharedDependencyGraph,
} from '../fixtures/dependency-graph';

const createContext = (
  graph: DependencyGraph,
  searchTerm: string,
  occurrenceIds?: OccurrenceIds
) => {
  const adjacency = buildAdjacencyMap(graph);

  return createDependencyTreeContext(
    graph,
    adjacency,
    createNodeSubtreeMatcher(
      graph,
      adjacency,
      searchTerm,
      packageIdTypeSchema.enum.ORT_ID
    ),
    occurrenceIds
  );
};

const generateAll = (
  graph: DependencyGraph,
  searchTerm = '',
  overrides = new Map<string, boolean>(),
  occurrenceIds = new OccurrenceIds()
) => {
  const context = createContext(graph, searchTerm, occurrenceIds);
  const expansion: TreeExpansion = { searchTerm, overrides };

  return { context, expansion, rows: [...generateRows(context, expansion)] };
};

const rowName = (graph: DependencyGraph, row: DependencyTreeRow) => {
  switch (row.kind) {
    case 'project':
      return graph.projectGroups[row.projectIndex]?.projectLabel;
    case 'scope':
      return graph.projectGroups[row.projectIndex]?.scopes[row.scopeIndex]
        ?.scopeName;
    default:
      return graph.packages[row.packageIndex]?.name;
  }
};

/**
 * Describes rows as indented lines, marking expanded rows with "-", collapsed
 * ones with "+" and cycles with "(cycle)".
 */
const describeRows = (
  graph: DependencyGraph,
  rows: readonly DependencyTreeRow[]
) =>
  rows.map((row) => {
    const marker =
      row.kind === 'cycle'
        ? ' (cycle)'
        : row.expandable
          ? row.expanded
            ? ' -'
            : ' +'
          : '';

    return `${'  '.repeat(row.depth)}${rowName(graph, row)}${marker}`;
  });

const findRow = (
  graph: DependencyGraph,
  rows: readonly DependencyTreeRow[],
  name: string,
  occurrence = 0
) => {
  const matching = rows.filter((row) => rowName(graph, row) === name);
  const row = matching[occurrence];
  if (row === undefined) throw new Error(`No row ${name} #${occurrence}.`);

  return row;
};

const findExpandableRow = (
  graph: DependencyGraph,
  rows: readonly DependencyTreeRow[],
  name: string,
  occurrence = 0
) => findRow(graph, rows, name, occurrence) as ExpandableRow;

const expandFully = (
  rows: readonly DependencyTreeRow[],
  index: number,
  context: DependencyTreeContext,
  expansion: TreeExpansion
) => {
  const expanded = expandRow(rows, index, context, expansion);

  return insertRows(expanded.rows, index + 1, [...expanded.rowsBelow]);
};

describe('generateRows', () => {
  it('shows only the projects without a search', () => {
    const graph = createDependencyGraph();
    const { rows } = generateAll(graph);

    expect(describeRows(graph, rows)).toEqual([
      'Gradle::app:1.0 +',
      'Gradle::lib:1.0 +',
      'Gradle::docs:1.0',
    ]);
    expect(rows.map((row) => row.isLast)).toEqual([false, false, true]);
  });

  it('expands the paths to all matches below every parent', () => {
    const graph = createDependencyGraph();
    const { rows } = generateAll(graph, 'logback');

    expect(describeRows(graph, rows)).toEqual([
      'Gradle::app:1.0 -',
      '  compileClasspath -',
      '    http-client -',
      '      logging-api -',
      '        logback-classic -',
      '          logback-core',
      '      json-lib',
      '    commons-text',
      '  testRuntimeClasspath -',
      '    test-lib -',
      '      logging-api -',
      '        logback-classic -',
      '          logback-core',
      'Gradle::lib:1.0 +',
      'Gradle::docs:1.0',
    ]);
  });

  it('records the position of each row and its ancestors for the connectors', () => {
    const graph = createDependencyGraph();
    const { rows } = generateAll(graph, 'logback');

    const logbackCore = findRow(graph, rows, 'logback-core');
    expect(logbackCore.depth).toBe(5);
    expect(logbackCore.isLast).toBe(true);
    // Project, scope, `http-client`, `logging-api` and `logback-classic`.
    expect(ancestorLastFlags(logbackCore)).toEqual([
      false,
      false,
      false,
      false,
      true,
    ]);

    const jsonLib = findRow(graph, rows, 'json-lib');
    expect(jsonLib.isLast).toBe(true);
    expect(ancestorLastFlags(jsonLib)).toEqual([false, false, false]);

    const testLib = findRow(graph, rows, 'test-lib');
    expect(ancestorLastFlags(testLib)).toEqual([false, true]);
    expect(testLib.parentId).toBe(
      findRow(graph, rows, 'testRuntimeClasspath').id
    );
  });

  it('gives every occurrence its own identifier', () => {
    const graph = createDependencyGraph();
    const { rows } = generateAll(graph, 'logback');

    expect(new Set(rows.map((row) => row.id)).size).toBe(rows.length);
    expect(findRow(graph, rows, 'logging-api', 0).id).not.toBe(
      findRow(graph, rows, 'logging-api', 1).id
    );
  });

  it('shows duplicate edges and roots separately', () => {
    const graph = createGraph(
      ['root', 'child'],
      [
        [0, 1],
        [0, 1],
      ],
      [0, 0]
    );
    const { rows } = generateAll(graph, 'child');

    expect(describeRows(graph, rows)).toEqual([
      'Gradle::project:1.0 -',
      '  compileClasspath -',
      '    root -',
      '      child',
      '      child',
      '    root -',
      '      child',
      '      child',
    ]);
    expect(new Set(rows.map((row) => row.id)).size).toBe(rows.length);
  });

  it('keeps identifiers when siblings are reordered', () => {
    const occurrenceIds = new OccurrenceIds();
    const graph = createDependencyGraph();
    const reordered: DependencyGraph = {
      ...graph,
      edges: [...graph.edges].reverse(),
      projectGroups: [...graph.projectGroups].reverse(),
    };
    const idsOf = (tree: DependencyGraph) => {
      const context = createContext(tree, 'logback', occurrenceIds);
      const rows = [
        ...generateRows(context, {
          searchTerm: 'logback',
          overrides: new Map(),
        }),
      ];

      return new Map(
        rows.map((row) => [`${row.parentId}/${rowName(tree, row)}`, row.id])
      );
    };

    expect(idsOf(reordered)).toEqual(idsOf(graph));
  });

  it('does not expand a match only because it matches itself', () => {
    const graph = createDependencyGraph();
    const { rows } = generateAll(graph, 'logging-api');

    expect(describeRows(graph, rows)).toContain('      logging-api +');
    expect(describeRows(graph, rows)).toContain('      json-lib');
  });

  it('lets manual changes override the search', () => {
    const graph = createDependencyGraph();
    const occurrenceIds = new OccurrenceIds();
    const { rows: searched } = generateAll(
      graph,
      'logback',
      new Map(),
      occurrenceIds
    );
    const overrides = new Map([
      [findRow(graph, searched, 'http-client').id, false],
      [findRow(graph, searched, 'Gradle::lib:1.0').id, true],
    ]);

    const { rows } = generateAll(graph, 'logback', overrides, occurrenceIds);

    expect(describeRows(graph, rows)).toEqual([
      'Gradle::app:1.0 -',
      '  compileClasspath -',
      '    http-client +',
      '    commons-text',
      '  testRuntimeClasspath -',
      '    test-lib -',
      '      logging-api -',
      '        logback-classic -',
      '          logback-core',
      'Gradle::lib:1.0 -',
      '  runtimeClasspath +',
      'Gradle::docs:1.0',
    ]);
  });

  it('shows a self-dependency as a cycle', () => {
    const graph = createGraph(['self'], [[0, 0]]);
    const { rows } = generateAll(graph, 'self');

    expect(describeRows(graph, rows)).toEqual([
      'Gradle::project:1.0 -',
      '  compileClasspath -',
      '    self -',
      '      self (cycle)',
    ]);
  });

  it('shows a longer cycle once along each path', () => {
    const graph = createGraph(
      ['a', 'b', 'c'],
      [
        [0, 1],
        [1, 2],
        [2, 0],
      ]
    );
    const { rows } = generateAll(graph, 'c');

    expect(describeRows(graph, rows)).toEqual([
      'Gradle::project:1.0 -',
      '  compileClasspath -',
      '    a -',
      '      b -',
      '        c -',
      '          a (cycle)',
    ]);
  });

  it('generates very deep trees without overflowing the call stack', () => {
    const length = 20_000;
    const graph = createGraph(
      Array.from({ length }, (_, index) =>
        index === length - 1 ? 'match' : `node-${index}`
      ),
      Array.from(
        { length: length - 1 },
        (_, index) => [index, index + 1] as [number, number]
      )
    );
    const { rows } = generateAll(graph, 'match');

    expect(rows).toHaveLength(length + 2);
    expect(rows.at(-1)?.depth).toBe(length + 1);
  });

  it('yields the same rows when taken in chunks', () => {
    const graph = createDependencyGraph();
    const { context, expansion, rows } = generateAll(graph, 'logback');
    const generator = generateRows(context, expansion);
    const chunked: DependencyTreeRow[] = [];

    for (
      let chunk = takeRows(generator, 4);
      chunk.length > 0;
      chunk = takeRows(generator, 4)
    ) {
      expect(chunk.length).toBeLessThanOrEqual(4);
      chunked.push(...chunk);
    }

    expect(chunked).toEqual(rows);
  });

  // Graphs with shared dependencies, duplicate edges and roots, and cycles.
  const resumableGraphs = () => [
    { graph: createDependencyGraph(), searchTerm: 'logback' },
    { graph: createSharedDependencyGraph(3, 3), searchTerm: 'leaf' },
    {
      graph: createGraph(
        ['root', 'child', 'grandchild'],
        [
          [0, 1],
          [0, 1],
          [1, 2],
          [2, 0],
        ],
        [0, 0]
      ),
      searchTerm: 'grandchild',
    },
  ];

  it('continues after any row as if the rows had been generated in one go', () => {
    resumableGraphs().forEach(({ graph, searchTerm }) => {
      const { context, expansion, rows } = generateAll(graph, searchTerm);

      rows.forEach((_, index) => {
        expect([
          ...rows.slice(0, index + 1),
          ...generateRowsAfter(context, expansion, rows, index),
        ]).toEqual(rows);
      });
    });
  });

  it('continues after a toggled row with the rows of the new expansion', () => {
    resumableGraphs().forEach(({ graph, searchTerm }) => {
      const { context, expansion, rows } = generateAll(graph, searchTerm);

      rows.forEach((row, index) => {
        if (row.kind === 'cycle' || !row.expandable) return;

        const toggled = setRowExpanded(expansion, row, !row.expanded);
        const kept = [
          ...rows.slice(0, index),
          { ...row, expanded: !row.expanded },
        ];

        expect([
          ...kept,
          ...generateRowsAfter(context, toggled, kept, index),
        ]).toEqual([...generateRows(context, toggled)]);
      });
    });
  });

  it('continues after a row without generating the preceding rows again', () => {
    // 4 ** 12 paths, far too many to enumerate.
    const graph = createSharedDependencyGraph(12, 4);
    const context = createContext(graph, 'leaf');
    const expansion = { searchTerm: 'leaf', overrides: new Map() };
    const rows = takeRows(generateRows(context, expansion), 50_000);
    const matchesNodeSubtree = vi.fn(context.matchesNodeSubtree);

    const following = takeRows(
      generateRowsAfter(
        { ...context, matchesNodeSubtree },
        expansion,
        rows,
        rows.length - 1
      ),
      10
    );

    expect(following).toEqual(
      takeRows(generateRows(context, expansion), 50_010).slice(50_000)
    );
    expect(matchesNodeSubtree.mock.calls.length).toBeLessThan(100);
  });

  it('only does the work for the rows taken from it', () => {
    // 4 ** 12 paths, far too many to enumerate.
    const graph = createSharedDependencyGraph(12, 4);
    const context = createContext(graph, 'leaf');
    const matchesNodeSubtree = vi.fn(context.matchesNodeSubtree);
    const generator = generateRows(
      { ...context, matchesNodeSubtree },
      { searchTerm: 'leaf', overrides: new Map() }
    );

    const rows = takeRows(generator, 100);

    expect(rows).toHaveLength(100);
    expect(matchesNodeSubtree.mock.calls.length).toBeLessThan(1_000);
  });
});

describe('expansion state', () => {
  it('keeps manual changes for an unchanged search term only', () => {
    const graph = createDependencyGraph();
    const { rows } = generateAll(graph, 'logback');
    const expansion = setRowExpanded(
      expansionForSearch(undefined, 'logback'),
      findExpandableRow(graph, rows, 'http-client'),
      false
    );

    expect(expansionForSearch(expansion, 'logback')).toBe(expansion);
    expect(expansionForSearch(expansion, 'json').overrides.size).toBe(0);
    expect(expansionForSearch(expansion, '').overrides.size).toBe(0);
  });

  it('generates the same rows again from the same expansion', () => {
    const graph = createDependencyGraph();
    const { context, rows: searched } = generateAll(graph, 'logback');
    const expansion = setRowExpanded(
      expansionForSearch(undefined, 'logback'),
      findExpandableRow(graph, searched, 'Gradle::lib:1.0'),
      true
    );

    expect([...generateRows(context, expansion)]).toEqual([
      ...generateRows(context, expansion),
    ]);
  });

  it('keeps the state of the shown rows when the defaults change', () => {
    const graph = createDependencyGraph();
    const { context, rows, expansion } = generateAll(graph, 'logback');
    const recorded = recordResolvedExpansion(expansion, rows);
    // Matching PURLs instead of ORT IDs: "Maven:" occurs in no PURL.
    const adjacency = buildAdjacencyMap(graph);
    const purlContext = createDependencyTreeContext(
      graph,
      adjacency,
      createNodeSubtreeMatcher(
        graph,
        adjacency,
        'maven:',
        packageIdTypeSchema.enum.PURL
      ),
      context.occurrenceIds
    );

    expect(
      describeRows(graph, [
        ...generateRows(purlContext, { ...recorded, searchTerm: 'maven:' }),
      ])
    ).toEqual(describeRows(graph, rows));
  });
});

describe('expanding and collapsing rows', () => {
  it('removes the rows below a collapsed row', () => {
    const graph = createDependencyGraph();
    const { rows } = generateAll(graph, 'logback');
    const index = rows.indexOf(findRow(graph, rows, 'http-client'));

    expect(describeRows(graph, collapseRow(rows, index))).toEqual([
      'Gradle::app:1.0 -',
      '  compileClasspath -',
      '    http-client +',
      '    commons-text',
      '  testRuntimeClasspath -',
      '    test-lib -',
      '      logging-api -',
      '        logback-classic -',
      '          logback-core',
      'Gradle::lib:1.0 +',
      'Gradle::docs:1.0',
    ]);
  });

  it('restores the same rows when a collapsed row is expanded again', () => {
    const graph = createDependencyGraph();
    const { context, expansion, rows } = generateAll(graph, 'logback');

    rows.forEach((row, index) => {
      if (row.kind === 'cycle' || !row.expanded) return;

      const collapsed = collapseRow(rows, index);

      expect(expandFully(collapsed, index, context, expansion)).toEqual(rows);
    });
  });

  it('inserts the same rows as generating the expanded tree from scratch', () => {
    const graph = createDependencyGraph();
    const { context, expansion, rows } = generateAll(graph, 'logback');
    const lib = findExpandableRow(graph, rows, 'Gradle::lib:1.0');
    const libExpansion = setRowExpanded(expansion, lib, true);

    expect(expandFully(rows, rows.indexOf(lib), context, libExpansion)).toEqual(
      [...generateRows(context, libExpansion)]
    );
  });

  it('expands a package on a cycle without repeating its ancestors', () => {
    const graph = createGraph(
      ['a', 'b'],
      [
        [0, 1],
        [1, 0],
      ]
    );
    const { context, expansion, rows } = generateAll(graph, 'b');
    const b = findExpandableRow(graph, rows, 'b');
    const index = rows.indexOf(b);

    expect(
      describeRows(
        graph,
        expandFully(
          collapseRow(rows, index),
          index,
          context,
          setRowExpanded(expansion, b, true)
        )
      )
    ).toEqual([
      'Gradle::project:1.0 -',
      '  compileClasspath -',
      '    a -',
      '      b -',
      '        a (cycle)',
    ]);
  });

  it('leaves rows that cannot be expanded unchanged', () => {
    const graph = createDependencyGraph();
    const { context, expansion, rows } = generateAll(graph, 'logback');
    const index = rows.indexOf(findRow(graph, rows, 'json-lib'));
    const expanded = expandRow(rows, index, context, expansion);

    expect(expanded.rows).toEqual(rows);
    expect([...expanded.rowsBelow]).toEqual([]);
  });
});
