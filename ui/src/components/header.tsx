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

import { Link, useRouterState } from '@tanstack/react-router';
import { CircleUser, Home, Menu } from 'lucide-react';
import { Sheet, SheetContent, SheetTrigger } from './ui/sheet';
import { Button } from './ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from './ui/dropdown-menu';
import { useAuth } from 'react-oidc-context';
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbSeparator,
} from './ui/breadcrumb';

export const Header = () => {
  const auth = useAuth();

  const matches = useRouterState({ select: (state) => state.matches });

  // The breadcrumbs are set in the routes, for example the organization name is set in the route
  // file `_layout.organizations.$orgId.route.tsx`, which is activated when the route matches any
  // route that starts with `/organizations/$orgId`. The breadcrumbs are stored in the router
  // context, and it is not reset when the user leaves the organization route. If does change when
  // the user navigates to a different organization.
  //
  // This find checks if the organization route is currently active, so we can display the
  // organization name in the header breadcrumb only when the user is in the organization route.
  const organizationMatch = matches.find(
    (match) => match.routeId === '/_layout/organizations/$orgId'
  );

  // The same breadcrumb logic applies to the other breadcrumb levels.
  const productMatch = matches.find(
    (match) =>
      match.routeId === '/_layout/organizations/$orgId/products/$productId'
  );

  const repoMatch = matches.find(
    (match) =>
      match.routeId ===
      '/_layout/organizations/$orgId/products/$productId/repositories/$repoId'
  );

  const runMatch = matches.find(
    (match) =>
      match.routeId ===
      '/_layout/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runId'
  );

  return (
    <header className='sticky top-0 z-50 flex justify-between h-16 gap-4 px-4 border-b bg-background md:px-6'>
      <div className='flex flex-row items-center gap-4'>
        <nav className='flex-col hidden gap-6 text-lg font-medium md:flex md:flex-row md:items-center md:gap-5 md:text-sm lg:gap-6'>
          <Link
            to='/'
            className='flex items-center gap-2 text-lg font-semibold md:text-base'
          >
            <Home className='w-6 h-6' />
            <span className='sr-only'>Home</span>
          </Link>
        </nav>
        <Sheet>
          <SheetTrigger asChild>
            <Button
              variant='outline'
              size='icon'
              className='shrink-0 md:hidden'
            >
              <Menu className='w-5 h-5' />
              <span className='sr-only'>Toggle navigation menu</span>
            </Button>
          </SheetTrigger>
          <SheetContent side='left'>
            <nav className='grid gap-6 text-lg font-medium'>
              <Link
                href='#'
                className='flex items-center gap-2 text-lg font-semibold'
              >
                <Home className='w-6 h-6' />
                <span className='sr-only'>Home</span>
              </Link>
            </nav>
          </SheetContent>
        </Sheet>
        <Breadcrumb>
          <BreadcrumbList>
            {organizationMatch && (
              <BreadcrumbItem>
                <BreadcrumbLink asChild>
                  <Link to={organizationMatch.pathname}>
                    {organizationMatch.context.breadcrumbs.organization}
                  </Link>
                </BreadcrumbLink>
              </BreadcrumbItem>
            )}
            {productMatch && (
              <>
                <BreadcrumbSeparator />
                <BreadcrumbItem>
                  <BreadcrumbLink asChild>
                    <Link to={productMatch.pathname}>
                      {productMatch.context.breadcrumbs.product}
                    </Link>
                  </BreadcrumbLink>
                </BreadcrumbItem>
              </>
            )}
            {repoMatch && (
              <>
                <BreadcrumbSeparator />
                <BreadcrumbItem>
                  <BreadcrumbLink asChild>
                    <Link to={repoMatch.pathname}>
                      {repoMatch.context.breadcrumbs.repo}
                    </Link>
                  </BreadcrumbLink>
                </BreadcrumbItem>
              </>
            )}
            {runMatch && (
              <>
                <BreadcrumbSeparator />
                <BreadcrumbItem>
                  <BreadcrumbLink asChild>
                    <Link to={runMatch.pathname}>
                      {runMatch.context.breadcrumbs.run}
                    </Link>
                  </BreadcrumbLink>
                </BreadcrumbItem>
              </>
            )}
          </BreadcrumbList>
        </Breadcrumb>
      </div>
      <div className='flex items-center gap-4 md:ml-auto md:gap-2 lg:gap-4'>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button
              variant='secondary'
              size='icon'
              className='ml-auto rounded-full'
            >
              <CircleUser className='w-5 h-5' />
              <span className='sr-only'>Toggle user menu</span>
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align='end'>
            <DropdownMenuItem
              onClick={async () => {
                await auth.signoutRedirect();
              }}
            >
              Logout
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  );
};
