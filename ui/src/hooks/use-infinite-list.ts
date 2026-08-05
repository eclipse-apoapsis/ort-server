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

import {
  keepPreviousData,
  useInfiniteQuery,
  type QueryFunction,
  type QueryKey,
  type SkipToken,
} from '@tanstack/react-query';

import { PagingData } from '@/api/types.gen';

/**
 * The shape shared by all the paged endpoints of the API, that is, by every generated
 * `PagedResponse*` type.
 */
type PagedResponse<TItem> = {
  data: Array<TItem>;
  pagination: PagingData;
};

/**
 * The parts of the generated `*InfiniteOptions` the hook actually needs. Everything else they carry
 * is deliberately left out of this type, so that the generated options can be passed in as they
 * are.
 *
 * This is also why the query options below are a separate argument instead of fields of this type:
 * as soon as a field the generated options also have, such as `staleTime`, is named here, their
 * far more elaborate version of it has to match the one here, and it does not.
 */
type InfiniteListOptions<TItem, TQueryKey extends QueryKey, TPageParam> = {
  queryKey: TQueryKey;
  queryFn?:
    QueryFunction<PagedResponse<TItem>, TQueryKey, TPageParam> | SkipToken;
};

/**
 * What a caller says about the query itself, such as fetching only while a dropdown is open.
 */
type InfiniteListQueryOptions = {
  enabled?: boolean;
  staleTime?: number;
  gcTime?: number;
};

/**
 * What the hook gives back, so that components can take a list without repeating the generics.
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

/**
 * Load a paged endpoint of the API one page at a time.
 *
 * The generated `*InfiniteOptions` deliberately leave out `initialPageParam` and
 * `getNextPageParam`, which this hook fills in: paging starts at offset 0, and every following page
 * is requested at the offset right after the page that was received last, until the whole
 * `totalCount` has been read. The pages are flattened into a single `items` list for the caller.
 *
 * Pass the generated options as the first argument and anything to say about the query itself as
 * the second one, as in
 * `useInfiniteList(getOrganizationsInfiniteOptions({ query }), { enabled: isOpen })`.
 */
export function useInfiniteList<TItem, TQueryKey extends QueryKey, TPageParam>(
  { queryKey, queryFn }: InfiniteListOptions<TItem, TQueryKey, TPageParam>,
  { enabled, staleTime, gcTime }: InfiniteListQueryOptions = {}
): InfiniteList<TItem> {
  const { data, isPending, isError, error, hasNextPage, ...query } =
    useInfiniteQuery({
      queryKey,
      // The generated query functions take the page parameter either as a number, which they turn
      // into the `offset` of the request, or as a whole set of request parameters. Only the number
      // is used here, which the generic page parameter of the query function does not spell out.
      queryFn: queryFn as
        QueryFunction<PagedResponse<TItem>, TQueryKey, number> | SkipToken,
      enabled,
      staleTime,
      gcTime,
      initialPageParam: 0,
      getNextPageParam,
      // Keep the pages that are shown while a new search term is being fetched, to avoid flicker.
      placeholderData: keepPreviousData,
    });

  return {
    items: data?.pages.flatMap((page) => page.data) ?? [],
    totalCount: data?.pages.at(-1)?.pagination.totalCount ?? 0,
    isPending,
    isError,
    error,
    hasNextPage,
    isFetchingNextPage: query.isFetchingNextPage,
    fetchNextPage: query.fetchNextPage,
  };
}
