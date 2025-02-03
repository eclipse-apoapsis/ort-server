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

import { AuthProviderProps } from 'react-oidc-context';

import { env } from '@/ui-env';

interface Config {
  BASEPATH: string;
  API_URL: string;
  oidcConfig: AuthProviderProps;
  serverClientId: string;
  authBaseUrl: string;
  realm: string;
  pollInterval: number;
}

// Read the environment variables needed to create the configuration object
const BASEPATH = env.VITE_BASEPATH;
const API_URL = env.VITE_API_URL;
const AUTHORITY = env.VITE_AUTHORITY;
const UI_URL = env.VITE_UI_URL;
const CLIENT_ID = env.VITE_CLIENT_ID;
const CLIENT_ID_SERVER = env.VITE_CLIENT_ID_SERVER;
const RUN_POLL_INTERVAL = env.VITE_RUN_POLL_INTERVAL;

// Initialize the OpenID Connect configuration
const oidcConfig = {
  authority: AUTHORITY,
  redirect_uri: UI_URL,
  client_id: CLIENT_ID,
  automaticSilentRenew: true,
  loadUserInfo: true,
  onSigninCallback: () => {
    // This removes the Keycloak related query parameters from the URL after a successful login.
    const url = new URL(window.location.href);
    url.searchParams.delete('state');
    url.searchParams.delete('session_state');
    url.searchParams.delete('iss');
    url.searchParams.delete('code');
    window.history.replaceState({}, document.title, url.pathname + url.search);
  },
} satisfies AuthProviderProps;

const serverClientId = CLIENT_ID_SERVER;

// Configure Keycloak
const authBaseUrl = AUTHORITY.split('/realms/')[0] || 'http://localhost:8081';
const realm = AUTHORITY.split('/realms/')[1] || 'master';

// Polling interval for run status updates
const pollInterval = RUN_POLL_INTERVAL;

export const config: Config = {
  BASEPATH,
  API_URL,
  oidcConfig,
  serverClientId,
  authBaseUrl,
  realm,
  pollInterval,
};
