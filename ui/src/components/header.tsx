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
import { useQuery } from '@tanstack/react-query';
import { Link, useNavigate, useRouterState } from '@tanstack/react-router';
import { Home, Menu } from 'lucide-react';
import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { getConfigByKeyOptions } from '@/api/@tanstack/react-query.gen';
import homeIcon from '@/assets/home-icon.svg';
import { ColorThemeToggle } from '@/components/color-theme-toggle';
import { LoadingIndicator } from '@/components/loading-indicator';
import { ModeToggle } from '@/components/mode-toggle';
import { Siblings } from '@/components/siblings';
import { useTheme } from '@/components/theme-provider-context';
import { ToastError } from '@/components/toast-error';
import { Avatar, AvatarFallback } from '@/components/ui/avatar';
import {
  Breadcrumb,
  BreadcrumbList,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Form, FormControl, FormField, FormItem } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Sheet, SheetContent, SheetTrigger } from '@/components/ui/sheet';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { extractInitials } from '@/helpers/extract-initials.ts';
import { setCustomFavicon } from '@/helpers/set-custom-favicon';
import { useUser } from '@/hooks/use-user';
import { toast } from '@/lib/toast';

const PRODUCT_NAME = 'ORT Server';

const formSchema = z.object({
  id: z.string().min(1),
});

export const Header = () => {
  const user = useUser();
  const navigate = useNavigate();
  const mode = useTheme().mode;

  const {
    data: dbHomeIcon,
    isPending: isHomeIconPending,
    isError: isHomeIconError,
    error: homeIconError,
  } = useQuery({
    ...getConfigByKeyOptions({ path: { key: 'HOME_ICON_URL' } }),
  });

  const {
    data: dbHomeIconDark,
    isPending: isHomeIconDarkPending,
    isError: isHomeIconDarkError,
    error: homeIconDarkError,
  } = useQuery({
    ...getConfigByKeyOptions({ path: { key: 'HOME_ICON_URL_DARK' } }),
  });

  const {
    data: dbProductName,
    isPending: isProductNamePending,
    isError: isProductNameError,
    error: productNameError,
  } = useQuery({
    ...getConfigByKeyOptions({ path: { key: 'MAIN_PRODUCT_NAME' } }),
  });

  const {
    data: dbFavicon,
    isPending: isFaviconPending,
    isError: isFaviconError,
    error: faviconError,
  } = useQuery({
    ...getConfigByKeyOptions({ path: { key: 'FAVICON_URL' } }),
  });

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
    // Extract only the repository name from the full URL to show in the title.
    const repoName = repository?.split('/').pop()?.split('.git')[0];
    const run = runMatch?.context.breadcrumbs.run;
    // Use the product name from the database if available and enabled, otherwise use z
    // the default product name which is 'ORT Server'.
    const name =
      dbProductName?.value && dbProductName.isEnabled
        ? dbProductName.value
        : PRODUCT_NAME;

    if (run) {
      document.title = `${repoName} (run ${run}) - ${name}`;
    } else if (repository) {
      document.title = `${repoName} - ${name}`;
    } else if (product) {
      document.title = `${product} - ${name}`;
    } else if (organization) {
      document.title = `${organization} - ${name}`;
    } else {
      document.title = `${name}`;
    }
  }, [
    dbProductName?.isEnabled,
    dbProductName?.value,
    organizationMatch,
    productMatch,
    repoMatch,
    runMatch,
  ]);

  if (
    isHomeIconPending ||
    isHomeIconDarkPending ||
    isProductNamePending ||
    isFaviconPending
  ) {
    return <LoadingIndicator />;
  }

  if (
    isHomeIconError ||
    isHomeIconDarkError ||
    isProductNameError ||
    isFaviconError
  ) {
    toast.error('Unable to load data', {
      description: (
        <ToastError
          error={
            homeIconError ||
            homeIconDarkError ||
            productNameError ||
            faviconError
          }
        />
      ),
      duration: Infinity,
      cancel: {
        label: 'Dismiss',
        onClick: () => {},
      },
    });
    return;
  }

  // The logic for showing the home icon is as follows:
  // - if custom icons are not enabled, fall back to using the default icon
  // - if the active theme is light, use the light mode icon, otherwise use the dark mode icon,
  //   using the default icon as a final fallback
  // - if the active theme is dark, use the dark mode icon, otherwise use the light mode icon,
  //   using the default icon as a final fallback
  // - in the case of formally correct but invalid icon URLs being provided, fall back to showing
  //   the configured or default product name instead
  let homeIconSrc;
  if (dbHomeIcon.isEnabled) {
    if (mode === 'light') {
      homeIconSrc = dbHomeIcon.value || dbHomeIconDark.value || homeIcon;
    } else {
      homeIconSrc = dbHomeIconDark.value || dbHomeIcon.value || homeIcon;
    }
  } else {
    homeIconSrc = homeIcon;
  }

  // Set the favicon based on the fetched data. Fall back to default favicon
  // in case of errors or missing values.
  if (dbFavicon.isEnabled) {
    setCustomFavicon(dbFavicon.value ?? '');
  } else {
    // If the favicon is not enabled, fall back to the default favicon
    setCustomFavicon();
  }

  return (
    <header className='bg-background sticky top-0 z-50 flex h-16 justify-between gap-4 border-b px-4 md:px-6'>
      <div className='flex flex-row items-center gap-4'>
        <nav className='hidden flex-col gap-6 text-lg font-medium md:flex md:flex-row md:items-center md:gap-5 md:text-sm lg:gap-6'>
          <Tooltip>
            <TooltipTrigger>
              <Link
                to='/'
                className='flex items-center gap-2 text-lg font-semibold md:text-base'
              >
                <img
                  src={homeIconSrc}
                  alt={
                    dbProductName.isEnabled && dbProductName.value
                      ? dbProductName.value
                      : PRODUCT_NAME
                  }
                  className='h-6'
                />
                <span className='sr-only'>Home</span>
              </Link>
            </TooltipTrigger>
            <TooltipContent>Home</TooltipContent>
          </Tooltip>
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
            {organizationMatch?.context &&
              organizationMatch.context.breadcrumbs.organization !==
                undefined && (
                <Siblings
                  entity='organization'
                  pathName={organizationMatch.pathname}
                />
              )}
            {productMatch?.context && (
              <>
                {organizationMatch?.context.breadcrumbs.organization !==
                  undefined && <BreadcrumbSeparator />}
                <Siblings entity='product' pathName={productMatch.pathname} />
              </>
            )}
            {repoMatch?.context && (
              <>
                {(organizationMatch?.context.breadcrumbs.organization !==
                  undefined ||
                  productMatch?.context.breadcrumbs.product !== undefined) && (
                  <BreadcrumbSeparator />
                )}
                <Siblings
                  entity='repository'
                  pathName={
                    '/organizations/$orgId/products/$productId/repositories/$repoId/runs'
                  }
                />
              </>
            )}
            {runMatch?.context && (
              <>
                <BreadcrumbSeparator />
                <Siblings entity='run' pathName={runMatch.pathname} />
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
                      className='w-28 text-xs'
                    />
                  </FormControl>
                </FormItem>
              )}
            />
            <input type='submit' hidden />
          </form>
        </Form>
        <ModeToggle />
        <ColorThemeToggle />
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button
              variant='secondary'
              size='icon'
              className='ml-auto rounded-full'
            >
              <Avatar className='h-8 w-8'>
                <AvatarFallback className='h-8 w-8 bg-red-400'>
                  {extractInitials(user.fullName) ??
                    user.username?.slice(0, 2).toUpperCase()}
                </AvatarFallback>
              </Avatar>
              <span className='sr-only'>Toggle user menu</span>
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align='end'>
            <DropdownMenuItem className='flex gap-2' disabled>
              <Avatar className='h-8 w-8'>
                <AvatarFallback className='h-8 w-8 bg-red-400'>
                  {extractInitials(user.fullName) ??
                    user.username?.slice(0, 2).toUpperCase()}
                </AvatarFallback>
              </Avatar>
              <div>
                <span className='font-semibold'>{user.fullName}</span>
                <br />
                <span>{user.username}</span>
              </div>
            </DropdownMenuItem>
            {user.hasRole(['superuser']) && (
              <>
                <DropdownMenuSeparator />
                <Link to='/admin'>
                  <DropdownMenuItem>Administration</DropdownMenuItem>
                </Link>
              </>
            )}
            <DropdownMenuSeparator />
            <Link to='/settings'>
              <DropdownMenuItem>Preferences</DropdownMenuItem>
            </Link>
            <Link to='/about'>
              <DropdownMenuItem>About</DropdownMenuItem>
            </Link>
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
