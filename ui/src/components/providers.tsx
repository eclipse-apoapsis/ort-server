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

import { QueryClientProvider } from '@tanstack/react-query';
import { ReactNode } from 'react';
import { AuthProvider, AuthProviderProps } from 'react-oidc-context';

import { OpenAPI as OpenAPIConfig } from '@/api/requests/core/OpenAPI';
import { Toaster } from '@/components/ui/toaster';
import { queryClient } from '@/lib/query-client';

OpenAPIConfig.BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080';

const oidcConfig = {
  authority:
    import.meta.env.VITE_AUTHORITY || 'http://localhost:8081/realms/master',
  redirect_uri: import.meta.env.VITE_UI_URL || 'http://localhost:5173/',
  client_id: import.meta.env.VITE_CLIENT_ID || 'ort-server-ui-dev',
  automaticSilentRenew: true,
} satisfies AuthProviderProps;

export const Providers = ({ children }: { children: ReactNode }) => {
  return (
    <AuthProvider {...oidcConfig}>
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      <Toaster />
    </AuthProvider>
  );
};
