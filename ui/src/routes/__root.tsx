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

import { ReactQueryDevtools } from '@tanstack/react-query-devtools';
import { createRootRouteWithContext, Outlet } from '@tanstack/react-router';
import React, { Suspense } from 'react';

import { RouterContext } from '@/app';
import { Header } from '@/components/header';

// Don't use Router devtools in production.
const TanStackRouterDevtools = import.meta.env.PROD
  ? () => null
  : React.lazy(() =>
      import('@tanstack/react-router-devtools').then((res) => ({
        default: res.TanStackRouterDevtools,
      }))
    );

const RootComponent = () => {
  return (
    <>
      <ReactQueryDevtools initialIsOpen={false} />
      <div className='flex min-h-screen w-full flex-col'>
        <Header />
        <main className='flex h-full flex-col gap-4 p-4 md:w-full md:items-center md:gap-8 md:p-8'>
          <Outlet />
        </main>
      </div>
      <Suspense>
        <TanStackRouterDevtools />
      </Suspense>
    </>
  );
};

export const Route = createRootRouteWithContext<RouterContext>()({
  component: RootComponent,
});
