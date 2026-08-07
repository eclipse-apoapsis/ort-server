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

import { QueryClient } from '@tanstack/react-query';

import type { Organization, Product, Repository } from '@/api';
import {
  getOrganizationProductsQueryKey,
  getOrganizationQueryKey,
  getOrganizationsQueryKey,
  getProductQueryKey,
  getProductRepositoriesQueryKey,
  getRepositoryQueryKey,
  getRepositoryRunQueryKey,
  getRepositoryRunsQueryKey,
  getUserInfoQueryKey,
} from '@/api/@tanstack/react-query.gen';

/**
 * Cache maintenance for the entities of the hierarchy: organizations, products, repositories and
 * runs. One function per lifecycle event, so that a call site can be read without knowing which
 * query keys exist.
 *
 * Every list endpoint of these entities is read under three different keys — a `limit: 1` count
 * query behind a breadcrumb chevron, the paged queries of the tables, and the infinite query of a
 * dropdown — and a key built from a `path` alone matches all of them, as the generated keys hold
 * `query` and `_infinite` as extra fields that a partial match ignores. That is why refreshing a
 * parent list is a single call here, and why it must go through the generated `*QueryKey` functions
 * rather than a hand-written key.
 *
 * Two rules apply to every caller:
 *
 * 1. **Invalidate before navigating.** The invalidation has to be in place before the loader of the
 *    destination route runs, or that route renders stale data and only corrects itself once a
 *    refetch lands. These functions mark the cache synchronously, so calling one before `navigate`
 *    is enough; they are not awaited.
 * 2. **A deleted entity is never refetched.** The settings pages read the entity they delete with
 *    `useSuspenseQuery`, and navigation does not reliably commit React's unmount before the cache
 *    settles, so refetching the detail query here would request an entity the backend has just
 *    removed. The deletion functions therefore pass `refetchType: 'none'`: the detail query is
 *    marked stale without its active observer fetching, and whoever asks for it next gets the 404
 *    that is by then the truthful answer.
 */

/**
 * Refresh the list of organizations after one was created.
 */
export function organizationCreated(queryClient: QueryClient): void {
  queryClient.invalidateQueries({ queryKey: getOrganizationsQueryKey() });
}

/**
 * Seed the cache with an updated organization and refresh the list it is shown in.
 *
 * The `PATCH` response is the same `Organization` a `GET` returns, so writing it here spares a
 * request and, more importantly, gives the route loader the new name at once: the loader reads the
 * organization through `ensureQueryData`, which hands back whatever is cached, and it is the loader
 * that writes the breadcrumb and through it the document title.
 */
export function organizationUpdated(
  queryClient: QueryClient,
  organization: Organization
): void {
  queryClient.setQueryData(
    getOrganizationQueryKey({ path: { organizationId: organization.id } }),
    organization
  );
  organizationCreated(queryClient);
}

/**
 * Refresh the list of organizations after one was deleted, and mark the deleted organization stale
 * without refetching it.
 */
export function organizationDeleted(
  queryClient: QueryClient,
  organizationId: number
): void {
  organizationCreated(queryClient);
  queryClient.invalidateQueries({
    queryKey: getOrganizationQueryKey({ path: { organizationId } }),
    refetchType: 'none',
  });
}

/**
 * Refresh the products of an organization after one was created.
 */
export function productCreated(
  queryClient: QueryClient,
  organizationId: number
): void {
  queryClient.invalidateQueries({
    queryKey: getOrganizationProductsQueryKey({ path: { organizationId } }),
  });
}

/**
 * Seed the cache with an updated product and refresh the list it is shown in.
 */
export function productUpdated(
  queryClient: QueryClient,
  product: Product
): void {
  queryClient.setQueryData(
    getProductQueryKey({ path: { productId: product.id } }),
    product
  );
  productCreated(queryClient, product.organizationId);
}

/**
 * Refresh the products of an organization after one was deleted, and mark the deleted product stale
 * without refetching it.
 */
export function productDeleted(
  queryClient: QueryClient,
  product: Product
): void {
  productCreated(queryClient, product.organizationId);
  queryClient.invalidateQueries({
    queryKey: getProductQueryKey({ path: { productId: product.id } }),
    refetchType: 'none',
  });
}

/**
 * Refresh the repositories of a product after one was created.
 */
export function repositoryCreated(
  queryClient: QueryClient,
  productId: number
): void {
  queryClient.invalidateQueries({
    queryKey: getProductRepositoriesQueryKey({ path: { productId } }),
  });
}

/**
 * Seed the cache with an updated repository and refresh the list it is shown in.
 */
export function repositoryUpdated(
  queryClient: QueryClient,
  repository: Repository
): void {
  queryClient.setQueryData(
    getRepositoryQueryKey({ path: { repositoryId: repository.id } }),
    repository
  );
  repositoryCreated(queryClient, repository.productId);
}

/**
 * Refresh both products a repository moved between, and the permissions it inherits.
 *
 * `repository` carries the destination `productId`, so the product it came from has to be passed
 * separately. The move can also change the permissions the repository inherits from its parents.
 * Those are read per repository id, which the move leaves unchanged, so the entry is invalidated
 * explicitly — without refetching, as the destination route fetches it while loading anyway.
 */
export function repositoryMoved(
  queryClient: QueryClient,
  repository: Repository,
  previousProductId: number
): void {
  repositoryUpdated(queryClient, repository);
  repositoryCreated(queryClient, previousProductId);
  queryClient.invalidateQueries({
    queryKey: getUserInfoQueryKey({ query: { repositoryId: repository.id } }),
    exact: true,
    refetchType: 'none',
  });
}

/**
 * Refresh the repositories of a product after one was deleted, and mark the deleted repository
 * stale without refetching it.
 */
export function repositoryDeleted(
  queryClient: QueryClient,
  repository: Repository
): void {
  repositoryCreated(queryClient, repository.productId);
  queryClient.invalidateQueries({
    queryKey: getRepositoryQueryKey({ path: { repositoryId: repository.id } }),
    refetchType: 'none',
  });
}

/**
 * Refresh the runs of a repository after one was created.
 *
 * This also covers the latest run shown on the repository page, which is read from the same list.
 */
export function runCreated(
  queryClient: QueryClient,
  repositoryId: number
): void {
  queryClient.invalidateQueries({
    queryKey: getRepositoryRunsQueryKey({ path: { repositoryId } }),
  });
}

/**
 * Refresh the runs of a repository after one was deleted, and mark the deleted run stale without
 * refetching it.
 *
 * A run is addressed by its repository and its index within that repository, not by an id of its
 * own, so both are needed to reach its detail entry.
 */
export function runDeleted(
  queryClient: QueryClient,
  repositoryId: number,
  runIndex: number
): void {
  runCreated(queryClient, repositoryId);
  queryClient.invalidateQueries({
    queryKey: getRepositoryRunQueryKey({
      path: { repositoryId, ortRunIndex: runIndex },
    }),
    refetchType: 'none',
  });
}
