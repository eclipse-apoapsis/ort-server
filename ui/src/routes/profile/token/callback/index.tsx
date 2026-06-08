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

import { useQuery } from '@tanstack/react-query';
import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { OidcClient } from 'oidc-client-ts';
import { useCallback, useEffect, useState } from 'react';

import { getCliOidcConfigOptions } from '@/api/@tanstack/react-query.gen';
import { CopyToClipboard } from '@/components/copy-to-clipboard';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';
import { Textarea } from '@/components/ui/textarea';
import { config } from '@/config';
import { TOKEN_FLOW_MARKER_KEY } from '@/helpers/token-flow';

const CLI_SCOPE = 'openid offline_access';

const getTokenCallbackRedirectUri = () => {
  const basePath =
    config.BASEPATH === '/'
      ? ''
      : `/${config.BASEPATH.replace(/^\/+|\/+$/g, '')}`;

  return `${window.location.origin}${basePath}/profile/token/callback`;
};

const clearAuthQueryParams = () => {
  const url = new URL(window.location.href);
  url.searchParams.delete('code');
  url.searchParams.delete('state');
  url.searchParams.delete('session_state');
  url.searchParams.delete('iss');
  window.history.replaceState({}, document.title, url.pathname + url.hash);
};

const TokenCallbackPage = () => {
  const navigate = useNavigate();

  const [offlineToken, setOfflineToken] = useState<string | undefined>();
  const [errorMessage, setErrorMessage] = useState<string | undefined>();
  const [isProcessingCallback, setIsProcessingCallback] = useState(true);

  const { data: cliOidcConfig } = useQuery(getCliOidcConfigOptions());
  const cliClientId = cliOidcConfig?.clientId;

  const createOidcClient = useCallback(() => {
    if (!cliClientId) return undefined;
    return new OidcClient({
      authority: `${config.authBaseUrl}/realms/${config.realm}`,
      client_id: cliClientId,
      redirect_uri: getTokenCallbackRedirectUri(),
      response_type: 'code',
      scope: CLI_SCOPE,
      loadUserInfo: false,
    });
  }, [cliClientId]);

  useEffect(() => {
    if (!cliClientId || offlineToken) return;

    const searchParams = new URLSearchParams(window.location.search);
    const hasCallbackParams =
      searchParams.has('state') &&
      (searchParams.has('code') || searchParams.has('error'));

    if (!hasCallbackParams) {
      window.sessionStorage.removeItem(TOKEN_FLOW_MARKER_KEY);
      void navigate({ to: '/profile/token', replace: true });
      return;
    }

    let canceled = false;

    const processCallback = async () => {
      setErrorMessage(undefined);
      setIsProcessingCallback(true);

      const oidcClient = createOidcClient();
      if (!oidcClient) {
        setIsProcessingCallback(false);
        setErrorMessage('Unable to initialize OIDC client configuration.');
        return;
      }

      try {
        const response = await oidcClient.processSigninResponse(
          window.location.href
        );
        const refreshToken = response.refresh_token;

        if (!refreshToken) {
          throw new Error(
            'No offline token was returned. Ensure the client allows offline access.'
          );
        }

        if (canceled) return;

        setOfflineToken(refreshToken);
      } catch (error) {
        if (canceled) return;

        setErrorMessage(
          error instanceof Error
            ? error.message
            : 'Failed to exchange the authorization code for an offline token.'
        );
      } finally {
        if (!canceled) {
          setIsProcessingCallback(false);
          window.sessionStorage.removeItem(TOKEN_FLOW_MARKER_KEY);
          clearAuthQueryParams();
        }
      }
    };

    void processCallback();

    return () => {
      canceled = true;
    };
  }, [cliClientId, createOidcClient, navigate, offlineToken]);

  return (
    <div className='mx-auto flex w-full max-w-4xl flex-col gap-6'>
      <Card>
        <CardHeader>
          <CardTitle className='text-3xl font-bold tracking-tight'>
            Authentication Token
          </CardTitle>
          <CardDescription>
            Copy the token now. It will not be shown again.
          </CardDescription>
        </CardHeader>
        <Separator />
        <CardContent className='space-y-4 pt-6'>
          {isProcessingCallback && (
            <p className='text-sm'>Finalizing token generation...</p>
          )}

          {errorMessage && (
            <div className='text-sm text-red-600 dark:text-red-400'>
              {errorMessage}
            </div>
          )}

          {offlineToken && (
            <div className='space-y-2'>
              <p className='text-sm font-semibold'>
                Authentication token (copy now, it will not be shown again):
              </p>
              <div className='flex gap-2'>
                <Textarea readOnly value={offlineToken} className='h-36' />
                <CopyToClipboard copyText={offlineToken} />
              </div>
            </div>
          )}
        </CardContent>
        <Separator />
        <CardFooter className='flex flex-col items-stretch gap-3 sm:flex-row sm:justify-end'>
          <Button
            variant='outline'
            onClick={() => {
              void navigate({ to: '/profile/token' });
            }}
          >
            Back to token page
          </Button>
        </CardFooter>
      </Card>
    </div>
  );
};

export const Route = createFileRoute('/profile/token/callback/')({
  component: TokenCallbackPage,
});
