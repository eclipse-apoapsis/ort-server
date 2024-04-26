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

import { routeTree } from './routeTree.gen';
import { queryClient } from './lib/query-client.ts';
import { RouterProvider, createRouter } from '@tanstack/react-router';
import { OpenAPI } from './api/requests/index.ts';
import { useEffect, useState } from 'react';
import { useAuth, hasAuthParams } from 'react-oidc-context';

export interface RouterContext {
  queryClient: typeof queryClient;
  breadcrumbs: {
    organization: string | undefined;
    product: string | undefined;
  };
  auth: ReturnType<typeof useAuth>;
}

// Create a new router instance
const router = createRouter({
  routeTree,
  context: {
    queryClient,
    breadcrumbs: { organization: undefined, product: undefined },
    auth: undefined!,
  },
});

// Register the router instance for type safety
declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router;
  }
}
export const App = () => {
  const auth = useAuth();
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
      auth.signinRedirect();
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

  if (auth.isLoading) {
    return <div>Loading...</div>;
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
