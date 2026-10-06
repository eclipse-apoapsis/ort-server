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

import { render, screen } from '@testing-library/react';
import type { ComponentProps } from 'react';
import { describe, expect, it, vi } from 'vitest';

import {
  buildAdjacencyMap,
  createNodeSubtreeMatcher,
} from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/dependencies/-components/dependency-graph-utils';
import {
  createDependencyTreeContext,
  generateRows,
  type ExpandableRow,
} from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/dependencies/-components/dependency-tree-model';
import { DependencyTreeRow } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/dependencies/-components/dependency-tree-row';
import { HighlightedMatch } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/dependencies/-components/highlighted-match';
import { PackageCountBadge } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/dependencies/-components/package-count-badge';
import { packageIdTypeSchema } from '@/schemas';
import {
  createDependencyGraph,
  packageLabel,
  packagePurl,
} from '../fixtures/dependency-graph';
import { isCompiledByReactCompiler } from '../fixtures/react-compiler';

const graph = createDependencyGraph();

const rowsFor = (searchTerm: string) => {
  const adjacency = buildAdjacencyMap(graph);
  const context = createDependencyTreeContext(
    graph,
    adjacency,
    createNodeSubtreeMatcher(
      graph,
      adjacency,
      searchTerm,
      packageIdTypeSchema.enum.ORT_ID
    )
  );

  return [...generateRows(context, { searchTerm, overrides: new Map() })];
};

const rows = rowsFor('logback');
// `http-client`, expanded because `logback` packages are below it.
const httpClient = rows[2] as ExpandableRow;

const props = (
  overrides: Partial<ComponentProps<typeof DependencyTreeRow>> = {}
): ComponentProps<typeof DependencyTreeRow> => ({
  row: httpClient,
  index: 2,
  top: 64,
  graph,
  packageIdType: packageIdTypeSchema.enum.ORT_ID,
  searchTerm: 'logback',
  measureRef: vi.fn(),
  onToggle: vi.fn(),
  ...overrides,
});

const rowElement = (container: HTMLElement) =>
  container.querySelector<HTMLElement>('[data-dependency-tree-row]');

describe('DependencyTreeRow', () => {
  it('is compiled by React Compiler, as are the components it uses', () => {
    expect(isCompiledByReactCompiler(DependencyTreeRow)).toBe(true);
    expect(isCompiledByReactCompiler(HighlightedMatch)).toBe(true);
    expect(isCompiledByReactCompiler(PackageCountBadge)).toBe(true);
  });

  it('shows the changed position and state of a row', () => {
    const { container, rerender } = render(<DependencyTreeRow {...props()} />);

    expect(rowElement(container)?.style.transform).toBe('translateY(64px)');
    expect(screen.getByRole('button')).toHaveAttribute('aria-expanded', 'true');

    rerender(
      <DependencyTreeRow
        {...props({ row: { ...httpClient, expanded: false }, top: 96 })}
      />
    );

    expect(rowElement(container)?.style.transform).toBe('translateY(96px)');
    expect(screen.getByRole('button')).toHaveAttribute(
      'aria-expanded',
      'false'
    );
  });

  it('shows a changed label and highlight', () => {
    const { rerender } = render(<DependencyTreeRow {...props()} />);

    expect(screen.getByText(packageLabel('http-client'))).toBeInTheDocument();

    rerender(
      <DependencyTreeRow
        {...props({ packageIdType: packageIdTypeSchema.enum.PURL })}
      />
    );

    expect(screen.getByText(packagePurl('http-client'))).toBeInTheDocument();

    rerender(<DependencyTreeRow {...props({ searchTerm: 'http' })} />);

    expect(screen.getByText('http')).toHaveClass('font-bold');
  });

  it('passes the current row to the toggle handler', () => {
    const onToggle = vi.fn();
    const collapsed = { ...httpClient, expanded: false };
    const { rerender } = render(<DependencyTreeRow {...props({ onToggle })} />);

    rerender(<DependencyTreeRow {...props({ onToggle, row: collapsed })} />);
    screen.getByRole('button').click();

    expect(onToggle).toHaveBeenCalledExactlyOnceWith(collapsed);
  });
});
