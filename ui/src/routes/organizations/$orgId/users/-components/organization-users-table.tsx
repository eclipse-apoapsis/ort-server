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

import { getRouteApi } from '@tanstack/react-router';

import {
  getOrganizationUsersOptions,
  getOrganizationUsersQueryKey,
} from '@/api/@tanstack/react-query.gen';
import {
  deleteOrganizationRoleFromUser,
  putOrganizationRoleToUser,
} from '@/api/sdk.gen';
import { UsersTable } from '@/routes/organizations/$orgId/-components/users-table';

const routeApi = getRouteApi('/organizations/$orgId/users');

export const OrganizationUsersTable = () => {
  const params = routeApi.useParams();
  const organizationId = Number.parseInt(params.orgId);

  return (
    <UsersTable
      level='organization'
      usersOptions={(query) =>
        getOrganizationUsersOptions({ path: { organizationId }, query })
      }
      usersQueryKey={getOrganizationUsersQueryKey({ path: { organizationId } })}
      assignRole={(username, role) =>
        putOrganizationRoleToUser({
          path: { organizationId, role },
          body: { username },
          throwOnError: true,
        })
      }
      removeRole={(username, role) =>
        deleteOrganizationRoleFromUser({
          path: { organizationId, role },
          query: { username },
          throwOnError: true,
        })
      }
    />
  );
};
