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

import { getNextPageParam } from '@/hooks/use-infinite-list';

/**
 * Build a page of the given size, as the API would return it. The items themselves do not matter
 * here, only the paging information does.
 */
const page = (limit: number, offset: number, totalCount: number) => ({
  data: Array.from({
    length: Math.max(Math.min(limit, totalCount - offset), 0),
  }),
  pagination: { limit, offset, totalCount, sortProperties: [] },
});

it.each([
  {
    name: 'first page of several',
    page: page(50, 0, 120),
    expected: 50,
  },
  {
    name: 'page in the middle',
    page: page(50, 50, 120),
    expected: 100,
  },
  {
    name: 'partial last page',
    page: page(50, 100, 120),
    expected: undefined,
  },
  {
    name: 'last page that is exactly full',
    page: page(50, 50, 100),
    expected: undefined,
  },
  {
    name: 'second to last page of a result that is an exact multiple of the page size',
    page: page(50, 0, 100),
    expected: 50,
  },
  {
    name: 'total count smaller than the page size',
    page: page(50, 0, 10),
    expected: undefined,
  },
  {
    name: 'total count equal to the page size',
    page: page(50, 0, 50),
    expected: undefined,
  },
  {
    name: 'empty result',
    page: page(50, 0, 0),
    expected: undefined,
  },
])('getNextPageParam - $name', ({ page, expected }) => {
  expect(getNextPageParam(page)).toBe(expected);
});
