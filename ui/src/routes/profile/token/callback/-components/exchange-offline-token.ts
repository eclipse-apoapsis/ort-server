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

import type { OidcClient } from 'oidc-client-ts';

/**
 * Exchanges the authorization code in the callback `url` for an offline token
 * and returns it. Rejects if the exchange fails or returns no offline token.
 */
export const exchangeOfflineToken = async (
  oidcClient: OidcClient,
  url: string
): Promise<string> => {
  const response = await oidcClient.processSigninResponse(url);
  const refreshToken = response.refresh_token;

  if (!refreshToken) {
    throw new Error(
      'No offline token was returned. Ensure the client allows offline access.'
    );
  }

  return refreshToken;
};
