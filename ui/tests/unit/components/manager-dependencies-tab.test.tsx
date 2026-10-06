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
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { DependencyGraph } from '@/api';
import { Tabs } from '@/components/ui/tabs';
import {
  buildAdjacencyMap,
  createNodeSubtreeMatcher,
} from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/dependencies/-components/dependency-graph-utils';
import { DependencyTreeRow } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/dependencies/-components/dependency-tree-row';
import { ManagerDependenciesTab } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/dependencies/-components/manager-dependencies-tab';
import { packageIdTypeSchema } from '@/schemas';
import { useUserSettingsStore } from '@/store/user-settings.store';
import {
  createDependencyGraph,
  packageLabel,
} from '../fixtures/dependency-graph';

vi.mock(
  '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/dependencies/-components/dependency-graph-utils',
  async (importOriginal) => {
    const actual =
      await importOriginal<
        typeof import('@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/dependencies/-components/dependency-graph-utils')
      >();

    return {
      ...actual,
      buildAdjacencyMap: vi.fn(actual.buildAdjacencyMap),
      createNodeSubtreeMatcher: vi.fn(actual.createNodeSubtreeMatcher),
    };
  }
);

// Counts how often the rows of the tree are rendered.
vi.mock(
  '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/dependencies/-components/dependency-tree-row',
  async (importOriginal) => {
    const actual =
      await importOriginal<
        typeof import('@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/dependencies/-components/dependency-tree-row')
      >();

    return { DependencyTreeRow: vi.fn(actual.DependencyTreeRow) };
  }
);

const SEARCH_DELAY_MS = 500;

const renderTab = (graph: DependencyGraph) => {
  const ui = (tabGraph: DependencyGraph) => (
    <Tabs defaultValue='Gradle'>
      <ManagerDependenciesTab graph={tabGraph} managerName='Gradle' />
    </Tabs>
  );
  const result = render(ui(graph));

  return {
    ...result,
    rerenderWithGraph: (newGraph: DependencyGraph) =>
      result.rerender(ui(newGraph)),
  };
};

const typeSearch = (value: string) =>
  fireEvent.change(
    screen.getByPlaceholderText('Search package ID or PURL...'),
    {
      target: { value },
    }
  );

const waitForSearch = (delay = SEARCH_DELAY_MS) =>
  act(() => vi.advanceTimersByTime(delay));

const search = (value: string) => {
  typeSearch(value);
  waitForSearch();
};

// Labels that match the search are split into several elements by the
// highlighter, so look for them in the text of the whole document.
const isShown = (text: string) => document.body.textContent.includes(text);

const noMatchesMessage =
  'No packages match your search. Showing the full graph.';

describe('ManagerDependenciesTab', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.clearAllMocks();
    useUserSettingsStore.setState({
      packageIdType: packageIdTypeSchema.enum.ORT_ID,
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('shows all projects collapsed without a search', () => {
    renderTab(createDependencyGraph());

    expect(isShown('Gradle::app:1.0')).toBe(true);
    expect(isShown('Gradle::lib:1.0')).toBe(true);
    expect(isShown('Gradle::docs:1.0')).toBe(true);
    expect(isShown('compileClasspath')).toBe(false);
  });

  it('expands the paths to the matches only after the search delay', () => {
    renderTab(createDependencyGraph());

    typeSearch('logback');
    waitForSearch(SEARCH_DELAY_MS - 1);

    expect(isShown(packageLabel('http-client'))).toBe(false);

    waitForSearch(1);

    expect(isShown('compileClasspath')).toBe(true);
    expect(isShown('testRuntimeClasspath')).toBe(true);
    expect(isShown(packageLabel('logback-classic'))).toBe(true);
    expect(isShown(packageLabel('logback-core'))).toBe(true);
  });

  it('keeps unrelated siblings and collapses branches without matches', () => {
    renderTab(createDependencyGraph());

    search('logback');

    // Siblings of the path to the matches remain visible.
    expect(isShown(packageLabel('json-lib'))).toBe(true);
    expect(isShown(packageLabel('commons-text'))).toBe(true);
    // A project without matches stays collapsed.
    expect(isShown('runtimeClasspath')).toBe(false);
    expect(screen.queryByText(noMatchesMessage)).not.toBeInTheDocument();
  });

  it('does not render the tree again while typing before the search delay', () => {
    renderTab(createDependencyGraph());
    search('logback');

    const rowRenders = vi.mocked(DependencyTreeRow).mock.calls.length;
    const matcherCreations = vi.mocked(createNodeSubtreeMatcher).mock.calls
      .length;

    expect(rowRenders).toBeGreaterThan(0);

    typeSearch('logbackx');
    typeSearch('logbackxy');

    expect(vi.mocked(DependencyTreeRow).mock.calls).toHaveLength(rowRenders);
    expect(vi.mocked(createNodeSubtreeMatcher).mock.calls).toHaveLength(
      matcherCreations
    );

    waitForSearch();

    expect(screen.getByText(noMatchesMessage)).toBeInTheDocument();
  });

  it('prepares the graph again only when the graph changes', () => {
    const graph = createDependencyGraph();
    const { rerenderWithGraph } = renderTab(graph);

    search('logback');
    rerenderWithGraph(graph);

    expect(buildAdjacencyMap).toHaveBeenCalledTimes(1);

    rerenderWithGraph(createDependencyGraph());

    expect(buildAdjacencyMap).toHaveBeenCalledTimes(2);
  });

  it('creates the matcher again only when its inputs change', () => {
    const graph = createDependencyGraph();
    const { rerenderWithGraph } = renderTab(graph);

    expect(createNodeSubtreeMatcher).toHaveBeenCalledTimes(1);

    search('logback');

    expect(createNodeSubtreeMatcher).toHaveBeenCalledTimes(2);

    // The normalized search term stays the same.
    search('  LogBack ');
    rerenderWithGraph(graph);

    expect(createNodeSubtreeMatcher).toHaveBeenCalledTimes(2);

    act(() =>
      useUserSettingsStore.setState({
        packageIdType: packageIdTypeSchema.enum.PURL,
      })
    );

    expect(createNodeSubtreeMatcher).toHaveBeenCalledTimes(3);

    rerenderWithGraph(createDependencyGraph());

    expect(createNodeSubtreeMatcher).toHaveBeenCalledTimes(4);
  });

  it('ignores surrounding whitespace and case in the search', () => {
    renderTab(createDependencyGraph());

    search('  LOGBACK-Classic ');

    expect(isShown(packageLabel('logback-classic'))).toBe(true);
    expect(screen.queryByText(noMatchesMessage)).not.toBeInTheDocument();
  });

  it('collapses the tree again when the search is cleared', () => {
    renderTab(createDependencyGraph());
    search('logback');

    fireEvent.click(screen.getByRole('button', { name: 'Clear search' }));
    waitForSearch();

    expect(isShown('compileClasspath')).toBe(false);
    expect(isShown('Gradle::app:1.0')).toBe(true);
  });

  it('shows the full graph with a message when nothing matches', () => {
    renderTab(createDependencyGraph());

    search('nothing-matches-this');

    expect(screen.getByText(noMatchesMessage)).toBeInTheDocument();
    expect(isShown('Gradle::app:1.0')).toBe(true);
    expect(isShown('Gradle::lib:1.0')).toBe(true);
  });

  it('searches PURLs only when PURLs are the preferred package ID', () => {
    renderTab(createDependencyGraph());

    search('pkg:maven/com.example/logback');

    expect(screen.getByText(noMatchesMessage)).toBeInTheDocument();

    act(() =>
      useUserSettingsStore.setState({
        packageIdType: packageIdTypeSchema.enum.PURL,
      })
    );

    expect(screen.queryByText(noMatchesMessage)).not.toBeInTheDocument();
  });

  it('lets projects be toggled by hand after a search', () => {
    renderTab(createDependencyGraph());
    search('logback');

    fireEvent.click(screen.getByRole('button', { name: /Gradle::lib:1.0/ }));

    expect(isShown('runtimeClasspath')).toBe(true);

    fireEvent.click(screen.getByRole('button', { name: /Gradle::app:1.0/ }));

    expect(isShown('compileClasspath')).toBe(false);
  });
});
