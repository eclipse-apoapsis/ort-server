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

import { useQuery } from '@tanstack/react-query';

import type {
  OrganizationPermission,
  ProductPermission,
  RepositoryPermission,
} from '@/api';
import {
  getSuperuserOptions,
  getUserInfoOptions,
} from '@/api/@tanstack/react-query.gen';

const PERMISSIONS_STALE_TIME = 60000;

type PermissionResult = {
  isAllowed: boolean | undefined;
  isPending: boolean;
  error: unknown;
};

type ScopedPermissionRequest =
  | {
      scopeType: 'organization';
      scope: { organizationId: number };
      requiredPermission: OrganizationPermission;
    }
  | {
      scopeType: 'product';
      scope: { productId: number };
      requiredPermission: ProductPermission;
    }
  | {
      scopeType: 'repository';
      scope: { repositoryId: number };
      requiredPermission: RepositoryPermission;
    };

export const useIsSuperuser = (): {
  isSuperuser: boolean | undefined;
  isPending: boolean;
  error: unknown;
} => {
  const {
    data: isSuperuser,
    isPending,
    error,
  } = useQuery({
    ...getSuperuserOptions(),
    staleTime: PERMISSIONS_STALE_TIME,
  });

  return {
    isSuperuser,
    isPending,
    error,
  };
};

const useEntityPermission = (
  request: ScopedPermissionRequest
): PermissionResult => {
  const {
    data: userInfo,
    isPending,
    error,
  } = useQuery({
    ...getUserInfoOptions({
      query: request.scope,
    }),
    staleTime: PERMISSIONS_STALE_TIME,
  });

  const isAllowed =
    userInfo?.isSuperuser ||
    (request.scopeType === 'organization'
      ? userInfo?.organizationPermissions?.includes(request.requiredPermission)
      : request.scopeType === 'product'
        ? userInfo?.productPermissions?.includes(request.requiredPermission)
        : userInfo?.repositoryPermissions?.includes(
            request.requiredPermission
          ));

  return {
    isAllowed,
    isPending,
    error,
  };
};

export const useOrganizationPermission = (
  organizationId: number,
  requiredPermission: OrganizationPermission
): PermissionResult =>
  useEntityPermission({
    scopeType: 'organization',
    scope: {
      organizationId,
    },
    requiredPermission,
  });

export const useProductPermission = (
  productId: number,
  requiredPermission: ProductPermission
): PermissionResult =>
  useEntityPermission({
    scopeType: 'product',
    scope: {
      productId,
    },
    requiredPermission,
  });

export const useRepositoryPermission = (
  repositoryId: number,
  requiredPermission: RepositoryPermission
): PermissionResult =>
  useEntityPermission({
    scopeType: 'repository',
    scope: {
      repositoryId,
    },
    requiredPermission,
  });
