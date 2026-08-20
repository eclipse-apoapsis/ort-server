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

import '@tanstack/react-table';

import type { CellData, RowData, TableFeatures } from '@tanstack/react-table';

import type { FilterOption } from '@/components/data-table/filter-multi-select';
import type { InfiniteList } from '@/lib/infinite-list';

declare module '@tanstack/react-table' {
  // Extend the ColumnMeta interface to include properties needed for filtering
  // Disable no-unused-vars for TFeatures and TData, they are needed here to
  // ensure the extended interface matches the original one.
  /* eslint-disable @typescript-eslint/no-unused-vars */
  interface ColumnMeta<
    in out TFeatures extends TableFeatures,
    in out TData extends RowData,
    TValue extends CellData = CellData,
  > {
    filter?: Filter<TValue>;
    /** Column takes this percentage of total table width (e.g., 30 = 30%) */
    widthPercentage?: number;
    /** Column expands to fill remaining available space */
    isGrow?: boolean;
  }
  /* eslint-enable @typescript-eslint/no-unused-vars */

  // Define the different filter variants

  type TextFilter = {
    filterVariant: 'text';
    setFilterValue: (value: string | undefined) => void;
  };

  type RegexFilter = {
    filterVariant: 'regex';
    setFilterValue: (value: string | undefined) => void;
  };

  type SelectFilter<TValue> = {
    filterVariant: 'select';
    selectOptions: FilterOption<TValue>[];
    setSelected: (selected: TValue[]) => void;
    align?: 'start' | 'end' | 'center';
  };

  type InfiniteSelectFilter<TValue> = {
    filterVariant: 'infinite-select';
    selectOptions: InfiniteList<FilterOption<TValue>>;
    getSelectedOption: (value: TValue) => FilterOption<TValue>;
    setSelected: (selected: TValue[]) => void;
    open: boolean;
    onOpenChange: (open: boolean) => void;
    searchTerm: string;
    onSearchTermChange: (value: string) => void;
    align?: 'start' | 'end' | 'center';
  };

  type SingleSelectFilter<TValue> = {
    filterVariant: 'single-select';
    selectOptions: {
      label: string;
      value: TValue;
      icon?: React.ComponentType<{ className?: string }>;
    }[];
    setSelected: (selected: TValue | undefined) => void;
    align?: 'start' | 'end' | 'center';
  };

  // Define the Filter type as a union of the filter variants
  type Filter =
    | TextFilter
    | RegexFilter
    | SelectFilter<TValue>
    | InfiniteSelectFilter<TValue>
    | SingleSelectFilter<TValue>;
}
