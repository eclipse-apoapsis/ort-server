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

import { describe, expect, it } from 'vitest';

import {
  formatRunConfigurationDiffSummary,
  formatRunConfigurationDiffValue,
  maxValueLength,
} from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/-components/run-configuration-diff-dialog-utils';

describe('formatRunConfigurationDiffSummary', () => {
  it('formats added, removed, and modified counts', () => {
    expect(
      formatRunConfigurationDiffSummary({
        added: [],
        removed: [],
        modified: [],
        counts: {
          added: 3,
          removed: 1,
          modified: 2,
          total: 6,
        },
      })
    ).toBe('3 added, 1 removed, 2 modified');
  });
});

describe('formatRunConfigurationDiffValue', () => {
  it('formats objects and arrays as compact JSON', () => {
    expect(formatRunConfigurationDiffValue({ enabled: true })).toBe(
      '{"enabled":true}'
    );
    expect(formatRunConfigurationDiffValue(['ScanCode', 'FossID'])).toBe(
      '["ScanCode","FossID"]'
    );
  });

  it('formats primitive values compactly', () => {
    expect(formatRunConfigurationDiffValue(true)).toBe('true');
    expect(formatRunConfigurationDiffValue(1)).toBe('1');
    expect(formatRunConfigurationDiffValue(null)).toBe('null');
  });

  it('quotes strings', () => {
    expect(formatRunConfigurationDiffValue('ScanCode')).toBe('"ScanCode"');
  });

  it('truncates long values', () => {
    expect(formatRunConfigurationDiffValue('a'.repeat(400))).toHaveLength(
      maxValueLength
    );
    expect(formatRunConfigurationDiffValue('a'.repeat(400))).toMatch(/…$/u);
  });
});
