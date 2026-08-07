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
  QueryClient,
  QueryObserver,
  type QueryKey,
} from '@tanstack/react-query';
import { beforeEach, expect, it, vi } from 'vitest';

import type { Organization, Product, Repository } from '@/api';
import {
  getOrganizationOptions,
  getOrganizationProductsInfiniteOptions,
  getOrganizationProductsOptions,
  getOrganizationsInfiniteOptions,
  getOrganizationsOptions,
  getProductOptions,
  getProductRepositoriesInfiniteOptions,
  getProductRepositoriesOptions,
  getRepositoryOptions,
  getRepositoryRunOptions,
  getRepositoryRunsInfiniteOptions,
  getRepositoryRunsOptions,
  getUserInfoOptions,
} from '@/api/@tanstack/react-query.gen';
import { DROPDOWN_PAGE_SIZE } from '@/lib/constants';
import {
  organizationCreated,
  organizationDeleted,
  organizationUpdated,
  productCreated,
  productDeleted,
  productUpdated,
  repositoryCreated,
  repositoryDeleted,
  repositoryMoved,
  repositoryUpdated,
  runCreated,
  runDeleted,
} from '@/lib/entity-cache';

const ORG_ID = 1;
const OTHER_ORG_ID = 2;
const PRODUCT_ID = 10;
const OTHER_PRODUCT_ID = 20;
const REPOSITORY_ID = 100;
const OTHER_REPOSITORY_ID = 200;
const RUN_INDEX = 3;

const organization: Organization = { id: ORG_ID, name: 'renamed org' };
const product: Product = {
  id: PRODUCT_ID,
  name: 'renamed product',
  organizationId: ORG_ID,
};
const repository: Repository = {
  id: REPOSITORY_ID,
  url: 'https://example.com/repo.git',
  type: 'GIT',
  productId: PRODUCT_ID,
  organizationId: ORG_ID,
};

/**
 * The three shapes every list endpoint of the hierarchy is read under: the `limit: 1` count query
 * behind a breadcrumb chevron, a paged query of a table, and the infinite query of a dropdown.
 *
 * The keys come from the generated options rather than being written by hand, so that a change in
 * the generator, or in how the query cache matches keys partially, fails here instead of in the
 * browser.
 */
type ListKeys = {
  count: QueryKey;
  paged: QueryKey;
  infinite: QueryKey;
};

const organizationListKeys = (): ListKeys => ({
  count: getOrganizationsOptions({ query: { limit: 1 } }).queryKey,
  paged: getOrganizationsOptions({ query: { limit: 25, offset: 25 } }).queryKey,
  infinite: getOrganizationsInfiniteOptions({
    query: { limit: DROPDOWN_PAGE_SIZE, filter: 'ac' },
  }).queryKey,
});

const productListKeys = (organizationId: number): ListKeys => ({
  count: getOrganizationProductsOptions({
    path: { organizationId },
    query: { limit: 1 },
  }).queryKey,
  paged: getOrganizationProductsOptions({
    path: { organizationId },
    query: { limit: 25, offset: 25 },
  }).queryKey,
  infinite: getOrganizationProductsInfiniteOptions({
    path: { organizationId },
    query: { limit: DROPDOWN_PAGE_SIZE, filter: 'ac' },
  }).queryKey,
});

const repositoryListKeys = (productId: number): ListKeys => ({
  count: getProductRepositoriesOptions({
    path: { productId },
    query: { limit: 1 },
  }).queryKey,
  paged: getProductRepositoriesOptions({
    path: { productId },
    query: { limit: 25, offset: 25 },
  }).queryKey,
  infinite: getProductRepositoriesInfiniteOptions({
    path: { productId },
    query: { limit: DROPDOWN_PAGE_SIZE, filter: 'ac' },
  }).queryKey,
});

const runListKeys = (repositoryId: number): ListKeys => ({
  count: getRepositoryRunsOptions({
    path: { repositoryId },
    query: { limit: 1 },
  }).queryKey,
  paged: getRepositoryRunsOptions({
    path: { repositoryId },
    query: { limit: 25, offset: 25 },
  }).queryKey,
  infinite: getRepositoryRunsInfiniteOptions({
    path: { repositoryId },
    query: { limit: DROPDOWN_PAGE_SIZE, sort: '-index' },
  }).queryKey,
});

const organizationKey = (organizationId: number) =>
  getOrganizationOptions({ path: { organizationId } }).queryKey;
const productKey = (productId: number) =>
  getProductOptions({ path: { productId } }).queryKey;
const repositoryKey = (repositoryId: number) =>
  getRepositoryOptions({ path: { repositoryId } }).queryKey;
const runKey = (repositoryId: number, ortRunIndex: number) =>
  getRepositoryRunOptions({ path: { repositoryId, ortRunIndex } }).queryKey;
const permissionsKey = (repositoryId: number) =>
  getUserInfoOptions({ query: { repositoryId } }).queryKey;

let queryClient: QueryClient;

/** Put something in the cache under a key, so that there is a query to invalidate. */
const seed = (...keys: QueryKey[]) => {
  for (const key of keys) queryClient.setQueryData(key, { seeded: true });
};

const isInvalidated = (key: QueryKey) =>
  queryClient.getQueryState(key)?.isInvalidated;

const listInvalidations = ({ count, paged, infinite }: ListKeys) => ({
  count: isInvalidated(count),
  paged: isInvalidated(paged),
  infinite: isInvalidated(infinite),
});

const allInvalidated = { count: true, paged: true, infinite: true };
const noneInvalidated = { count: false, paged: false, infinite: false };

beforeEach(() => {
  queryClient = new QueryClient();
});

it.each([
  {
    name: 'organizationCreated',
    own: organizationListKeys,
    other: undefined,
    run: () => organizationCreated(queryClient),
  },
  {
    name: 'productCreated',
    own: () => productListKeys(ORG_ID),
    other: () => productListKeys(OTHER_ORG_ID),
    run: () => productCreated(queryClient, ORG_ID),
  },
  {
    name: 'repositoryCreated',
    own: () => repositoryListKeys(PRODUCT_ID),
    other: () => repositoryListKeys(OTHER_PRODUCT_ID),
    run: () => repositoryCreated(queryClient, PRODUCT_ID),
  },
  {
    name: 'runCreated',
    own: () => runListKeys(REPOSITORY_ID),
    other: () => runListKeys(OTHER_REPOSITORY_ID),
    run: () => runCreated(queryClient, REPOSITORY_ID),
  },
])(
  '$name invalidates the count, paged and infinite forms of its parent list',
  ({ own, other, run }) => {
    const ownKeys = own();
    seed(ownKeys.count, ownKeys.paged, ownKeys.infinite);

    const otherKeys = other?.();
    if (otherKeys) {
      seed(otherKeys.count, otherKeys.paged, otherKeys.infinite);
    }

    run();

    expect(listInvalidations(ownKeys)).toEqual(allInvalidated);

    // A list of another parent is a different `path`, and must be left alone.
    if (otherKeys) {
      expect(listInvalidations(otherKeys)).toEqual(noneInvalidated);
    }
  }
);

it.each([
  {
    name: 'organizationCreated',
    run: () => organizationCreated(queryClient),
  },
  {
    name: 'productCreated',
    run: () => productCreated(queryClient, ORG_ID),
  },
  {
    name: 'repositoryCreated',
    run: () => repositoryCreated(queryClient, PRODUCT_ID),
  },
  {
    name: 'runCreated',
    run: () => runCreated(queryClient, REPOSITORY_ID),
  },
])(
  '$name leaves the detail and list queries of other endpoints alone',
  ({ run }) => {
    const untouched = [
      organizationKey(ORG_ID),
      productKey(PRODUCT_ID),
      repositoryKey(REPOSITORY_ID),
      runKey(REPOSITORY_ID, RUN_INDEX),
      permissionsKey(REPOSITORY_ID),
    ];
    seed(...untouched);

    run();

    // `_id` is matched as a whole string, so neighbouring endpoints such as `getOrganization` and
    // `getOrganizationProducts` never match each other.
    expect(untouched.map(isInvalidated)).toEqual(untouched.map(() => false));
  }
);

it.each([
  {
    name: 'organizationUpdated',
    detail: () => organizationKey(ORG_ID),
    list: organizationListKeys,
    entity: organization,
    run: () => organizationUpdated(queryClient, organization),
  },
  {
    name: 'productUpdated',
    detail: () => productKey(PRODUCT_ID),
    list: () => productListKeys(ORG_ID),
    entity: product,
    run: () => productUpdated(queryClient, product),
  },
  {
    name: 'repositoryUpdated',
    detail: () => repositoryKey(REPOSITORY_ID),
    list: () => repositoryListKeys(PRODUCT_ID),
    entity: repository,
    run: () => repositoryUpdated(queryClient, repository),
  },
])(
  '$name writes the response to the detail key and invalidates the parent list',
  ({ detail, list, entity, run }) => {
    const detailKey = detail();
    const listKeys = list();
    seed(detailKey, listKeys.count, listKeys.paged, listKeys.infinite);

    run();

    // The route loader reads the entity through `ensureQueryData`, which hands back whatever is
    // cached, so seeding it here is what lets the breadcrumb and the document title pick up the
    // new name without a request.
    expect(queryClient.getQueryData(detailKey)).toEqual(entity);
    expect(listInvalidations(listKeys)).toEqual(allInvalidated);
  }
);

it.each([
  {
    name: 'organizationDeleted',
    detail: () => organizationKey(ORG_ID),
    list: organizationListKeys,
    run: () => organizationDeleted(queryClient, ORG_ID),
  },
  {
    name: 'productDeleted',
    detail: () => productKey(PRODUCT_ID),
    list: () => productListKeys(ORG_ID),
    run: () => productDeleted(queryClient, product),
  },
  {
    name: 'repositoryDeleted',
    detail: () => repositoryKey(REPOSITORY_ID),
    list: () => repositoryListKeys(PRODUCT_ID),
    run: () => repositoryDeleted(queryClient, repository),
  },
  {
    name: 'runDeleted',
    detail: () => runKey(REPOSITORY_ID, RUN_INDEX),
    list: () => runListKeys(REPOSITORY_ID),
    run: () => runDeleted(queryClient, REPOSITORY_ID, RUN_INDEX),
  },
])(
  '$name invalidates the parent list and the deleted detail without refetching it',
  async ({ detail, list, run }) => {
    const detailKey = detail();
    const listKeys = list();
    seed(detailKey, listKeys.count, listKeys.paged, listKeys.infinite);

    // The settings pages still observe the entity they delete when the mutation succeeds. An
    // infinite stale time keeps the observer from fetching on its own, so any call of this
    // function would come from the invalidation.
    const queryFn = vi.fn().mockResolvedValue({ seeded: true });
    const observer = new QueryObserver(queryClient, {
      queryKey: detailKey,
      queryFn,
      staleTime: Infinity,
    });
    const unsubscribe = observer.subscribe(() => {});

    run();
    await vi.waitFor(() => expect(isInvalidated(detailKey)).toBe(true));

    expect(listInvalidations(listKeys)).toEqual(allInvalidated);
    // `refetchType: 'none'`: the deleted entity is marked stale, but never requested again from
    // the page that just deleted it.
    expect(queryFn).not.toHaveBeenCalled();
    expect(queryClient.getQueryData(detailKey)).toEqual({ seeded: true });

    unsubscribe();
  }
);

it('repositoryMoved invalidates both products and the permissions of the moved repository', () => {
  const movedRepository: Repository = {
    ...repository,
    productId: OTHER_PRODUCT_ID,
  };
  const source = repositoryListKeys(PRODUCT_ID);
  const destination = repositoryListKeys(OTHER_PRODUCT_ID);
  const detailKey = repositoryKey(REPOSITORY_ID);
  const otherPermissions = permissionsKey(OTHER_REPOSITORY_ID);

  seed(
    detailKey,
    permissionsKey(REPOSITORY_ID),
    otherPermissions,
    source.count,
    source.paged,
    source.infinite,
    destination.count,
    destination.paged,
    destination.infinite
  );

  repositoryMoved(queryClient, movedRepository, PRODUCT_ID);

  expect(listInvalidations(source)).toEqual(allInvalidated);
  expect(listInvalidations(destination)).toEqual(allInvalidated);
  expect(queryClient.getQueryData(detailKey)).toEqual(movedRepository);

  // The permissions a repository inherits change with its parents, but are keyed by the repository
  // id, which the move leaves unchanged. Only this repository's entry may be invalidated.
  expect(isInvalidated(permissionsKey(REPOSITORY_ID))).toBe(true);
  expect(isInvalidated(otherPermissions)).toBe(false);
});
