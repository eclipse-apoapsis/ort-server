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

import type { ReactNode } from 'react';

import { PermissionGuard } from '@/components/authorization/permission-guard';
import {
  useOrganizationPermission,
  useProductPermission,
  useRepositoryPermission,
} from '@/hooks/use-authorization';

interface RequirePermissionProps {
  children: ReactNode;
  permission: string;
}

interface RequireOrganizationPermissionProps extends RequirePermissionProps {
  organizationId: number;
}

interface RequireProductPermissionProps extends RequirePermissionProps {
  productId: number;
}

interface RequireRepositoryPermissionProps extends RequirePermissionProps {
  repositoryId: number;
}

export const RequireOrganizationPermission = ({
  organizationId,
  children,
  permission,
}: RequireOrganizationPermissionProps) => {
  const { isAllowed, isPending, error } = useOrganizationPermission(
    organizationId,
    permission
  );

  return (
    <PermissionGuard isAllowed={isAllowed} isLoading={isPending} error={error}>
      {children}
    </PermissionGuard>
  );
};

export const RequireProductPermission = ({
  productId,
  children,
  permission,
}: RequireProductPermissionProps) => {
  const { isAllowed, isPending, error } = useProductPermission(
    productId,
    permission
  );

  return (
    <PermissionGuard isAllowed={isAllowed} isLoading={isPending} error={error}>
      {children}
    </PermissionGuard>
  );
};

export const RequireRepositoryPermission = ({
  repositoryId,
  children,
  permission,
}: RequireRepositoryPermissionProps) => {
  const { isAllowed, isPending, error } = useRepositoryPermission(
    repositoryId,
    permission
  );

  return (
    <PermissionGuard isAllowed={isAllowed} isLoading={isPending} error={error}>
      {children}
    </PermissionGuard>
  );
};
