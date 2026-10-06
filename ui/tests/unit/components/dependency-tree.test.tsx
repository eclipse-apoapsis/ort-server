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
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { DependencyGraph } from '@/api';
import {
  buildAdjacencyMap,
  createNodeSubtreeMatcher,
} from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/dependencies/-components/dependency-graph-utils';
import { DependencyTree } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/dependencies/-components/dependency-tree';
import { packageIdTypeSchema, type PackageIdType } from '@/schemas';
import {
  createDependencyGraph,
  createSharedDependencyGraph,
  packageLabel,
  packagePurl,
} from '../fixtures/dependency-graph';
import {
  mountedRows,
  ROW_HEIGHT,
  setScrollY,
  setUpDependencyTreeLayout,
} from '../fixtures/dependency-tree-layout';

const runGeneration = () => act(() => vi.runAllTimers());

const treeProps = (
  graph: DependencyGraph,
  searchTerm: string,
  packageIdType: PackageIdType = packageIdTypeSchema.enum.ORT_ID
) => {
  const adjacency = buildAdjacencyMap(graph);

  return {
    adjacency,
    graph,
    matchesNodeSubtree: createNodeSubtreeMatcher(
      graph,
      adjacency,
      searchTerm,
      packageIdType
    ),
    packageIdType,
    searchTerm,
  };
};

const renderTree = (
  graph: DependencyGraph,
  searchTerm: string,
  rowLimit?: number
) => {
  const result = render(
    <DependencyTree {...treeProps(graph, searchTerm)} rowLimit={rowLimit} />
  );

  return {
    ...result,
    list: () =>
      result.container.firstElementChild?.firstElementChild as HTMLElement,
    rerenderTree: (
      newSearchTerm: string,
      packageIdType: PackageIdType = packageIdTypeSchema.enum.ORT_ID
    ) =>
      result.rerender(
        <DependencyTree
          {...treeProps(graph, newSearchTerm, packageIdType)}
          rowLimit={rowLimit}
        />
      ),
  };
};

const toggleButton = (name: string | RegExp) =>
  screen.getByRole('button', { name });

// A graph with 1,366 rows when searching for "leaf".
const largeGraph = () => createSharedDependencyGraph(5, 4);

describe('DependencyTree', () => {
  setUpDependencyTreeLayout();

  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('collapses and expands rows', () => {
    renderTree(createDependencyGraph(), 'logback');

    expect(mountedRows()).toHaveLength(15);

    fireEvent.click(toggleButton(new RegExp(packageLabel('http-client'))));

    expect(mountedRows()).toHaveLength(11);

    fireEvent.click(toggleButton(new RegExp(packageLabel('http-client'))));

    expect(mountedRows()).toHaveLength(15);
  });

  it('keeps manual changes while rows are scrolled out of view and back', () => {
    renderTree(largeGraph(), 'leaf');
    runGeneration();

    fireEvent.click(toggleButton(/layer-0-0/));
    runGeneration();

    setScrollY(20_000);
    setScrollY(0);

    expect(toggleButton(/layer-0-0/)).toHaveAttribute('aria-expanded', 'false');
    expect(toggleButton(/layer-0-1/)).toHaveAttribute('aria-expanded', 'true');
  });

  it('discards manual changes when the search changes', () => {
    const { rerenderTree } = renderTree(createDependencyGraph(), 'logback');

    fireEvent.click(toggleButton(/Gradle::lib:1\.0/));

    expect(screen.getByText('runtimeClasspath')).toBeInTheDocument();

    rerenderTree('logback-core');

    expect(screen.queryByText('runtimeClasspath')).not.toBeInTheDocument();

    rerenderTree('');

    expect(mountedRows()).toHaveLength(3);
  });

  it('keeps the shown rows unchanged when switching to PURLs', () => {
    const { rerenderTree } = renderTree(createDependencyGraph(), 'logback');

    fireEvent.click(toggleButton(new RegExp(packageLabel('http-client'))));
    rerenderTree('logback', packageIdTypeSchema.enum.PURL);

    expect(mountedRows()).toHaveLength(11);
    expect(toggleButton(new RegExp(packagePurl('test-lib')))).toHaveAttribute(
      'aria-expanded',
      'true'
    );
  });

  it('pauses at the row limit until asked to continue', () => {
    const { list } = renderTree(largeGraph(), 'leaf', 1_200);
    runGeneration();

    expect(screen.getByText(/More dependency paths exist/)).toBeInTheDocument();
    expect(list().style.height).toBe(`${1_200 * ROW_HEIGHT}px`);

    fireEvent.click(
      screen.getByRole('button', { name: 'Continue loading dependency paths' })
    );
    runGeneration();

    expect(list().style.height).toBe(`${1_366 * ROW_HEIGHT}px`);
    expect(
      screen.queryByText(/More dependency paths exist/)
    ).not.toBeInTheDocument();
  });

  it('continues after a row toggled while generation is paused', () => {
    const { list } = renderTree(largeGraph(), 'leaf', 1_200);
    runGeneration();

    expect(screen.getByText(/More dependency paths exist/)).toBeInTheDocument();

    // Collapsing the first dependency hides its 340 descendants, so the
    // remaining 1,026 rows fit below the row limit.
    fireEvent.click(toggleButton(/layer-0-0/));
    runGeneration();

    expect(list().style.height).toBe(`${1_026 * ROW_HEIGHT}px`);
    expect(toggleButton(/layer-0-0/)).toHaveAttribute('aria-expanded', 'false');
    expect(
      screen.queryByText(/More dependency paths exist/)
    ).not.toBeInTheDocument();
  });

  it('shows no rows of a search that was replaced before it finished', () => {
    const { list, rerenderTree } = renderTree(largeGraph(), 'leaf');

    // The first 1,000 rows are shown at once, the rest come later.
    expect(screen.getByRole('status')).toHaveTextContent('1,000 rows');

    rerenderTree('');
    runGeneration();

    expect(list().style.height).toBe(`${ROW_HEIGHT}px`);
    expect(mountedRows()).toHaveLength(1);
  });

  it('keeps the focus on a row when it is collapsed with the keyboard', async () => {
    // The small graph needs no timers, which `user-event` would wait for.
    vi.useRealTimers();
    const user = userEvent.setup();
    renderTree(createDependencyGraph(), 'logback');
    const button = toggleButton(new RegExp(packageLabel('http-client')));

    button.focus();
    await user.keyboard('{Enter}');

    expect(button).toHaveAttribute('aria-expanded', 'false');
    expect(button).toHaveFocus();
    expect(mountedRows()).toHaveLength(11);
  });
});
