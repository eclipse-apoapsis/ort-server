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
import { expect, it } from 'vitest';

import {
  convertToBackendSorting,
  updateColumnSorting,
} from '@/helpers/handle-multisort';

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
