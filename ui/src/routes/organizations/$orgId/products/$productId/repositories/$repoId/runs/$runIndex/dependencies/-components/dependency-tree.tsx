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

import {
  memo,
  useEffect,
  useLayoutEffect,
  useState,
  useSyncExternalStore,
} from 'react';

import type { DependencyGraph } from '@/api';
import { Button } from '@/components/ui/button';
import type { PackageIdType } from '@/schemas';
import type { AdjacencyMap } from './dependency-graph-utils';
import {
  CHUNK_ROW_LIMIT,
  ROW_LIMIT,
  startRowGeneration,
  type RowGeneration,
  type RowGenerationStatus,
} from './dependency-tree-generation';
import {
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
} from './dependency-tree-model';
import { VirtualizedDependencyTree } from './virtualized-dependency-tree';

type TreeSnapshot = {
  readonly rows: readonly DependencyTreeRow[];
  readonly status: Exclude<RowGenerationStatus, 'cancelled'>;
};

/** Places a chunk of generated rows into the current rows. */
type PlaceRows = (
  rows: readonly DependencyTreeRow[],
  chunk: readonly DependencyTreeRow[],
  isFirstChunk: boolean
) => readonly DependencyTreeRow[];

const appendRows: PlaceRows = (rows, chunk) => [...rows, ...chunk];

/**
 * Holds the rows of a dependency tree, which rows are expanded, and the
 * generation of rows that is in progress. Every change publishes a new
 * snapshot for React to render.
 */
class DependencyTreeRows {
  private snapshot: TreeSnapshot = { rows: [], status: 'running' };
  private readonly listeners = new Set<() => void>();
  private context: DependencyTreeContext | undefined;
  private expansion: TreeExpansion | undefined;
  private packageIdType: PackageIdType | undefined;
  private generation: RowGeneration | undefined;

  constructor(private readonly rowLimit: number) {}

  subscribe = (listener: () => void) => {
    this.listeners.add(listener);

    return () => {
      this.listeners.delete(listener);
    };
  };

  getSnapshot = () => this.snapshot;

  /** Shows the rows for a changed graph, search or package ID setting. */
  update(
    context: DependencyTreeContext,
    searchTerm: string,
    packageIdType: PackageIdType
  ) {
    if (context === this.context) return;

    let expansion = expansionForSearch(this.expansion, searchTerm);

    // Keep the state of the rows shown so far when only the labels the search
    // looks at change.
    if (expansion === this.expansion && packageIdType !== this.packageIdType) {
      expansion = recordResolvedExpansion(expansion, this.snapshot.rows);
    }

    this.context = context;
    this.expansion = expansion;
    this.packageIdType = packageIdType;

    // Keep showing the current rows until the first new ones are generated.
    this.generate(generateRows(context, expansion), (rows, chunk, isFirst) =>
      isFirst ? chunk : [...rows, ...chunk]
    );
  }

  toggle = (row: ExpandableRow) => {
    const { context, expansion } = this;
    const { rows } = this.snapshot;
    const index = rows.findIndex(({ id }) => id === row.id);
    const current = rows[index];

    if (
      context === undefined ||
      expansion === undefined ||
      current === undefined ||
      current.kind === 'cycle' ||
      !current.expandable
    ) {
      return;
    }

    const expanded = !current.expanded;
    this.expansion = setRowExpanded(expansion, current, expanded);

    if (
      this.generation !== undefined &&
      this.generation.status !== 'complete'
    ) {
      // Rows after the toggled one are still being generated, so generate them
      // again, continuing from the toggled row. The rows before it do not
      // change.
      this.generation.cancel();
      const kept = [...rows.slice(0, index), { ...current, expanded }];
      this.publish(kept);
      this.generate(
        generateRowsAfter(context, this.expansion, kept, index),
        appendRows
      );

      return;
    }

    if (!expanded) {
      this.publish(collapseRow(rows, index));

      return;
    }

    const { rows: expandedRows, rowsBelow } = expandRow(
      rows,
      index,
      context,
      this.expansion
    );
    const firstRows = takeRows(rowsBelow, CHUNK_ROW_LIMIT);
    let insertAt = index + 1;

    this.publish(insertRows(expandedRows, insertAt, firstRows));
    insertAt += firstRows.length;

    if (firstRows.length === CHUNK_ROW_LIMIT) {
      this.generate(rowsBelow, (current, chunk) => {
        const inserted = insertRows(current, insertAt, chunk);
        insertAt += chunk.length;

        return inserted;
      });
    }
  };

  /** Continues a generation that paused at the row limit. */
  resume = () => {
    this.generation?.resume();
  };

  /** Stops generating rows; `update` starts again from scratch. */
  dispose() {
    this.generation?.cancel();
    this.generation = undefined;
    this.context = undefined;
  }

  private publish(
    rows: readonly DependencyTreeRow[],
    status: TreeSnapshot['status'] = this.snapshot.status
  ) {
    this.snapshot = { rows, status };
    this.listeners.forEach((listener) => listener());
  }

  private generate(rows: Iterator<DependencyTreeRow>, placeRows: PlaceRows) {
    this.generation?.cancel();

    let isFirstChunk = true;
    const place = (chunk: readonly DependencyTreeRow[]) => {
      const placed = placeRows(this.snapshot.rows, chunk, isFirstChunk);
      isFirstChunk = false;

      return placed;
    };

    this.publish(this.snapshot.rows, 'running');
    this.generation = startRowGeneration(rows, {
      onRows: (chunk) => this.publish(place(chunk)),
      onStatusChange: (status) => {
        if (status === 'cancelled') return;

        this.publish(isFirstChunk ? place([]) : this.snapshot.rows, status);
      },
      rowLimit: this.rowLimit,
      firstChunkImmediately: true,
    });
  }
}

type DependencyTreeProps = {
  adjacency: AdjacencyMap;
  graph: DependencyGraph;
  matchesNodeSubtree: (nodeIndex: number) => boolean;
  packageIdType: PackageIdType;
  searchTerm: string;
  /** The number of rows after which generating rows pauses. */
  rowLimit?: number;
};

/**
 * Renders the projects, scopes and dependencies of a graph as a tree. It is
 * memoized so that typing into the search field, which only changes the search
 * term after a delay, does not render the tree again on every keystroke.
 */
export const DependencyTree = memo(function DependencyTree({
  adjacency,
  graph,
  matchesNodeSubtree,
  packageIdType,
  searchTerm,
  rowLimit = ROW_LIMIT,
}: DependencyTreeProps) {
  'use memo';

  // Manual expansion changes are stored by occurrence identifier, so keep the
  // identifiers for as long as this tree exists, also across graph changes.
  const [occurrenceIds] = useState(() => new OccurrenceIds());
  const [treeRows] = useState(() => new DependencyTreeRows(rowLimit));
  const context = createDependencyTreeContext(
    graph,
    adjacency,
    matchesNodeSubtree,
    occurrenceIds
  );

  // Generate the first rows before the browser draws the page, so that it does
  // not show an empty tree in between.
  useLayoutEffect(() => {
    treeRows.update(context, searchTerm, packageIdType);
  }, [treeRows, context, searchTerm, packageIdType]);

  useEffect(() => () => treeRows.dispose(), [treeRows]);

  const { rows, status } = useSyncExternalStore(
    treeRows.subscribe,
    treeRows.getSnapshot
  );

  if (graph.projectGroups.length === 0) {
    return (
      <div className='text-muted-foreground text-sm'>
        No scopes are available for this dependency graph.
      </div>
    );
  }

  return (
    <div className='space-y-2'>
      <VirtualizedDependencyTree
        rows={rows}
        graph={graph}
        packageIdType={packageIdType}
        searchTerm={searchTerm}
        onToggle={treeRows.toggle}
      />

      {status === 'running' && rows.length >= CHUNK_ROW_LIMIT && (
        <div className='text-muted-foreground text-sm' role='status'>
          Loading dependency paths… {rows.length.toLocaleString()} rows so far.
        </div>
      )}

      {status === 'paused' && (
        <div className='flex flex-wrap items-center gap-2'>
          <span className='text-muted-foreground text-sm' role='status'>
            Showing {rows.length.toLocaleString()} rows. More dependency paths
            exist.
          </span>
          <Button
            type='button'
            variant='secondary'
            size='sm'
            onClick={treeRows.resume}
          >
            Continue loading dependency paths
          </Button>
        </div>
      )}
    </div>
  );
});
