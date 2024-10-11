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
import { AuthProvider } from 'react-oidc-context';

import { OpenAPI as OpenAPIConfig } from '@/api/requests/core/OpenAPI';
import { ThemeProvider } from '@/components/theme-provider';
import { Toaster } from '@/components/ui/toaster';
import { TooltipProvider } from '@/components/ui/tooltip';
import { config } from '@/config';
import { queryClient } from '@/lib/query-client';

OpenAPIConfig.BASE = config.API_URL;

const oidcConfig = config.oidcConfig;

export const Providers = ({ children }: { children: ReactNode }) => {
  return (
    <AuthProvider {...oidcConfig}>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider defaultTheme='light' storageKey='vite-ui-theme'>
          <TooltipProvider>{children}</TooltipProvider>
        </ThemeProvider>
      </QueryClientProvider>
      <Toaster
        toastOptions={{
          classNames: {
            title: 'font-semibold',
            error: 'bg-red-500 text-white',
            success: 'text-green-400',
            warning: 'text-yellow-400',
            info: 'bg-white text-black',
          },
        }}
      />
    </AuthProvider>
  );
};
