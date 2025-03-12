/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import { useAuth } from 'react-oidc-context';

import { config } from '@/config';

export const useUser = () => {
  const auth = useAuth();
  const serverClientId = config.serverClientId;
  const userRoles =
    auth?.user?.profile?.resource_access?.[serverClientId]?.roles || [];

  // Check if the user has any of the given roles.
  const hasRole = (roles: string[]) => {
    return roles.some((role) => userRoles.includes(role));
  };

  // Return the logged-in username.
  const username = auth?.user?.profile?.preferred_username;

  // Return end-user's full name, including all name parts.
  const fullName = auth?.user?.profile?.name;

  return {
    hasRole,
    username,
    fullName,
    ...auth,
  };
};
