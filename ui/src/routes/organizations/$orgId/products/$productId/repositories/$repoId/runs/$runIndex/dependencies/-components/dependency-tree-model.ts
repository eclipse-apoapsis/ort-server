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

import type { DependencyGraph } from '@/api';
import {
  scopeHasSearchMatch,
  type AdjacencyMap,
} from './dependency-graph-utils';

/**
 * The last-sibling flags of a row's ancestors, from its parent up to the root.
 * Rows below the same parent share one link, so deep trees do not copy the
 * flags of all ancestors into every row.
 */
export type AncestorLink = {
  readonly isLast: boolean;
  readonly parent: AncestorLink | null;
};

/** The graph nodes on the path to a package row, from the row itself upwards. */
export type PathLink = {
  readonly nodeIndex: number;
  readonly parent: PathLink | null;
};

type RowBase = {
  /** Identifies this occurrence independently of the order of its siblings. */
  readonly id: string;
  readonly parentId: string | null;
  readonly depth: number;
  /** The position of the row among the children of its parent. */
  readonly position: number;
  readonly isLast: boolean;
  readonly ancestors: AncestorLink | null;
};

export type ProjectRow = RowBase & {
  readonly kind: 'project';
  readonly projectIndex: number;
  readonly packageCount: number | null;
  readonly expandable: boolean;
  readonly expanded: boolean;
};

export type ScopeRow = RowBase & {
  readonly kind: 'scope';
  readonly projectIndex: number;
  readonly scopeIndex: number;
  readonly packageCount: number | null;
  readonly expandable: boolean;
  readonly expanded: boolean;
};

export type PackageRow = RowBase & {
  readonly kind: 'package';
  readonly nodeIndex: number;
  readonly packageIndex: number;
  readonly linkage: string;
  readonly packageCount: number;
  readonly path: PathLink;
  readonly expandable: boolean;
  readonly expanded: boolean;
};

/** A dependency that already occurs on the path to it, so it is not expanded. */
export type CycleRow = RowBase & {
  readonly kind: 'cycle';
  readonly nodeIndex: number;
  readonly packageIndex: number;
};

export type ExpandableRow = ProjectRow | ScopeRow | PackageRow;

export type DependencyTreeRow = ExpandableRow | CycleRow;

/**
 * Assigns short identifiers to occurrences. An occurrence is described by the
 * identifier of its parent and a segment naming the project, scope or node and
 * how many siblings with the same name precede it. The same description always
 * gets the same identifier, so identifiers do not depend on the order of
 * siblings or on the order in which occurrences are discovered, while their
 * length does not grow with the depth of the tree.
 *
 * Identifiers are only comparable when they come from the same instance, so
 * keep one instance for as long as manual expansion changes are kept.
 */
export class OccurrenceIds {
  private readonly ids = new Map<string, string>();

  child(parentId: string | null, segment: string): string {
    const key = `${parentId ?? ''}/${segment}`;
    let id = this.ids.get(key);

    if (id === undefined) {
      id = `o${this.ids.size}`;
      this.ids.set(key, id);
    }

    return id;
  }
}

export type DependencyTreeContext = {
  readonly graph: DependencyGraph;
  readonly matchesNodeSubtree: (nodeIndex: number) => boolean;
  readonly occurrenceIds: OccurrenceIds;
  /** The children of a node that exist in the graph, in the order of the edges. */
  readonly childNodeIndexes: (nodeIndex: number) => readonly number[];
};

export const createDependencyTreeContext = (
  graph: DependencyGraph,
  adjacency: AdjacencyMap,
  matchesNodeSubtree: (nodeIndex: number) => boolean,
  occurrenceIds: OccurrenceIds = new OccurrenceIds()
): DependencyTreeContext => {
  const childNodeIndexCache = new Map<number, readonly number[]>();

  return {
    graph,
    matchesNodeSubtree,
    occurrenceIds,
    childNodeIndexes: (nodeIndex) => {
      let children = childNodeIndexCache.get(nodeIndex);

      if (children === undefined) {
        children = (adjacency.get(nodeIndex) ?? []).filter((childIndex) =>
          isRenderableNode(graph, childIndex)
        );
        childNodeIndexCache.set(nodeIndex, children);
      }

      return children;
    },
  };
};

const isRenderableNode = (graph: DependencyGraph, nodeIndex: number) => {
  const node = graph.nodes[nodeIndex];

  return node !== undefined && graph.packages[node.pkg] !== undefined;
};

/**
 * Which occurrences are expanded: by default those on the path to a search
 * match, unless the user expanded or collapsed an occurrence by hand.
 */
export type TreeExpansion = {
  readonly searchTerm: string;
  readonly overrides: ReadonlyMap<string, boolean>;
};

/**
 * Returns the expansion for the given normalized search term. A changed search
 * term discards all manual changes, so that the paths to the new matches are
 * expanded; an unchanged one keeps them.
 */
export const expansionForSearch = (
  current: TreeExpansion | undefined,
  searchTerm: string
): TreeExpansion =>
  current?.searchTerm === searchTerm
    ? current
    : { searchTerm, overrides: new Map() };

export const setRowExpanded = (
  expansion: TreeExpansion,
  row: ExpandableRow,
  expanded: boolean
): TreeExpansion => ({
  ...expansion,
  overrides: new Map(expansion.overrides).set(row.id, expanded),
});

/**
 * Records the current state of all given rows as manual changes. Call this
 * before the defaults change for another reason than a new search term, such as
 * matching against PURLs instead of ORT IDs, so that the rows already shown
 * keep their state. Rows that are discovered later get the new defaults.
 */
export const recordResolvedExpansion = (
  expansion: TreeExpansion,
  rows: readonly DependencyTreeRow[]
): TreeExpansion => {
  const overrides = new Map(expansion.overrides);

  rows.forEach((row) => {
    if (row.kind !== 'cycle' && row.expandable) {
      overrides.set(row.id, row.expanded);
    }
  });

  return { ...expansion, overrides };
};

/** Returns the last-sibling flags of the row's ancestors, from the root down. */
export const ancestorLastFlags = (row: DependencyTreeRow): boolean[] => {
  const flags: boolean[] = [];

  for (let link = row.ancestors; link; link = link.parent) {
    flags.push(link.isLast);
  }

  return flags.reverse();
};

type Frame = {
  readonly row: ExpandableRow | null;
  readonly childCount: number;
  readonly childNodeIndexes: readonly number[];
  readonly childAncestors: AncestorLink | null;
  readonly sameNameSiblings: Map<string, number>;
  nextChild: number;
};

/** Where a traversal starts. */
type TraversalStart =
  | { readonly kind: 'root' }
  | { readonly kind: 'below'; readonly parent: ExpandableRow }
  | {
      readonly kind: 'after';
      readonly rows: readonly DependencyTreeRow[];
      readonly index: number;
    };

/**
 * Generates the rows of the tree in the order they are shown, one at a time.
 * Without `parent`, the generator yields every visible row of the graph;
 * with `parent`, it yields the visible rows below that row, treating the row
 * itself as expanded.
 *
 * The traversal keeps its own stack instead of recursing, so deep graphs cannot
 * overflow the call stack, and it only does work for the rows that are actually
 * taken from it. A node that already occurs on the path to it is shown as a
 * cycle and not expanded further; a node that is reachable along several paths
 * is shown below each of them.
 */
export const generateRows = (
  context: DependencyTreeContext,
  expansion: TreeExpansion,
  parent?: ExpandableRow
): Generator<DependencyTreeRow, void, undefined> =>
  traverse(
    context,
    expansion,
    parent ? { kind: 'below', parent } : { kind: 'root' }
  );

/**
 * Generates the rows that follow the row at `index`, as `generateRows` would
 * after yielding it. The rows up to and including `index` must be the first
 * rows `generateRows` yields for the same expansion; the row at `index` may
 * have just been expanded or collapsed.
 *
 * The traversal continues from the ancestors of the row instead of generating
 * the preceding rows again, so its cost does not grow with `index`.
 */
export const generateRowsAfter = (
  context: DependencyTreeContext,
  expansion: TreeExpansion,
  rows: readonly DependencyTreeRow[],
  index: number
): Generator<DependencyTreeRow, void, undefined> =>
  traverse(context, expansion, { kind: 'after', rows, index });

function* traverse(
  context: DependencyTreeContext,
  expansion: TreeExpansion,
  start: TraversalStart
): Generator<DependencyTreeRow, void, undefined> {
  const { graph, occurrenceIds } = context;
  const { searchTerm, overrides } = expansion;
  const nodesOnPath = new Map<number, number>();

  const addToPath = (nodeIndex: number, delta: number) => {
    const count = (nodesOnPath.get(nodeIndex) ?? 0) + delta;

    if (count > 0) {
      nodesOnPath.set(nodeIndex, count);
    } else {
      nodesOnPath.delete(nodeIndex);
    }
  };

  const isExpanded = (id: string, isOpenByDefault: () => boolean) =>
    overrides.get(id) ?? (searchTerm.length > 0 && isOpenByDefault());

  const childNodeIndexesOf = (row: ScopeRow | PackageRow) =>
    row.kind === 'scope'
      ? (graph.projectGroups[row.projectIndex]?.scopes[
          row.scopeIndex
        ]?.rootNodeIndexes.filter((nodeIndex) =>
          isRenderableNode(graph, nodeIndex)
        ) ?? [])
      : context.childNodeIndexes(row.nodeIndex);

  const createFrame = (row: ExpandableRow | null): Frame => {
    const childNodeIndexes =
      row === null || row.kind === 'project' ? [] : childNodeIndexesOf(row);
    const childCount =
      row === null
        ? graph.projectGroups.length
        : row.kind === 'project'
          ? (graph.projectGroups[row.projectIndex]?.scopes.length ?? 0)
          : childNodeIndexes.length;

    return {
      row,
      childCount,
      childNodeIndexes,
      childAncestors: row
        ? { isLast: row.isLast, parent: row.ancestors }
        : null,
      sameNameSiblings: new Map(),
      nextChild: 0,
    };
  };

  /** Names a child; siblings with the same name are told apart by ordinals. */
  const childName = (frame: Frame, position: number) => {
    const { row } = frame;

    if (row === null) {
      return `p:${graph.projectGroups[position]?.projectLabel ?? ''}`;
    }

    if (row.kind === 'project') {
      return `s:${graph.projectGroups[row.projectIndex]?.scopes[position]?.scopeName ?? ''}`;
    }

    return `n:${frame.childNodeIndexes[position]}`;
  };

  const createChild = (frame: Frame, position: number): DependencyTreeRow => {
    const { row: parentRow } = frame;
    const parentId = parentRow?.id ?? null;
    const isLast = position === frame.childCount - 1;
    const base = {
      parentId,
      depth: parentRow ? parentRow.depth + 1 : 0,
      position,
      isLast,
      ancestors: frame.childAncestors,
    };

    const name = childName(frame, position);
    const ordinal = frame.sameNameSiblings.get(name) ?? 0;
    frame.sameNameSiblings.set(name, ordinal + 1);
    const id = occurrenceIds.child(parentId, `${name}#${ordinal}`);

    if (parentRow === null) {
      const project = graph.projectGroups[position];
      const scopes = project?.scopes ?? [];
      const expandable = scopes.length > 0;

      return {
        ...base,
        kind: 'project',
        id,
        projectIndex: position,
        packageCount: project?.packageCount ?? null,
        expandable,
        expanded:
          expandable &&
          isExpanded(id, () =>
            scopes.some((scope) =>
              scopeHasSearchMatch(scope, searchTerm, context.matchesNodeSubtree)
            )
          ),
      };
    }

    if (parentRow.kind === 'project') {
      const scope =
        graph.projectGroups[parentRow.projectIndex]?.scopes[position];

      return {
        ...base,
        kind: 'scope',
        id,
        projectIndex: parentRow.projectIndex,
        scopeIndex: position,
        packageCount: scope?.packageCount ?? null,
        expandable: true,
        expanded: isExpanded(
          id,
          () => scope?.rootNodeIndexes.some(context.matchesNodeSubtree) ?? false
        ),
      };
    }

    const nodeIndex = frame.childNodeIndexes[position]!;
    // Only renderable nodes become children, so the node exists.
    const node = graph.nodes[nodeIndex]!;

    if (nodesOnPath.has(nodeIndex)) {
      return { ...base, kind: 'cycle', id, nodeIndex, packageIndex: node.pkg };
    }

    const children = context.childNodeIndexes(nodeIndex);
    const expandable = children.length > 0;

    return {
      ...base,
      kind: 'package',
      id,
      nodeIndex,
      packageIndex: node.pkg,
      linkage: node.linkage,
      packageCount: node.packageCount,
      path: {
        nodeIndex,
        parent: parentRow.kind === 'package' ? parentRow.path : null,
      },
      expandable,
      expanded:
        expandable &&
        isExpanded(id, () => children.some(context.matchesNodeSubtree)),
    };
  };

  const stack: Frame[] = [];

  const pushFrame = (row: ExpandableRow | null) => {
    stack.push(createFrame(row));
    if (row?.kind === 'package') addToPath(row.nodeIndex, 1);
  };

  /**
   * Restores the stack as it was right after the traversal yielded the row at
   * `index`: one frame for each ancestor, positioned after the child on the
   * path to the row, and one for the row itself if it is expanded.
   */
  const resumeAfter = (rows: readonly DependencyTreeRow[], index: number) => {
    const row = rows[index];
    if (row === undefined) return;

    // The ancestors are the closest preceding rows with a smaller depth.
    const path: DependencyTreeRow[] = [row];
    for (let i = index - 1; i >= 0 && (path[0]?.depth ?? 0) > 0; i--) {
      const candidate = rows[i];
      if (candidate && candidate.depth < (path[0]?.depth ?? 0)) {
        path.unshift(candidate);
      }
    }

    path.forEach((pathRow, level) => {
      const frame = createFrame(
        level === 0 ? null : (path[level - 1] as ExpandableRow)
      );

      for (let position = 0; position <= pathRow.position; position++) {
        const name = childName(frame, position);
        frame.sameNameSiblings.set(
          name,
          (frame.sameNameSiblings.get(name) ?? 0) + 1
        );
      }

      frame.nextChild = pathRow.position + 1;
      stack.push(frame);

      const parentRow = frame.row;
      if (parentRow?.kind === 'package') addToPath(parentRow.nodeIndex, 1);
    });

    if (row.kind !== 'cycle' && row.expanded) pushFrame(row);
  };

  switch (start.kind) {
    case 'root':
      pushFrame(null);
      break;

    case 'below':
      if (start.parent.kind === 'package') {
        for (let link = start.parent.path.parent; link; link = link.parent) {
          addToPath(link.nodeIndex, 1);
        }
      }

      pushFrame(start.parent);
      break;

    case 'after':
      resumeAfter(start.rows, start.index);
      break;
  }

  for (let frame = stack.at(-1); frame; frame = stack.at(-1)) {
    if (frame.nextChild >= frame.childCount) {
      stack.pop();
      if (frame.row?.kind === 'package') addToPath(frame.row.nodeIndex, -1);
      continue;
    }

    const row = createChild(frame, frame.nextChild);
    frame.nextChild++;

    yield row;

    if (row.kind !== 'cycle' && row.expanded) pushFrame(row);
  }
}

function* noRows(): Generator<DependencyTreeRow, void, undefined> {}

/** Takes up to `count` rows from the generator. */
export const takeRows = (
  rows: Iterator<DependencyTreeRow>,
  count: number
): DependencyTreeRow[] => {
  const taken: DependencyTreeRow[] = [];

  while (taken.length < count) {
    const result = rows.next();
    if (result.done) break;
    taken.push(result.value);
  }

  return taken;
};

/** Returns the position after the last row below the row at `index`. */
const endOfSubtree = (rows: readonly DependencyTreeRow[], index: number) => {
  const depth = rows[index]?.depth ?? 0;
  let end = index + 1;

  while (end < rows.length && (rows[end]?.depth ?? 0) > depth) end++;

  return end;
};

/** Collapses the row at `index` and removes all rows below it. */
export const collapseRow = (
  rows: readonly DependencyTreeRow[],
  index: number
): DependencyTreeRow[] => {
  const row = rows[index];
  if (row === undefined || row.kind === 'cycle') return [...rows];

  return [
    ...rows.slice(0, index),
    { ...row, expanded: false },
    ...rows.slice(endOfSubtree(rows, index)),
  ];
};

/**
 * Expands the row at `index`. The rows below it are not inserted yet; take
 * them from the returned generator, in chunks if there are many, and insert
 * them after the row with `insertRows`.
 */
export const expandRow = (
  rows: readonly DependencyTreeRow[],
  index: number,
  context: DependencyTreeContext,
  expansion: TreeExpansion
): {
  rows: DependencyTreeRow[];
  rowsBelow: Generator<DependencyTreeRow, void, undefined>;
} => {
  const row = rows[index];

  if (row === undefined || row.kind === 'cycle' || !row.expandable) {
    return { rows: [...rows], rowsBelow: noRows() };
  }

  const expandedRow = { ...row, expanded: true };

  return {
    rows: [...rows.slice(0, index), expandedRow, ...rows.slice(index + 1)],
    rowsBelow: generateRows(context, expansion, expandedRow),
  };
};

/** Inserts rows before the row at `index`. */
export const insertRows = (
  rows: readonly DependencyTreeRow[],
  index: number,
  inserted: readonly DependencyTreeRow[]
): DependencyTreeRow[] => [
  ...rows.slice(0, index),
  ...inserted,
  ...rows.slice(index),
];
