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

import { createRouter, RouterProvider } from '@tanstack/react-router';
import { useEffect, useState } from 'react';
import { hasAuthParams } from 'react-oidc-context';

import { LoadingIndicator } from '@/components/loading-indicator';
import { config } from '@/config';
import { OpenAPI } from './api/requests/index.ts';
import { useUser } from './hooks/use-user.ts';
import { queryClient } from './lib/query-client.ts';
import { routeTree } from './routeTree.gen';

export interface RouterContext {
  queryClient: typeof queryClient;
  breadcrumbs: {
    organization: string | undefined;
    product: string | undefined;
    repo: string | undefined;
    run: string | undefined;
  };
  auth: ReturnType<typeof useUser>;
}

// Create a new router instance
const router = createRouter({
  routeTree,
  basepath: config.BASEPATH,
  context: {
    queryClient,
    breadcrumbs: {
      organization: undefined,
      product: undefined,
      repo: undefined,
      run: undefined,
    },
    auth: undefined!,
  },
  defaultPreload: 'intent',
});

// Register the router instance for type safety
declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router;
  }
}
export const App = () => {
  const auth = useUser();
  const [hasTriedSignin, setHasTriedSignin] = useState(false);
  const [tokenIsSet, setTokenIsSet] = useState(false);

  // Automatically sign-in
  useEffect(() => {
    if (
      !hasAuthParams() &&
      !auth.isAuthenticated &&
      !auth.activeNavigator &&
      !auth.isLoading &&
      !hasTriedSignin
    ) {
      auth.signinRedirect({
        redirect_uri: window.location.href,
      });
      setHasTriedSignin(true);
    }
  }, [auth, hasTriedSignin]);

  useEffect(() => {
    if (auth.user?.access_token) {
      OpenAPI.TOKEN = auth.user.access_token;
      setTokenIsSet(true);
    } else {
      setTokenIsSet(false);
    }
  }, [auth.user]);

  // Handle errors for the silent renew process
  useEffect(() => {
    const nonRetryableErrors = [
      'token is not active',
      'session not active',
      'no matching state',
      'login required',
      'session expired',
      'session not found',
    ];

    const handleSilentRenewError = async (error: Error) => {
      if (
        !nonRetryableErrors.some((msg) =>
          error.message.toLowerCase().includes(msg)
        )
      ) {
        // Retry silent signin after 5 seconds to mitigate transient issues
        setTimeout(() => {
          auth.refreshUser();
        }, 5000);
      } else {
        auth.signinRedirect({ redirect_uri: window.location.href });
      }
    };

    // Register the silent renew error handler
    auth.events.addSilentRenewError(handleSilentRenewError);

    return () => {
      // Unregister the silent renew error handler on component unmount
      auth.events.removeSilentRenewError(handleSilentRenewError);
    };
  }, [auth]);

  if (auth.isLoading) {
    return <LoadingIndicator />;
  }

  if (!auth.isAuthenticated) {
    return <div>Unable to log in</div>;
  }

  if (!tokenIsSet) {
    return <div>Token is not set</div>;
  }

  return <RouterProvider router={router} context={{ auth }} />;
};

export default App;
