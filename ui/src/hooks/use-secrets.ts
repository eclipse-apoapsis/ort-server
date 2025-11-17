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

import { Secret } from '@/api';
import {
  getOrganizationSecretsOptions,
  getProductSecretsOptions,
  getRepositorySecretsOptions,
} from '@/api/@tanstack/react-query.gen';
import { ALL_ITEMS } from '@/lib/constants';
import {
  OrganizationPermissions,
  ProductPermissions,
  RepositoryPermissions,
} from '@/lib/permissions.ts';

export type SecretWithHierarchy = Secret & {
  hierarchy: 'organization' | 'product' | 'repository';
};

type UseSecretsParams = {
  orgId?: string;
  productId?: string;
  repositoryId?: string;
  permissions: {
    organization: OrganizationPermissions | undefined;
    product: ProductPermissions | undefined;
    repository: RepositoryPermissions | undefined;
  };
};

export function useSecrets({
  orgId,
  productId,
  repositoryId,
  permissions,
}: UseSecretsParams) {
  const secrets = useQueries({
    queries: [
      {
        ...getOrganizationSecretsOptions({
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
        ...getProductSecretsOptions({
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
        ...getRepositorySecretsOptions({
          path: {
            repositoryId: Number.parseInt(repositoryId || ''),
          },
          query: {
            limit: ALL_ITEMS,
          },
        }),
        enabled: permissions.repository?.includes('READ'),
      },
    ],
    combine: (results) => {
      const [orgSecrets, productSecrets, repoSecrets] = results;
      // Combine all secrets into an array of objects.
      // Each object contains the name of the secret and to which hierarchy
      // level it belongs (organization, product, repository).
      return [
        ...(orgSecrets.data?.data?.map((secret) => ({
          ...secret,
          hierarchy: 'organization',
        })) ?? []),
        ...(productSecrets.data?.data?.map((secret) => ({
          ...secret,
          hierarchy: 'product',
        })) ?? []),
        ...(repoSecrets.data?.data?.map((secret) => ({
          ...secret,
          hierarchy: 'repository',
        })) ?? []),
      ];
    },
  }) as SecretWithHierarchy[];

  return secrets;
}
