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

import { describe, expect, it } from 'vitest';

import type { OrtRunSummary } from '@/api';
import {
  emptyRunComparisonSelection,
  resetRunComparisonSelection,
  selectRunForComparison,
} from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/-components/repository-runs-table-utils';

const runSummary = (index: number) => ({ index }) as OrtRunSummary;

describe('resetRunComparisonSelection', () => {
  it('clears selected runs and closes the dialog', () => {
    expect(resetRunComparisonSelection()).toEqual({
      isDialogOpen: false,
    });
  });
});

describe('selectRunForComparison', () => {
  it('clears the selection when selecting the base run again', () => {
    expect(
      selectRunForComparison(
        { baseRun: runSummary(1), isDialogOpen: false },
        runSummary(1)
      )
    ).toEqual({
      isDialogOpen: false,
    });
  });

  it('selects the first run as the base run', () => {
    expect(
      selectRunForComparison(emptyRunComparisonSelection, runSummary(1))
    ).toEqual({
      baseRun: runSummary(1),
      isDialogOpen: false,
    });
  });

  it('selects the second run and opens the dialog', () => {
    expect(
      selectRunForComparison(
        { baseRun: runSummary(1), isDialogOpen: false },
        runSummary(2)
      )
    ).toEqual({
      baseRun: runSummary(1),
      comparedRun: runSummary(2),
      isDialogOpen: true,
    });
  });
});
