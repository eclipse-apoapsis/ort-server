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

import { SortingState } from '@tanstack/react-table';

/** A named sorting that can be applied with one click. */
export type SortPreset = {
  id: string;
  label: string;
  description: string;
  sorting: SortingState;
};

/** Returns whether two sortings have the same columns in the same order with the same directions. */
function isSameSorting(a: SortingState, b: SortingState): boolean {
  return (
    a.length === b.length &&
    a.every((sort, index) => {
      const other = b[index];
      return (
        other !== undefined && sort.id === other.id && sort.desc === other.desc
      );
    })
  );
}

/**
 * Finds the preset whose sorting equals the given sorting.
 *
 * @param sorting The current sorting.
 * @param presets The presets to search.
 * @returns The matching preset, or `undefined` if the sorting is not one of the presets.
 */
export function findMatchingSortPreset(
  sorting: SortingState,
  presets: SortPreset[]
): SortPreset | undefined {
  return presets.find((preset) => isSameSorting(preset.sorting, sorting));
}
