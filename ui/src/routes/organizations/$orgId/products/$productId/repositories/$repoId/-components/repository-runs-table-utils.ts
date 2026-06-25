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

import type { OrtRunSummary } from '@/api';

export type RunComparisonSelection = {
  baseRun?: OrtRunSummary;
  comparedRun?: OrtRunSummary;
  isDialogOpen: boolean;
};

export const emptyRunComparisonSelection: RunComparisonSelection = {
  isDialogOpen: false,
};

export const resetRunComparisonSelection = (): RunComparisonSelection => ({
  isDialogOpen: false,
});

export const selectRunForComparison = (
  selection: RunComparisonSelection,
  summary: OrtRunSummary
): RunComparisonSelection => {
  if (!selection.baseRun) {
    return { baseRun: summary, isDialogOpen: false };
  }

  if (selection.baseRun.index === summary.index) {
    return resetRunComparisonSelection();
  }

  if (!selection.comparedRun) {
    return {
      baseRun: selection.baseRun,
      comparedRun: summary,
      isDialogOpen: true,
    };
  }

  return selection;
};
