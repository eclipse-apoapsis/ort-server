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

// @vitest-environment jsdom

import { act, fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import type { DependencyGraph } from '@/api';
import {
  buildAdjacencyMap,
  createNodeSubtreeMatcher,
} from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/dependencies/-components/dependency-graph-utils';
import {
  createDependencyTreeContext,
  generateRows,
} from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/dependencies/-components/dependency-tree-model';
import { VirtualizedDependencyTree } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/dependencies/-components/virtualized-dependency-tree';
import { packageIdTypeSchema } from '@/schemas';
import {
  createDependencyGraph,
  createGraph,
  createSharedDependencyGraph,
  packageLabel,
} from '../fixtures/dependency-graph';
import {
  MAX_MOUNTED_ROWS,
  mountedIndexes,
  mountedRows,
  resizeRow,
  ROW_HEIGHT,
  rowTop,
  setScrollY,
  setUpDependencyTreeLayout,
} from '../fixtures/dependency-tree-layout';

const renderList = (graph: DependencyGraph, searchTerm: string) => {
  const packageIdType = packageIdTypeSchema.enum.ORT_ID;
  const adjacency = buildAdjacencyMap(graph);
  const context = createDependencyTreeContext(
    graph,
    adjacency,
    createNodeSubtreeMatcher(graph, adjacency, searchTerm, packageIdType)
  );
  const rows = [...generateRows(context, { searchTerm, overrides: new Map() })];
  const onToggle = vi.fn();
  const result = render(
    <VirtualizedDependencyTree
      rows={rows}
      graph={graph}
      packageIdType={packageIdType}
      searchTerm={searchTerm}
      onToggle={onToggle}
    />
  );

  return {
    ...result,
    list: result.container.firstElementChild as HTMLElement,
    onToggle,
    rows,
  };
};

const toggleButton = (name: string | RegExp) =>
  screen.getByRole('button', { name });

// A graph with 1,366 rows when searching for "leaf".
const largeGraph = () => createSharedDependencyGraph(5, 4);

describe('VirtualizedDependencyTree', () => {
  setUpDependencyTreeLayout();

  it('shows the rows with labels, badges and highlights', () => {
    renderList(createDependencyGraph(), 'logback');

    expect(mountedRows()).toHaveLength(15);
    expect(toggleButton(/Gradle::app:1\.0/)).toHaveAttribute(
      'aria-expanded',
      'true'
    );
    expect(toggleButton(/Gradle::lib:1\.0/)).toHaveAttribute(
      'aria-expanded',
      'false'
    );
    expect(screen.getAllByText('logback')).toHaveLength(4);
    expect(screen.getByText('7 packages')).toBeInTheDocument();
    expect(screen.getAllByText('DYNAMIC').length).toBeGreaterThan(0);
  });

  it('mounts only the rows near the viewport', () => {
    const { list } = renderList(largeGraph(), 'leaf');

    expect(list.style.height).toBe(`${1_366 * ROW_HEIGHT}px`);
    expect(mountedRows().length).toBeLessThanOrEqual(MAX_MOUNTED_ROWS);
    expect(mountedIndexes()[0]).toBe(0);
  });

  it('shows later rows when scrolling, still mounting only a few', () => {
    renderList(largeGraph(), 'leaf');

    setScrollY(20_000);

    expect(mountedIndexes()[0]).toBeGreaterThan(600);
    expect(mountedRows().length).toBeLessThanOrEqual(MAX_MOUNTED_ROWS);

    setScrollY(40_000);

    expect(mountedIndexes()[0]).toBeGreaterThan(1_200);
  });

  it('moves the following rows when a row becomes higher', () => {
    renderList(createDependencyGraph(), 'logback');

    expect(rowTop('compileClasspath')).toBe(`translateY(${ROW_HEIGHT}px)`);

    resizeRow('Gradle::app:1.0', 3 * ROW_HEIGHT);

    expect(rowTop('compileClasspath')).toBe(`translateY(${3 * ROW_HEIGHT}px)`);
  });

  it('draws the connector lines of the row and its ancestors', () => {
    renderList(createDependencyGraph(), 'logback');

    const row = mountedRows().find((element) =>
      element.textContent.includes(packageLabel('logback-core'))
    );
    const lines = Array.from(
      row?.querySelectorAll<HTMLElement>('span[aria-hidden]') ?? []
    ).map((line) => line.style.left);

    // Lines continue for the scope, `http-client` and `logging-api`, which
    // all have later siblings; `logback-classic` has none. The row itself is
    // the last below its parent and draws its own two lines.
    expect(lines).toEqual(['8px', '32px', '56px', '104px', '104px']);
    expect(row?.style.paddingLeft).toBe('120px');
  });

  it('marks a dependency on itself as a cycle', () => {
    renderList(createGraph(['self'], [[0, 0]]), 'self');

    expect(screen.getByText('cycle')).toBeInTheDocument();
  });

  it('passes a clicked row to the toggle handler', () => {
    const { onToggle, rows } = renderList(createDependencyGraph(), 'logback');

    fireEvent.click(toggleButton(new RegExp(packageLabel('http-client'))));

    expect(onToggle).toHaveBeenCalledExactlyOnceWith(rows[2]);
  });

  it('toggles rows with the keyboard', async () => {
    const user = userEvent.setup();
    const { onToggle, rows } = renderList(createDependencyGraph(), 'logback');

    toggleButton(/Gradle::lib:1\.0/).focus();
    await user.keyboard('{Enter}');

    expect(onToggle).toHaveBeenCalledExactlyOnceWith(rows[13]);
  });

  it('keeps the focused row mounted while it is scrolled out of view', () => {
    renderList(largeGraph(), 'leaf');
    const button = toggleButton(/layer-0-0/);

    act(() => button.focus());
    setScrollY(20_000);

    expect(button).toBeInTheDocument();
    expect(button).toHaveFocus();
    // The project, the scope and then the focused row.
    expect(mountedIndexes()[0]).toBe(2);
    expect(mountedIndexes()[1]).toBeGreaterThan(600);
  });

  it('is not compiled by React Compiler', () => {
    const source = VirtualizedDependencyTree.toString();

    expect(source).toContain('use no memo');
    expect(source).not.toMatch(/const \$ = .*\.c\)\(\d+\);/);
  });
});
