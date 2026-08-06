/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import { InfrastructureService } from '@/api';
import {
  getOrganizationInfrastructureServicesInfiniteOptions,
  getProductInfrastructureServicesInfiniteOptions,
  getRepositoryInfrastructureServicesInfiniteOptions,
} from '@/api/@tanstack/react-query.gen';
import { useInfiniteList } from '@/hooks/use-infinite-list';
import { DROPDOWN_PAGE_SIZE } from '@/lib/constants';
import { combineInfiniteLists, InfiniteList } from '@/lib/infinite-list';
import {
  OrganizationPermissions,
  ProductPermissions,
  RepositoryPermissions,
} from '@/lib/permissions.ts';

export type InfrastructureServiceWithHierarchy = InfrastructureService & {
  hierarchy: 'organization' | 'product' | 'repository';
};

type UseInfrastructureServicesParams = {
  orgId?: string;
  productId?: string;
  repoId?: string;
  permissions: {
    organization: OrganizationPermissions | undefined;
    product: ProductPermissions | undefined;
    repository: RepositoryPermissions | undefined;
  };
  /** Whether to read anything at all, so that a closed dropdown costs no request. */
  enabled?: boolean;
};

// Infrastructure services are not expected to change while a form is open, and closing a dropdown
// only turns its queries off. Without a stale time, opening it again would read every page that was
// loaded before.
const staleTime = 2 * 60 * 60 * 1000;

/**
 * Read the infrastructure services a user can pick from, a page at a time, as one list of the
 * organization, product and repository services, in that order. Which levels it reads depends on
 * the ids that are given and on what the user is allowed to read.
 */
export const useInfrastructureServices = ({
  orgId,
  productId,
  repoId,
  permissions,
  enabled = true,
}: UseInfrastructureServicesParams): InfiniteList<InfrastructureServiceWithHierarchy> => {
  const readsOrganization =
    enabled && !!orgId && !!permissions.organization?.includes('READ');
  const readsProduct =
    enabled && !!productId && !!permissions.product?.includes('READ');
  const readsRepository =
    enabled && !!repoId && !!permissions.repository?.includes('READ');

  const organizationServices = useInfiniteList(
    getOrganizationInfrastructureServicesInfiniteOptions({
      path: { organizationId: Number.parseInt(orgId || '') },
      query: { limit: DROPDOWN_PAGE_SIZE },
    }),
    { enabled: readsOrganization, staleTime: staleTime }
  );

  const productServices = useInfiniteList(
    getProductInfrastructureServicesInfiniteOptions({
      path: { productId: Number.parseInt(productId || '') },
      query: { limit: DROPDOWN_PAGE_SIZE },
    }),
    { enabled: readsProduct, staleTime: staleTime }
  );

  const repositoryServices = useInfiniteList(
    getRepositoryInfrastructureServicesInfiniteOptions({
      path: { repositoryId: Number.parseInt(repoId || '') },
      query: { limit: DROPDOWN_PAGE_SIZE },
    }),
    { enabled: readsRepository, staleTime: staleTime }
  );

  // Only the levels that are read are combined, as a query that is turned off never leaves
  // `pending` and would keep the whole list loading.
  return combineInfiniteLists([
    ...(readsOrganization
      ? [withHierarchy(organizationServices, 'organization')]
      : []),
    ...(readsProduct ? [withHierarchy(productServices, 'product')] : []),
    ...(readsRepository
      ? [withHierarchy(repositoryServices, 'repository')]
      : []),
  ]);
};

/**
 * Tag the services of a list with the hierarchy level they belong to.
 */
function withHierarchy(
  list: InfiniteList<InfrastructureService>,
  hierarchy: InfrastructureServiceWithHierarchy['hierarchy']
): InfiniteList<InfrastructureServiceWithHierarchy> {
  return {
    ...list,
    items: list.items.map((service) => ({ ...service, hierarchy })),
  };
}
