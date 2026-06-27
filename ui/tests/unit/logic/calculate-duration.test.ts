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

import { expect, it } from 'vitest';

import { calculateDuration, divmod } from '@/helpers/calculate-duration';

it('calculateDuration', () => {
  expect(
    calculateDuration('2024-06-11T13:07:45Z', '2024-06-11T13:08:15Z')
      .formattedDuration
  ).toBe('30s');
  expect(
    calculateDuration('2024-06-11T13:07:45Z', '2024-06-11T13:12:15Z')
      .formattedDuration
  ).toBe('4m 30s');
  expect(
    calculateDuration('2024-06-11T13:00:00Z', '2024-06-11T14:00:01Z')
      .formattedDuration
  ).toBe('1h 0m 1s');
  expect(
    calculateDuration('2024-06-11T13:00:00Z', '2024-06-22T14:42:01Z')
      .formattedDuration
  ).toBe('11d 1h 42m 1s');
});

it('divmod', () => {
  expect(divmod(10, 3)).toStrictEqual([3, 1]);
  expect(divmod(8, 2)).toStrictEqual([4, 0]);
  expect(divmod(1, 1)).toStrictEqual([1, 0]);
  expect(divmod(1, 0)).toStrictEqual([NaN, NaN]);
  expect(divmod(-10, 3)).toStrictEqual([-3, -1]);
  expect(divmod(10, -3)).toStrictEqual([-3, 1]);
  expect(divmod(-10, -3)).toStrictEqual([3, -1]);
});
