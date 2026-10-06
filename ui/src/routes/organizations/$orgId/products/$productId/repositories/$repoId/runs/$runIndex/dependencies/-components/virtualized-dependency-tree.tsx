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
  defaultRangeExtractor,
  useWindowVirtualizer,
} from '@tanstack/react-virtual';
import { useLayoutEffect, useRef, useState, type FocusEvent } from 'react';

import type { DependencyGraph } from '@/api';
import type { PackageIdType } from '@/schemas';
import type {
  ExpandableRow,
  DependencyTreeRow as Row,
} from './dependency-tree-model';
import { DependencyTreeRow } from './dependency-tree-row';

/** The height assumed for rows that have not been measured yet, in pixels. */
const ESTIMATED_ROW_HEIGHT_PX = 32;

/** The number of rows mounted above and below the visible ones. */
const OVERSCAN = 10;

type VirtualizedDependencyTreeProps = {
  rows: readonly Row[];
  graph: DependencyGraph;
  packageIdType: PackageIdType;
  searchTerm: string;
  onToggle: (row: ExpandableRow) => void;
};

const rowIndexOf = (element: EventTarget | null) => {
  const rowElement =
    element instanceof Element ? element.closest('[data-index]') : null;
  const index = Number(rowElement?.getAttribute('data-index'));

  return Number.isInteger(index) ? index : -1;
};

/**
 * Mounts only the rows of the dependency tree that are on screen or close to
 * it. The page itself scrolls, so the list is positioned relative to the
 * window.
 */
export const VirtualizedDependencyTree = ({
  rows,
  graph,
  packageIdType,
  searchTerm,
  onToggle,
}: VirtualizedDependencyTreeProps) => {
  // The virtualizer is a mutable object whose identity does not change when it
  // scrolls or measures rows. React Compiler would reuse the rows read from it
  // as long as its identity stays the same and show outdated rows, so this
  // component must not be compiled. See #6147 and, for the switch to compiling
  // components by default, #5741 and #6030.
  'use no memo';

  const listRef = useRef<HTMLDivElement>(null);
  const [scrollMargin, setScrollMargin] = useState(0);
  const [focusedRowId, setFocusedRowId] = useState<string | null>(null);

  // The list starts below the search field and the tabs, whose height changes
  // when the no-match message appears or the tabs wrap. Measure the distance
  // to the top of the page whenever the size of the page changes.
  useLayoutEffect(() => {
    const list = listRef.current;
    if (list === null) return;

    const measure = () =>
      setScrollMargin(list.getBoundingClientRect().top + window.scrollY);
    const observer = new ResizeObserver(measure);

    measure();
    observer.observe(document.body);

    return () => observer.disconnect();
  }, []);

  // Keep the row with the keyboard focus mounted while it is scrolled out of
  // view, so that the focus does not get lost.
  const focusedIndex =
    focusedRowId === null
      ? -1
      : rows.findIndex((row) => row.id === focusedRowId);

  const virtualizer = useWindowVirtualizer({
    count: rows.length,
    estimateSize: () => ESTIMATED_ROW_HEIGHT_PX,
    overscan: OVERSCAN,
    scrollMargin,
    getItemKey: (index) => rows[index]?.id ?? index,
    rangeExtractor: (range) => {
      const indexes = defaultRangeExtractor(range);

      if (focusedIndex >= 0 && !indexes.includes(focusedIndex)) {
        indexes.push(focusedIndex);
        indexes.sort((a, b) => a - b);
      }

      return indexes;
    },
  });

  const handleFocus = (event: FocusEvent<HTMLDivElement>) => {
    setFocusedRowId(rows[rowIndexOf(event.target)]?.id ?? null);
  };

  const handleBlur = (event: FocusEvent<HTMLDivElement>) => {
    if (!event.currentTarget.contains(event.relatedTarget)) {
      setFocusedRowId(null);
    }
  };

  return (
    <div
      ref={listRef}
      className='relative w-full'
      style={{ height: virtualizer.getTotalSize() }}
      onFocus={handleFocus}
      onBlur={handleBlur}
    >
      {virtualizer.getVirtualItems().map((item) => {
        const row = rows[item.index];
        if (row === undefined) return null;

        return (
          <DependencyTreeRow
            key={item.key}
            row={row}
            index={item.index}
            top={item.start - scrollMargin}
            graph={graph}
            packageIdType={packageIdType}
            searchTerm={searchTerm}
            measureRef={virtualizer.measureElement}
            onToggle={onToggle}
          />
        );
      })}
    </div>
  );
};
