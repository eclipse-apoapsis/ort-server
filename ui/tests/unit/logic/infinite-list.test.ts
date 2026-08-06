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

import { expect, it, vi } from 'vitest';

import {
  combineInfiniteLists,
  getNextPageParam,
  type InfiniteList,
} from '@/lib/infinite-list';

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

/**
 * Build a list of the given entries, as `useInfiniteList` would hand it over once it has loaded
 * them all. A case that is about something else than the entries says so through the overrides.
 */
const list = (
  items: string[],
  overrides: Partial<InfiniteList<string>> = {}
): InfiniteList<string> => ({
  items,
  totalCount: items.length,
  isPending: false,
  isError: false,
  error: null,
  hasNextPage: false,
  isFetchingNextPage: false,
  fetchNextPage: () => {},
  ...overrides,
});

it('combineInfiniteLists - shows the lists one after another', () => {
  const combined = combineInfiniteLists([
    list(['org-a', 'org-b']),
    list(['product-a']),
    list(['repository-a']),
  ]);

  expect(combined.items).toEqual([
    'org-a',
    'org-b',
    'product-a',
    'repository-a',
  ]);
});

it('combineInfiniteLists - counts the entries of every list', () => {
  const combined = combineInfiniteLists([
    list(['org-a'], { totalCount: 120 }),
    list(['product-a'], { totalCount: 7 }),
    list([], { totalCount: 0 }),
  ]);

  expect(combined.totalCount).toBe(127);
});

it('combineInfiniteLists - asks the first list that still has a page', () => {
  const exhausted = vi.fn();
  const next = vi.fn();
  const later = vi.fn();

  const combined = combineInfiniteLists([
    list(['org-a'], { fetchNextPage: exhausted }),
    list(['product-a'], { hasNextPage: true, fetchNextPage: next }),
    list(['repository-a'], { hasNextPage: true, fetchNextPage: later }),
  ]);
  combined.fetchNextPage();

  expect(combined.hasNextPage).toBe(true);
  expect(next).toHaveBeenCalledOnce();
  expect(exhausted).not.toHaveBeenCalled();
  expect(later).not.toHaveBeenCalled();
});

it('combineInfiniteLists - is out of pages once every list is', () => {
  const fetchNextPage = vi.fn();

  const combined = combineInfiniteLists([
    list(['org-a'], { fetchNextPage }),
    list(['product-a'], { fetchNextPage }),
  ]);
  combined.fetchNextPage();

  expect(combined.hasNextPage).toBe(false);
  expect(fetchNextPage).not.toHaveBeenCalled();
});

it('combineInfiniteLists - stays pending while any list is', () => {
  const loading = combineInfiniteLists([
    list([], { isPending: true }),
    list(['product-a']),
  ]);

  expect(loading.isPending).toBe(true);
  expect(combineInfiniteLists([list([]), list(['product-a'])]).isPending).toBe(
    false
  );
});

it('combineInfiniteLists - reports the first error', () => {
  const error = new Error('Failed to load the products');

  const combined = combineInfiniteLists([
    list(['org-a']),
    list([], { isError: true, error }),
    list([], { isError: true, error: new Error('And the repositories') }),
  ]);

  expect(combined.isError).toBe(true);
  expect(combined.error).toBe(error);
});
