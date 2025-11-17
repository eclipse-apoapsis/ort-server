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

import { useQueries } from '@tanstack/react-query';

import { InfrastructureService } from '@/api';
import {
  getOrganizationInfrastructureServicesOptions,
  getProductInfrastructureServicesOptions,
  getRepositoryInfrastructureServicesOptions,
} from '@/api/@tanstack/react-query.gen';
import { ALL_ITEMS } from '@/lib/constants';
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
};

export const useInfrastructureServices = ({
  orgId,
  productId,
  repoId,
  permissions,
}: UseInfrastructureServicesParams) => {
  // Only fetch infrastructure services the user has access to.
  const infrastructureServices = useQueries({
    queries: [
      {
        ...getOrganizationInfrastructureServicesOptions({
          path: {
            organizationId: Number.parseInt(orgId || ''),
          },
          query: {
            limit: ALL_ITEMS,
          },
        }),
        enabled: permissions.organization?.includes('READ'),
      },
      {
        ...getProductInfrastructureServicesOptions({
          path: {
            productId: Number.parseInt(productId || ''),
          },
          query: {
            limit: ALL_ITEMS,
          },
        }),
        enabled: permissions.product?.includes('READ'),
      },
      {
        ...getRepositoryInfrastructureServicesOptions({
          path: {
            repositoryId: Number.parseInt(repoId || ''),
          },
          query: {
            limit: ALL_ITEMS,
          },
        }),
        enabled: permissions.repository?.includes('READ'),
      },
    ],
    combine: (results) => {
      const [orgServices, productServices, repoServices] = results;
      // Combine all infrastructure services into an array of objects.
      // Each object contains the name of the service and to which hierarchy
      // level it belongs (organization, product, repository).
      return [
        ...(orgServices.data?.data?.map((service) => ({
          ...service,
          hierarchy: 'organization',
        })) ?? []),
        ...(productServices.data?.data?.map((service) => ({
          ...service,
          hierarchy: 'product',
        })) ?? []),
        ...(repoServices.data?.data?.map((service) => ({
          ...service,
          hierarchy: 'repository',
        })) ?? []),
      ];
    },
  }) as InfrastructureServiceWithHierarchy[];

  return infrastructureServices;
};
