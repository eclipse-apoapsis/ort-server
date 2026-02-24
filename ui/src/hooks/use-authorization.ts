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
  scope:
    | { organizationId: number }
    | { productId: number }
    | { repositoryId: number },
  requiredPermission: string
): PermissionResult => {
  const {
    data: userInfo,
    isPending,
    error,
  } = useQuery({
    ...getUserInfoOptions({
      query: scope,
    }),
    staleTime: PERMISSIONS_STALE_TIME,
  });

  const isAllowed =
    userInfo?.isSuperuser ||
    userInfo?.permissions?.includes(requiredPermission);

  return {
    isAllowed,
    isPending,
    error,
  };
};

export const useOrganizationPermission = (
  organizationId: number,
  requiredPermission: string
): PermissionResult =>
  useEntityPermission(
    {
      organizationId,
    },
    requiredPermission
  );

export const useProductPermission = (
  productId: number,
  requiredPermission: string
): PermissionResult =>
  useEntityPermission(
    {
      productId,
    },
    requiredPermission
  );

export const useRepositoryPermission = (
  repositoryId: number,
  requiredPermission: string
): PermissionResult =>
  useEntityPermission(
    {
      repositoryId,
    },
    requiredPermission
  );
