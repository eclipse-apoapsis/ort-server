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

import { QueryClient } from '@tanstack/react-query';

import type {
  OrganizationPermission,
  ProductPermission,
  RepositoryPermission,
  UserInfo,
} from '@/api';
import {
  getSuperuserOptions,
  getUserInfoOptions,
} from '@/api/@tanstack/react-query.gen.ts';

class BasePermissions<TPermission extends string> {
  constructor(
    public readonly isSuperuser: boolean,
    public readonly permissions: TPermission[]
  ) {}

  includes(requiredPermission: TPermission): boolean {
    return this.isSuperuser || this.permissions.includes(requiredPermission);
  }
}

export class OrganizationPermissions extends BasePermissions<OrganizationPermission> {
  constructor(
    public readonly organizationId: number,
    isSuperuser: boolean,
    permissions: OrganizationPermission[]
  ) {
    super(isSuperuser, permissions);
  }
}

export class ProductPermissions extends BasePermissions<ProductPermission> {
  constructor(
    public readonly productId: number,
    isSuperuser: boolean,
    permissions: ProductPermission[]
  ) {
    super(isSuperuser, permissions);
  }
}

export class RepositoryPermissions extends BasePermissions<RepositoryPermission> {
  constructor(
    public readonly repositoryId: number,
    isSuperuser: boolean,
    permissions: RepositoryPermission[]
  ) {
    super(isSuperuser, permissions);
  }
}

type PermissionEntity = 'organizationId' | 'productId' | 'repositoryId';
type PermissionConfig<TPermission extends string> = {
  entityType: PermissionEntity;
  permissionsForScope: (userInfo: UserInfo) => Array<TPermission> | undefined;
};

async function fetchPermissions<
  TPermission extends string,
  T extends BasePermissions<TPermission>,
>(
  queryClient: QueryClient,
  entityId: number,
  config: PermissionConfig<TPermission>,
  PermissionsClass: new (
    entityId: number,
    isSuperuser: boolean,
    permissions: TPermission[]
  ) => T
): Promise<T> {
  const isSuperuser = await queryClient.fetchQuery({
    ...getSuperuserOptions(),
    staleTime: 60000,
  });

  if (isSuperuser) {
    return new PermissionsClass(entityId, true, []);
  }

  const userInfo = await queryClient.fetchQuery({
    ...getUserInfoOptions({
      query: {
        [config.entityType]: entityId,
      },
    }),
    staleTime: 60000,
  });

  return new PermissionsClass(
    entityId,
    false,
    config.permissionsForScope(userInfo) ?? []
  );
}

export const fetchOrganizationPermissions = (
  queryClient: QueryClient,
  organizationId: number
) =>
  fetchPermissions(
    queryClient,
    organizationId,
    {
      entityType: 'organizationId',
      permissionsForScope: (userInfo) => userInfo.organizationPermissions,
    },
    OrganizationPermissions
  );

export const fetchProductPermissions = (
  queryClient: QueryClient,
  productId: number
) =>
  fetchPermissions(
    queryClient,
    productId,
    {
      entityType: 'productId',
      permissionsForScope: (userInfo) => userInfo.productPermissions,
    },
    ProductPermissions
  );

export const fetchRepositoryPermissions = (
  queryClient: QueryClient,
  repositoryId: number
) =>
  fetchPermissions(
    queryClient,
    repositoryId,
    {
      entityType: 'repositoryId',
      permissionsForScope: (userInfo) => userInfo.repositoryPermissions,
    },
    RepositoryPermissions
  );
