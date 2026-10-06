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

import type { OidcClient, SigninResponse } from 'oidc-client-ts';
import { describe, expect, it, vi } from 'vitest';

import { exchangeOfflineToken } from '@/routes/profile/token/callback/-components/exchange-offline-token';

const CALLBACK_URL = 'https://ort.example.com/profile/token/callback?code=c';

const clientResolvingTo = (response: Partial<SigninResponse>) => {
  const processSigninResponse = vi.fn().mockResolvedValue(response);

  return {
    client: { processSigninResponse } as unknown as OidcClient,
    processSigninResponse,
  };
};

describe('exchangeOfflineToken', () => {
  it('returns the refresh token of the response', async () => {
    const { client, processSigninResponse } = clientResolvingTo({
      refresh_token: 'offline-token',
    });

    await expect(exchangeOfflineToken(client, CALLBACK_URL)).resolves.toBe(
      'offline-token'
    );
    expect(processSigninResponse).toHaveBeenCalledWith(CALLBACK_URL);
  });

  it('rejects when the response has no refresh token', async () => {
    const { client } = clientResolvingTo({});

    await expect(exchangeOfflineToken(client, CALLBACK_URL)).rejects.toThrow(
      'No offline token was returned. Ensure the client allows offline access.'
    );
  });

  it('rejects with the error of a failed exchange', async () => {
    const error = new Error('invalid_grant');
    const client = {
      processSigninResponse: vi.fn().mockRejectedValue(error),
    } as unknown as OidcClient;

    await expect(exchangeOfflineToken(client, CALLBACK_URL)).rejects.toBe(
      error
    );
  });
});
