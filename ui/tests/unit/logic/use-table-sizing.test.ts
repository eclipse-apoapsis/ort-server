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

import { expect, it } from 'vitest';

import {
  calculateColumnSizing,
  type ColumnInfo,
} from '@/hooks/use-table-sizing';

it('calculateColumnSizing calculates sizing for fixed, percentage, and grow columns', () => {
  const columns: ColumnInfo[] = [
    { id: 'col1', minSize: 40, maxSize: 200, isGrow: false, fixedSize: 100 },
    {
      id: 'col2',
      minSize: 40,
      maxSize: 300,
      isGrow: false,
      widthPercentage: 50,
    },
    { id: 'col3', minSize: 40, maxSize: 400, isGrow: true },
  ];
  const totalWidth = 600;

  const sizing = calculateColumnSizing(columns, totalWidth);
  expect(sizing['col1']).toBe(100); // fixed size
  expect(sizing['col2']).toBe(300); // 50% of 600
  expect(sizing['col3']).toBe(200); // remaining space
});
