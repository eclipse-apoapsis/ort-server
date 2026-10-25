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

import type {
  DefaultError,
  QueryClient,
  QueryKey,
  UseSuspenseQueryOptions,
} from '@tanstack/react-query';
import type { AxiosError } from 'axios';

import type { GetFileArchivesData, PagedResponseProvenanceFile } from '@/api';
import { convertToBackendSorting } from '@/helpers/handle-multisort';
import { routePrefetchStaleTime } from '@/lib/query-client';
import type { AdminProvenanceFilesSearchParameters } from '@/schemas';

/** The page size to use if none is set in the search parameters. */
export const provenanceFilesDefaultPageSize = 20;

/** The query parameters of the endpoints that list provenance files. */
export type ProvenanceFilesQuery = NonNullable<GetFileArchivesData['query']>;

/** The options of a query as generated for the API, with the specific type of its query key. */
type GeneratedQueryOptions<
  TData,
  TQueryKey extends QueryKey,
> = UseSuspenseQueryOptions<TData, AxiosError<DefaultError>, TData, TQueryKey>;

/**
 * The configuration of a page to manage a type of stored provenance files, such as file archives or file lists. It
 * provides the texts of the page and the API functions for this type of files.
 */
export type ProvenanceFilesConfig<TFilesQueryKey extends QueryKey> = {
  /** The title of the page. */
  title: string;
  /** A description of the files shown at the top of the page. */
  description: string;
  /** The human-readable name of a single file, such as "file archive". */
  thingName: string;
  /** The path of the page, used for navigation. */
  to: '/admin/file-archives' | '/admin/file-lists';
  /** Return the options of the query for the files matching the given query parameters. */
  filesQueryOptions: (
    query: ProvenanceFilesQuery
  ) => GeneratedQueryOptions<PagedResponseProvenanceFile, TFilesQueryKey>;
  /** The key that matches the queries for all pages of files, to invalidate them when the files have changed. */
  filesQueryKey: QueryKey;
  /** Delete the file with the given ID. */
  deleteFile: (id: string) => Promise<unknown>;
};

/** Convert the given search parameters of a page to the query parameters of the endpoint that lists the files. */
export const toProvenanceFilesQuery = ({
  page,
  pageSize,
  sortBy,
  filter,
}: AdminProvenanceFilesSearchParameters): ProvenanceFilesQuery => {
  const limit = pageSize ?? provenanceFilesDefaultPageSize;

  return {
    limit,
    offset: page ? (page - 1) * limit : 0,
    sort: convertToBackendSorting(sortBy),
    filter: filter || undefined,
  };
};

/** Select the search parameters that the data of a page depends on. */
export const provenanceFilesLoaderDeps = ({
  search: { page, pageSize, sortBy, filter },
}: {
  search: AdminProvenanceFilesSearchParameters;
}) => ({ page, pageSize, sortBy, filter });

/** Load the data of a page with the given [config] for the given search parameters [deps]. */
export const loadProvenanceFiles = async <TFilesQueryKey extends QueryKey>(
  queryClient: QueryClient,
  config: ProvenanceFilesConfig<TFilesQueryKey>,
  deps: AdminProvenanceFilesSearchParameters
) => {
  await queryClient.ensureQueryData({
    ...config.filesQueryOptions(toProvenanceFilesQuery(deps)),
    staleTime: routePrefetchStaleTime,
  });
};
