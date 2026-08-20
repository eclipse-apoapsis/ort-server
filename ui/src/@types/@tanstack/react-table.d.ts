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

import type { DataTableColumnMeta } from '@/components/data-table/data-table-types';

declare module '@tanstack/react-table' {
  // Keep the global metadata available to legacy tables during the incremental
  // migration. Native tables receive this type from appTableFeatures instead.
  // Disable no-unused-vars for the type parameters, which are required to match
  // the original interface. The intentionally empty body declaration-merges
  // the shared metadata type into that interface.
  /* eslint-disable @typescript-eslint/no-empty-object-type, @typescript-eslint/no-unused-vars */
  interface ColumnMeta<
    in out TFeatures extends TableFeatures,
    in out TData extends RowData,
    TValue extends CellData = CellData,
  > extends DataTableColumnMeta {}
  /* eslint-enable @typescript-eslint/no-empty-object-type, @typescript-eslint/no-unused-vars */
}
