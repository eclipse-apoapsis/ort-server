/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import { SortingState } from '@tanstack/react-table';

/**
 * Handle multi-sorting of table columns.
 *
 * @param columns Current sorting state of the table.
 * @param column The column to sort by.
 * @returns Updated sorting state of the table:
 * (1) if the column is not in the sorting array, it is added with ascending order;
 * (2) if the column is already in the sorting array and is sorted in ascending order, it is updated to descending order;
 * (3) if the column is already in the sorting array and is sorted in descending order, it is removed from the sorting array.
 */
export const updateColumnSorting = (
  columns: SortingState | undefined,
  column: {
    id: string;
    desc: boolean | undefined; // For column removal to work, the type must be extended from ColumnSort
  }
): SortingState | undefined => {
  // When no sorting is applied, do early return with the column
  if (!columns) {
    return [{ ...column, desc: column.desc ?? false }];
  }

  // Find if the column already exists in the sorting array
  const index = columns.findIndex((c) => c.id === column.id);

  // Initialize a new sorting array
  let updatedSort: SortingState;

  if (index >= 0) {
    // If column already exists, safely get the current sorting order
    const currentSortOrder = columns[index];

    if (currentSortOrder && !currentSortOrder.desc) {
      // Cycle from asc to desc
      updatedSort = [...columns];
      updatedSort[index] = { id: column.id, desc: true };
    } else {
      // Remove the column when cycling to none
      updatedSort = columns.filter((_, ind) => ind !== index);
    }
  } else {
    // If column is not in the sort array, add it with asc order
    updatedSort = [...columns, { id: column.id, desc: false }];
  }

  // Update the search object with the new sort order
  return updatedSort.length > 0 ? updatedSort : undefined;
};

/**
 * Convert the sorting state to a string format for the backend.
 *
 * @param sorting The current sorting state of the table.
 * @returns The sorting state as a string for the backend.
 */
export const convertToBackendSorting = (
  sorting: SortingState | undefined
): string | undefined => {
  if (!sorting || sorting.length === 0) {
    return undefined;
  }

  return sorting
    .map((sort) => (sort.desc ? `-${sort.id}` : `${sort.id}`))
    .join(',');
};

//
// Unit tests for multisorting
//

if (import.meta.vitest) {
  const { it, expect } = import.meta.vitest;

  // Test the updateColumnSorting() function

  it('adds a new column to an empty sorting state', () => {
    const columns = undefined;
    const column = { id: 'column1', desc: false };

    expect(updateColumnSorting(columns, column)).toEqual([
      { id: 'column1', desc: false },
    ]);
  });

  it('adds a new column to an existing sorting state', () => {
    const columns = [{ id: 'column1', desc: false }];
    const column = { id: 'column2', desc: false };

    expect(updateColumnSorting(columns, column)).toEqual([
      { id: 'column1', desc: false },
      { id: 'column2', desc: false },
    ]);
  });

  it('updates the sorting state of an existing column from desc -> asc', () => {
    const columns = [{ id: 'column1', desc: false }];
    const column = { id: 'column1', desc: false };

    expect(updateColumnSorting(columns, column)).toEqual([
      { id: 'column1', desc: true },
    ]);
  });

  it('when an existing column is asc -> removes the column from the sorting state', () => {
    const columns = [{ id: 'column1', desc: true }];
    const column = { id: 'column1', desc: true };

    expect(updateColumnSorting(columns, column)).toEqual(undefined);
  });

  it('more complicated sorting state update', () => {
    const columns = [
      { id: 'column1', desc: false },
      { id: 'column2', desc: true },
      { id: 'column3', desc: false },
    ];
    const column = { id: 'column2', desc: true };

    expect(updateColumnSorting(columns, column)).toEqual([
      { id: 'column1', desc: false },
      { id: 'column3', desc: false },
    ]);
  });

  // Test the convertToBackendSorting() function

  it('converts sorting state to backend format', () => {
    const sorting: SortingState = [
      { id: 'column1', desc: false },
      { id: 'column2', desc: true },
    ];

    expect(convertToBackendSorting(sorting)).toEqual('column1,-column2');
  });

  it('returns undefined for empty sorting state', () => {
    const sorting: SortingState = [];

    expect(convertToBackendSorting(sorting)).toEqual(undefined);
  });
}
