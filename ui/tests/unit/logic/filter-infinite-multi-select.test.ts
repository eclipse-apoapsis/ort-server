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

import { includeSelectedFilterOptions } from '@/components/data-table/filter-infinite-multi-select-utils';
import type { FilterOption } from '@/components/data-table/filter-multi-select';

const option = (value: string): FilterOption<string> => ({
  label: value,
  value,
});

it('keeps selected options visible before their page is loaded', () => {
  expect(
    includeSelectedFilterOptions(
      [option('Apache-2.0'), option('BSD-3-Clause')],
      ['MIT'],
      option
    )
  ).toEqual([option('MIT'), option('Apache-2.0'), option('BSD-3-Clause')]);
});

it('puts selected options first in selection order', () => {
  expect(
    includeSelectedFilterOptions(
      [option('Apache-2.0'), option('MIT'), option('BSD-3-Clause')],
      ['MIT', 'Apache-2.0'],
      option
    )
  ).toEqual([option('MIT'), option('Apache-2.0'), option('BSD-3-Clause')]);
});

it('does not duplicate selected options that have been loaded', () => {
  const options = includeSelectedFilterOptions(
    [option('Apache-2.0'), option('MIT')],
    ['MIT'],
    option
  );

  expect(options.filter(({ value }) => value === 'MIT')).toHaveLength(1);
});
