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

import { zodResolver } from '@hookform/resolvers/zod';
import { Link, useNavigate, useRouterState } from '@tanstack/react-router';
import { CircleUser, Home, Menu } from 'lucide-react';
import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { ModeToggle } from '@/components/mode-toggle';
import { Avatar, AvatarFallback } from '@/components/ui/avatar';
import { Form, FormControl, FormField, FormItem } from '@/components/ui/form';
import { useUser } from '@/hooks/use-user';
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbSeparator,
} from './ui/breadcrumb';
import { Button } from './ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from './ui/dropdown-menu';
import { Input } from './ui/input';
import { Sheet, SheetContent, SheetTrigger } from './ui/sheet';

const TITLE = 'ORT Server';

const formSchema = z.object({
  id: z.string().min(1),
});

export const Header = () => {
  const user = useUser();
  const navigate = useNavigate();

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
  });

  async function onSubmit(values: z.infer<typeof formSchema>) {
    navigate({ to: `/runs/$runId`, params: { runId: values.id } });
  }

  const matches = useRouterState({ select: (state) => state.matches });

  // The breadcrumbs are set in the routes, for example the organization name is set in the route
  // file `organizations.$orgId.route.tsx`, which is activated when the route matches any
  // route that starts with `/organizations/$orgId`. The breadcrumbs are stored in the router
  // context, and it is not reset when the user leaves the organization route. If does change when
  // the user navigates to a different organization.
  //
  // This find checks if the organization route is currently active, so we can display the
  // organization name in the header breadcrumb only when the user is in the organization route.
  const organizationMatch = matches.find(
    (match) => match.routeId === '/organizations/$orgId'
  );

  // The same breadcrumb logic applies to the other breadcrumb levels.
  const productMatch = matches.find(
    (match) => match.routeId === '/organizations/$orgId/products/$productId'
  );

  const repoMatch = matches.find(
    (match) =>
      match.routeId ===
      '/organizations/$orgId/products/$productId/repositories/$repoId'
  );

  const runMatch = matches.find(
    (match) =>
      match.routeId ===
      '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex'
  );

  // Update the document title based on the deepest breadcrumb level currently active.
  useEffect(() => {
    const organization = organizationMatch?.context.breadcrumbs.organization;
    const product = productMatch?.context.breadcrumbs.product;
    const repository = repoMatch?.context.breadcrumbs.repo;

    if (repository) {
      document.title = `${repository} - ${TITLE}`;
    } else if (product) {
      document.title = `${product} - ${TITLE}`;
    } else if (organization) {
      document.title = `${organization} - ${TITLE}`;
    } else {
      document.title = `${TITLE}`;
    }
  }, [organizationMatch, productMatch, repoMatch]);

  return (
    <header className='bg-background sticky top-0 z-50 flex h-16 justify-between gap-4 border-b px-4 md:px-6'>
      <div className='flex flex-row items-center gap-4'>
        <nav className='hidden flex-col gap-6 text-lg font-medium md:flex md:flex-row md:items-center md:gap-5 md:text-sm lg:gap-6'>
          <Link
            to='/'
            className='flex items-center gap-2 text-lg font-semibold md:text-base'
          >
            <Home className='h-6 w-6' />
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
              <Menu className='h-5 w-5' />
              <span className='sr-only'>Toggle navigation menu</span>
            </Button>
          </SheetTrigger>
          <SheetContent side='left'>
            <nav className='grid gap-6 text-lg font-medium'>
              <Link
                to='/'
                className='flex items-center gap-2 text-lg font-semibold'
              >
                <Home className='h-6 w-6' />
                <span className='sr-only'>Home</span>
              </Link>
            </nav>
          </SheetContent>
        </Sheet>
        <Breadcrumb>
          <BreadcrumbList>
            {organizationMatch?.context && (
              <BreadcrumbItem>
                <BreadcrumbLink asChild>
                  <Link to={organizationMatch.pathname}>
                    {organizationMatch.context.breadcrumbs.organization}
                  </Link>
                </BreadcrumbLink>
              </BreadcrumbItem>
            )}
            {productMatch?.context && (
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
            {repoMatch?.context && (
              <>
                <BreadcrumbSeparator />
                <BreadcrumbItem>
                  <BreadcrumbLink asChild>
                    <Link
                      to='/organizations/$orgId/products/$productId/repositories/$repoId/runs'
                      params={{
                        orgId: repoMatch.params.orgId,
                        productId: repoMatch.params.productId,
                        repoId: repoMatch.params.repoId,
                      }}
                    >
                      {repoMatch.context.breadcrumbs.repo}
                    </Link>
                  </BreadcrumbLink>
                </BreadcrumbItem>
              </>
            )}
            {runMatch?.context && (
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
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)}>
            <FormField
              control={form.control}
              name='id'
              render={({ field }) => (
                <FormItem>
                  <FormControl>
                    <Input
                      {...field}
                      type='text'
                      name='runId'
                      placeholder='Enter Run ID'
                      className='w-25 text-xs'
                    />
                  </FormControl>
                </FormItem>
              )}
            />
            <input type='submit' hidden />
          </form>
        </Form>
        <ModeToggle />
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button
              variant='secondary'
              size='icon'
              className='ml-auto rounded-full'
            >
              <CircleUser className='h-5 w-5' />
              <span className='sr-only'>Toggle user menu</span>
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align='end'>
            <DropdownMenuItem className='flex gap-2' disabled>
              <Avatar className='h-8 w-8'>
                <AvatarFallback className='h-8 w-8 bg-red-400'>
                  {user.username?.slice(0, 2).toUpperCase()}
                </AvatarFallback>
              </Avatar>
              <div>
                <span className='font-semibold'>{user.username}</span>
                <br />
                <span>{user.fullName}</span>
              </div>
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <Link to='/about'>
              <DropdownMenuItem>About</DropdownMenuItem>
            </Link>
            {user.hasRole(['superuser']) && (
              <Link to='/admin'>
                <DropdownMenuItem>Admin</DropdownMenuItem>
              </Link>
            )}
            <DropdownMenuSeparator />
            <DropdownMenuItem
              onClick={async () => {
                await user.signoutRedirect();
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
