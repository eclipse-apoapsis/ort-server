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

import {
  Column,
  RegexFilter,
  SelectFilter,
  TextFilter,
} from '@tanstack/react-table';

import { FilterMultiSelect } from '@/components/data-table/filter-multi-select';
import { FilterRegex } from '@/components/data-table/filter-regex';
import { FilterText } from '@/components/data-table/filter-text';

interface DataTableFilterProps<TData, TValue> {
  column: Column<TData, TValue>;
  showTitle?: boolean; // Whether to show the title next to the filter icon
}

export function DataTableFilter<TData, TValue>({
  column,
  showTitle,
}: DataTableFilterProps<TData, TValue>) {
  const filterVariant = column.columnDef.meta?.filter?.filterVariant;

  const columnFilterValue = column.getFilterValue();
  const title = column.columnDef.header?.toString();

  if (filterVariant === 'text') {
    const { setFilterValue } = column.columnDef.meta?.filter as TextFilter;

    return (
      <FilterText
        title={title}
        showTitle={showTitle}
        filterValue={(columnFilterValue as string) || ''}
        setFilterValue={setFilterValue}
      />
    );
  }

  if (filterVariant === 'regex') {
    const { setFilterValue } = column.columnDef.meta?.filter as RegexFilter;

    return (
      <FilterRegex
        title={title}
        showTitle={showTitle}
        filterValue={(columnFilterValue as string) || ''}
        setFilterValue={setFilterValue}
      />
    );
  }

  if (filterVariant === 'select') {
    const { selectOptions, setSelected } = column.columnDef.meta
      ?.filter as SelectFilter<TValue>;
    const align = column.columnDef.meta?.filter?.align;

    return (
      <FilterMultiSelect
        title={title}
        showTitle={showTitle}
        options={selectOptions}
        selected={(columnFilterValue as TValue[]) ?? []}
        setSelected={setSelected}
        align={align}
      />
    );
  }
}
