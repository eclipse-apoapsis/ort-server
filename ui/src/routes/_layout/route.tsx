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

import { createFileRoute, Outlet } from '@tanstack/react-router';

import { Header } from '@/components/header';
import { SideBar } from '@/components/sidebar';
import { useUser } from '@/hooks/useUser';
import { cn } from '@/lib/utils';

const Layout = () => {
  const user = useUser();
  return (
    <div className='flex min-h-screen w-full flex-col'>
      <Header />
      <div>
        {user.hasRole('superuser') && (
          <SideBar className='hidden h-full p-4 md:fixed md:float-left md:block md:w-[230px]' />
        )}
        <main
          className={cn(
            user.hasRole('superuser')
              ? 'md:float-right md:w-[calc(100%-230px)]'
              : 'md:w-full',
            'flex flex-col gap-4 p-4 md:gap-8 md:p-8'
          )}
        >
          <Outlet />
        </main>
      </div>
    </div>
  );
};

export const Route = createFileRoute('/_layout')({
  component: Layout,
});
