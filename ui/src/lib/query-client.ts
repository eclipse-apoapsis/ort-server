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

import { QueryCache, QueryClient } from '@tanstack/react-query';
import { AxiosError } from 'axios';

import { authRef } from '@/hooks/use-user';

export const queryClient = new QueryClient({
  queryCache: new QueryCache({
    onError: async (error) => {
      if (error instanceof AxiosError && error.status === 401) {
        try {
          /*
           * Attempt to refresh the user session silently.
           * Add a small delay to mitigate potential transient issues.
           * The timeout can be small, as this block is only reached
           * when the original request has already been retried.
           */
          setTimeout(async () => {
            const user = await authRef.current?.signinSilent();

            if (!user) {
              // eslint-disable-next-line no-console
              console.warn(
                'Silent sign-in returned null — treating as unauthenticated'
              );
              // Redirect to login
              authRef.current?.signinRedirect({
                redirect_uri: window.location.href,
              });
            }
          }, 2000);
        } catch (error) {
          // eslint-disable-next-line no-console
          console.error('Silent sign-in failed: ', error);
          // Redirect to login
          authRef.current?.signinRedirect({
            redirect_uri: window.location.href,
          });
        }
      }
    },
  }),
});
