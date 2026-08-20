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

import type { ComponentType } from 'react';

import type { FilterOption } from '@/components/data-table/filter-multi-select';
import type { InfiniteList } from '@/lib/infinite-list';

// The application-wide column metadata erases each column's value type to
// unknown. Keep callbacks bivariant so a column can retain its concrete value
// type until DataTableFilter narrows the filter variant and restores it.
type BivariantCallback<TArgs extends unknown[], TResult = void> = {
  bivarianceHack(...args: TArgs): TResult;
}['bivarianceHack'];

export type TextFilter = {
  filterVariant: 'text';
  setFilterValue: (value: string | undefined) => void;
};

export type RegexFilter = {
  filterVariant: 'regex';
  setFilterValue: (value: string | undefined) => void;
};

export type SelectFilter<TValue> = {
  filterVariant: 'select';
  selectOptions: FilterOption<TValue>[];
  setSelected: BivariantCallback<[selected: TValue[]]>;
  align?: 'start' | 'end' | 'center';
};

export type InfiniteSelectFilter<TValue> = {
  filterVariant: 'infinite-select';
  selectOptions: InfiniteList<FilterOption<TValue>>;
  getSelectedOption: BivariantCallback<[value: TValue], FilterOption<TValue>>;
  setSelected: BivariantCallback<[selected: TValue[]]>;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  searchTerm: string;
  onSearchTermChange: (value: string) => void;
  align?: 'start' | 'end' | 'center';
};

export type SingleSelectFilter<TValue> = {
  filterVariant: 'single-select';
  selectOptions: {
    label: string;
    value: TValue;
    icon?: ComponentType<{ className?: string }>;
  }[];
  setSelected: BivariantCallback<[selected: TValue | undefined]>;
  align?: 'start' | 'end' | 'center';
};

export type Filter<TValue> =
  | TextFilter
  | RegexFilter
  | SelectFilter<TValue>
  | InfiniteSelectFilter<TValue>
  | SingleSelectFilter<TValue>;

/** Metadata shared by all application table columns. */
export interface DataTableColumnMeta {
  /** The filter configuration for this column. */
  filter?: Filter<unknown>;
  /** Column takes this percentage of total table width (e.g., 30 = 30%). */
  widthPercentage?: number;
  /** Column expands to fill remaining available space. */
  isGrow?: boolean;
}
