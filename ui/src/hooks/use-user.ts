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

import { useEffect } from 'react';
import { AuthContextProps, useAuth } from 'react-oidc-context';

import { config } from '@/config';

export const authRef: { current: AuthContextProps | null } = {
  current: null,
};

export const useUser = () => {
  const auth = useAuth();
  const serverClientId = config.serverClientId;
  const userRoles =
    auth?.user?.profile?.resource_access?.[serverClientId]?.roles || [];

  // Check if the user has any of the given roles.
  const hasRole = (roles: string[]) => {
    return roles.some((role) => userRoles.includes(role));
  };

  // Refresh the token silently. This also refreshes the user profile (and the roles).
  const refreshUser = async () => {
    try {
      const user = await auth.signinSilent();

      if (!user) {
        // eslint-disable-next-line no-console
        console.warn(
          'Silent sign-in returned null — treating as unauthenticated'
        );
        await auth.signinRedirect({ redirect_uri: window.location.href });
      }
    } catch (error) {
      // eslint-disable-next-line no-console
      console.error('Silent sign-in failed:', error);
      await auth.signinRedirect({ redirect_uri: window.location.href });
    }
  };

  // Return the logged-in username.
  const username = auth?.user?.profile?.preferred_username;

  // Return end-user's full name, including all name parts.
  const fullName = auth?.user?.profile?.name;

  useEffect(() => {
    // Store the auth context in a ref for use outside of components.
    authRef.current = auth;
  }, [auth]);

  return {
    hasRole,
    username,
    fullName,
    refreshUser,
    ...auth,
  };
};
