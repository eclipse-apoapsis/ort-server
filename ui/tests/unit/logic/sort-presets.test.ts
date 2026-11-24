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
import { describe, expect, it } from 'vitest';

import { findMatchingSortPreset, SortPreset } from '@/helpers/sort-presets';

const severity: SortingState = [
  { id: 'rating', desc: true },
  { id: 'epssScore', desc: true },
];

const presets: SortPreset[] = [
  {
    id: 'severity',
    label: 'Severity',
    description: 'Highest rating first.',
    sorting: severity,
  },
];

describe('findMatchingSortPreset', () => {
  it('returns the preset with an equal sorting', () => {
    expect(findMatchingSortPreset(severity, presets)?.id).toBe('severity');
  });

  it('returns no preset for a sorting with an added column', () => {
    expect(
      findMatchingSortPreset(
        [...severity, { id: 'externalId', desc: false }],
        presets
      )
    ).toBeUndefined();
  });

  it('returns no preset for a sorting with a removed column', () => {
    expect(
      findMatchingSortPreset([{ id: 'rating', desc: true }], presets)
    ).toBeUndefined();
  });

  it('returns no preset for the same columns in a different order', () => {
    expect(
      findMatchingSortPreset(
        [
          { id: 'epssScore', desc: true },
          { id: 'rating', desc: true },
        ],
        presets
      )
    ).toBeUndefined();
  });

  it('returns no preset for a different direction', () => {
    expect(
      findMatchingSortPreset(
        [
          { id: 'rating', desc: false },
          { id: 'epssScore', desc: true },
        ],
        presets
      )
    ).toBeUndefined();
  });
});
