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

import { PagingData } from '@/api/types.gen';

/**
 * The shape shared by all the paged endpoints of the API, that is, by every generated
 * `PagedResponse*` type.
 */
export type PagedResponse<TItem> = {
  data: Array<TItem>;
  pagination: PagingData;
};

/**
 * What the `useInfiniteList` hook gives back, so that components can take a list without repeating
 * the generics.
 */
export type InfiniteList<TItem> = {
  items: Array<TItem>;
  totalCount: number;
  isPending: boolean;
  isError: boolean;
  error: Error | null;
  hasNextPage: boolean;
  isFetchingNextPage: boolean;
  fetchNextPage: () => void;
};

/**
 * Work out the offset the next page has to be requested at, based on the page that was received
 * last. Return `undefined` once the whole `totalCount` has been read, which tells the query that
 * there is nothing more to load.
 */
export function getNextPageParam(lastPage: PagedResponse<unknown>) {
  const { limit, offset, totalCount } = lastPage.pagination;
  const next = offset + limit;

  return next < totalCount ? next : undefined;
}
