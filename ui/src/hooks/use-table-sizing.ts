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

import { Table } from '@tanstack/react-table';
import { useLayoutEffect, useRef } from 'react';

const DEFAULT_MIN_SIZE = 40;

/**
 * Clamp a size value between min and max bounds.
 */
function clampSize(
  size: number,
  max = Number.MAX_SAFE_INTEGER,
  min = DEFAULT_MIN_SIZE
): number {
  return Math.max(Math.min(size, max), min);
}

type ColumnInfo = {
  id: string;
  minSize: number;
  maxSize: number;
  isGrow: boolean;
  widthPercentage?: number;
  fixedSize?: number;
};

/**
 * Calculate column sizes based on container width and column meta properties.
 *
 * Algorithm:
 * 1. Fixed columns (with `size`) get their exact pixel width (clamped to min/max)
 * 2. Percentage columns get their percentage of total width (clamped to min/max)
 * 3. Grow columns and unsized columns share remaining space equally
 * 4. If a flex column is constrained by minSize/maxSize, remaining space is
 *    redistributed to other flex columns (iterative allocation)
 */
function calculateColumnSizing(
  columns: ColumnInfo[],
  totalWidth: number
): Record<string, number> {
  const sizing: Record<string, number> = {};
  let availableWidth = totalWidth;

  // Pass 1: Allocate fixed and percentage-based columns
  columns.forEach((col) => {
    if (col.fixedSize !== undefined) {
      const size = clampSize(col.fixedSize, col.maxSize, col.minSize);
      sizing[col.id] = size;
      availableWidth -= size;
    } else if (col.widthPercentage !== undefined) {
      const size = clampSize(
        (col.widthPercentage / 100) * totalWidth,
        col.maxSize,
        col.minSize
      );
      sizing[col.id] = size;
      availableWidth -= size;
    }
  });

  // Pass 2: Handle grow and unsized columns iteratively
  // This ensures minSize constraints don't cause overflow
  const flexColumns = columns.filter(
    (col) =>
      col.isGrow ||
      (col.fixedSize === undefined && col.widthPercentage === undefined)
  );

  if (flexColumns.length > 0) {
    const remainingColumns = [...flexColumns];
    let remainingWidth = availableWidth;

    // Iteratively allocate: columns that need minSize/maxSize get it first
    while (remainingColumns.length > 0) {
      const flexWidth = Math.floor(remainingWidth / remainingColumns.length);
      let changed = false;

      for (let i = remainingColumns.length - 1; i >= 0; i--) {
        const col = remainingColumns[i];
        if (!col) continue;

        // Check if this column is constrained by min/max
        if (flexWidth < col.minSize) {
          sizing[col.id] = col.minSize;
          remainingWidth -= col.minSize;
          remainingColumns.splice(i, 1);
          changed = true;
        } else if (flexWidth > col.maxSize) {
          sizing[col.id] = col.maxSize;
          remainingWidth -= col.maxSize;
          remainingColumns.splice(i, 1);
          changed = true;
        }
      }

      // If no constraints hit, distribute equally to remaining columns
      if (!changed) {
        remainingColumns.forEach((col) => {
          sizing[col.id] = Math.max(flexWidth, 0);
        });
        break;
      }
    }
  }

  return sizing;
}

/**
 * Hook that manages responsive column sizing for TanStack Table.
 *
 * Features:
 * - Calculates column widths based on container width
 * - Supports fixed, percentage, and grow columns
 * - Respects minSize and maxSize constraints
 * - Recalculates on container resize via ResizeObserver
 *
 * @param table - The TanStack Table instance
 * @returns A ref to attach to the table container element
 *
 * @example
 * ```tsx
 * function MyTable({ table }) {
 *   const containerRef = useTableSizing(table);
 *   return (
 *     <div ref={containerRef}>
 *       <Table>...</Table>
 *     </div>
 *   );
 * }
 * ```
 */
export function useTableSizing<TData>(table: Table<TData>) {
  const containerRef = useRef<HTMLDivElement>(null);

  useLayoutEffect(() => {
    if (!containerRef.current) return;

    const calculateAndApplySizing = () => {
      const totalWidth = containerRef.current?.clientWidth;
      if (!totalWidth) return;

      const headers = table.getLeafHeaders();

      // Collect column info for processing
      const columns: ColumnInfo[] = headers.map((header) => {
        const columnDef = header.column.columnDef;
        const meta = columnDef.meta;
        return {
          id: columnDef.id ?? header.id,
          minSize: columnDef.minSize ?? DEFAULT_MIN_SIZE,
          maxSize: columnDef.maxSize ?? Number.MAX_SAFE_INTEGER,
          isGrow: meta?.isGrow ?? false,
          widthPercentage: meta?.widthPercentage,
          fixedSize:
            !meta?.isGrow && !meta?.widthPercentage
              ? columnDef.size
              : undefined,
        };
      });

      const sizing = calculateColumnSizing(columns, totalWidth);
      table.setColumnSizing(sizing);
    };

    // ResizeObserver handles both initial load and resize events
    const observer = new ResizeObserver((entries) => {
      const entry = entries[0];
      if (entry) {
        calculateAndApplySizing();
      }
    });

    observer.observe(containerRef.current);
    return () => observer.disconnect();
  }, [table]);

  return containerRef;
}

//
// Unit tests for the clampSize(), calculateColumnSizing() functions.
//

if (import.meta.vitest) {
  const { it, expect } = import.meta.vitest;

  // Test the clampSize() function

  it('clamps size within min and max bounds', () => {
    expect(clampSize(50, 100, 40)).toBe(50); // within bounds
    expect(clampSize(30, 100, 40)).toBe(40); // below min
    expect(clampSize(120, 100, 40)).toBe(100); // above max
  });

  // Test the calculateColumnSizing() function

  it('calculates sizing for fixed, percentage, and grow columns', () => {
    const columns: ColumnInfo[] = [
      { id: 'col1', minSize: 40, maxSize: 200, isGrow: false, fixedSize: 100 },
      {
        id: 'col2',
        minSize: 40,
        maxSize: 300,
        isGrow: false,
        widthPercentage: 50,
      },
      { id: 'col3', minSize: 40, maxSize: 400, isGrow: true },
    ];
    const totalWidth = 600;

    const sizing = calculateColumnSizing(columns, totalWidth);
    expect(sizing['col1']).toBe(100); // fixed size
    expect(sizing['col2']).toBe(300); // 50% of 600
    expect(sizing['col3']).toBe(200); // remaining space
  });
}
