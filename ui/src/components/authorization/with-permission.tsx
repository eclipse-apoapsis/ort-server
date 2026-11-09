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

import { Outlet, useParams } from '@tanstack/react-router';
import type { ComponentType } from 'react';

import type {
  OrganizationPermission,
  ProductPermission,
  RepositoryPermission,
} from '@/api';
import {
  RequireOrganizationPermission,
  RequireProductPermission,
  RequireRepositoryPermission,
} from '@/components/authorization/require-permission';

/**
 * A route component that shows the page only to users with the permission on the organization
 * of the route. Without a page, it guards the child routes of the route.
 */
export const withOrganizationPermission = (
  permission: OrganizationPermission,
  Page: ComponentType = Outlet
) =>
  function OrganizationPermissionGuard() {
    const { orgId } = useParams({ strict: false });

    return (
      <RequireOrganizationPermission
        organizationId={Number(orgId)}
        permission={permission}
      >
        <Page />
      </RequireOrganizationPermission>
    );
  };

/**
 * A route component that shows the page only to users with the permission on the product of
 * the route. Without a page, it guards the child routes of the route.
 */
export const withProductPermission = (
  permission: ProductPermission,
  Page: ComponentType = Outlet
) =>
  function ProductPermissionGuard() {
    const { productId } = useParams({ strict: false });

    return (
      <RequireProductPermission
        productId={Number(productId)}
        permission={permission}
      >
        <Page />
      </RequireProductPermission>
    );
  };

/**
 * A route component that shows the page only to users with the permission on the repository of
 * the route. Without a page, it guards the child routes of the route.
 */
export const withRepositoryPermission = (
  permission: RepositoryPermission,
  Page: ComponentType = Outlet
) =>
  function RepositoryPermissionGuard() {
    const { repoId } = useParams({ strict: false });

    return (
      <RequireRepositoryPermission
        repositoryId={Number(repoId)}
        permission={permission}
      >
        <Page />
      </RequireRepositoryPermission>
    );
  };
