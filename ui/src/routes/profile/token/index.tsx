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
import { createFileRoute, Link } from '@tanstack/react-router';
import { ExternalLink } from 'lucide-react';
import { OidcClient } from 'oidc-client-ts';
import { useCallback, useState } from 'react';

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
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
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

const TokenPage = () => {
  const [errorMessage, setErrorMessage] = useState<string | undefined>();
  const [isRedirecting, setIsRedirecting] = useState(false);

  const { data: cliOidcConfig, isPending: isCliOidcConfigPending } = useQuery(
    getCliOidcConfigOptions()
  );
  const cliClientId = cliOidcConfig?.clientId;
  const cliLoginCommand = `osc auth login --url=${config.API_URL} --token=<your-token>`;

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

  const startTokenGeneration = async () => {
    const oidcClient = createOidcClient();
    if (!oidcClient) {
      setErrorMessage('Unable to initialize OIDC client configuration.');
      return;
    }

    setErrorMessage(undefined);
    setIsRedirecting(true);
    window.sessionStorage.setItem(TOKEN_FLOW_MARKER_KEY, '1');

    try {
      const request = await oidcClient.createSigninRequest({
        redirect_uri: getTokenCallbackRedirectUri(),
        scope: CLI_SCOPE,
      });

      window.location.assign(request.url);
    } catch (error) {
      setIsRedirecting(false);
      window.sessionStorage.removeItem(TOKEN_FLOW_MARKER_KEY);
      setErrorMessage(
        error instanceof Error
          ? error.message
          : 'Failed to start token generation flow.'
      );
    }
  };

  return (
    <div className='mx-auto flex w-full max-w-4xl flex-col gap-6'>
      <Card>
        <CardHeader>
          <CardTitle className='text-3xl font-bold tracking-tight'>
            Authentication Token
          </CardTitle>
          <CardDescription>
            Generate a token for automation usage.
          </CardDescription>
        </CardHeader>
        <Separator />
        <CardContent className='space-y-4 pt-6'>
          <p className='text-sm'>
            Generate a token for automation, so scripts and tools can
            authenticate without manual login and 2FA.
          </p>

          <div className='space-y-2 text-sm'>
            <p>
              1. Use it to sign in with{' '}
              <a
                href='https://eclipse-apoapsis.github.io/ort-server/docs/user-guide/cli/getting-started'
                target='_blank'
                rel='noopener noreferrer'
                className='underline'
              >
                ORT Server CLI
                <ExternalLink
                  aria-hidden='true'
                  className='ml-1 inline size-3'
                />
                <span className='sr-only'>(opens in a new tab)</span>
              </a>{' '}
              using{' '}
              <span className='inline-flex items-center'>
                <code className='bg-muted rounded px-1 py-0.5 font-mono text-xs'>
                  {cliLoginCommand}
                </code>
                <CopyToClipboard
                  copyText={cliLoginCommand}
                  className='h-5 pr-0.5 pl-1 align-middle'
                />
              </span>
              {'.'}
            </p>
            <p>
              2. Use it as a refresh token to request a short-lived access token
              when calling the API manually.
            </p>
          </div>

          {errorMessage && (
            <div className='text-sm text-red-600 dark:text-red-400'>
              {errorMessage}
            </div>
          )}

          <p className='text-sm'>
            Store this token in a secure place. It gives long-lived access and
            should be treated like a password.
          </p>

          <p className='text-sm'>
            After authorization, you will see the token on the next page exactly
            once.
          </p>
        </CardContent>
        <Separator />
        <CardFooter className='flex flex-col items-stretch gap-3 sm:flex-row sm:justify-end'>
          <Button asChild variant='outline'>
            <Link to='/profile'>Back to profile</Link>
          </Button>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button asChild variant='outline'>
                <a
                  href={config.accountDeviceActivityUrl}
                  target='_blank'
                  rel='noopener noreferrer'
                >
                  Manage sessions
                  <ExternalLink aria-hidden='true' />
                  <span className='sr-only'>(opens in a new tab)</span>
                </a>
              </Button>
            </TooltipTrigger>
            <TooltipContent>
              Sign out of the &quot;ORT Server CLI&quot; session to invalidate
              all offline tokens.
            </TooltipContent>
          </Tooltip>
          <Button
            onClick={() => {
              void startTokenGeneration();
            }}
            disabled={isCliOidcConfigPending || isRedirecting || !cliClientId}
          >
            {isRedirecting ? 'Redirecting...' : 'Generate authentication token'}
          </Button>
        </CardFooter>
      </Card>
    </div>
  );
};

export const Route = createFileRoute('/profile/token/')({
  component: TokenPage,
});
