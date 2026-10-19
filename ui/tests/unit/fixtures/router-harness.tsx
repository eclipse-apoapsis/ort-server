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

/* eslint-disable react-refresh/only-export-components */

import {
  createMemoryHistory,
  createRootRouteWithContext,
  createRoute,
  createRouter,
  Outlet,
  RouterProvider,
  type AnyRoute,
  type RouteComponent,
} from '@tanstack/react-router';
import type { ReactNode } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { afterEach } from 'vitest';

export interface TestRouteDefinition {
  // A route without a path, such as a layout route, has an ID instead.
  path?: string;
  id?: string;
  component?: RouteComponent;
  // A loader of the application, such as the one of a layout route that writes the breadcrumb.
  loader?: AnyRoute['options']['loader'];
  validateSearch?: (search: Record<string, unknown>) => Record<string, unknown>;
  children?: ReadonlyArray<TestRouteDefinition>;
}

export interface CreateTestRouterOptions {
  path: string;
  routes?: ReadonlyArray<TestRouteDefinition>;
  // The context the loaders receive, as the application passes it to its router.
  context?: Record<string, unknown>;
}

const testRouters = new Set<TestRouter>();

export const createTestRouter = ({
  path,
  routes = [{ path: '/' }],
  context = {},
}: CreateTestRouterOptions) => {
  const rootRoute = createRootRouteWithContext<Record<string, unknown>>()({
    component: Outlet,
  });

  const createChildRoutes = (
    parentRoute: AnyRoute,
    definitions: ReadonlyArray<TestRouteDefinition>
  ): AnyRoute[] =>
    definitions.map((definition) => {
      const route = createRoute({
        getParentRoute: () => parentRoute,
        ...(definition.id
          ? { id: definition.id }
          : { path: definition.path ?? '/' }),
        component: definition.component,
        loader: definition.loader,
        validateSearch: definition.validateSearch,
      });

      return definition.children
        ? route.addChildren(createChildRoutes(route, definition.children))
        : route;
    });

  const childRoutes = createChildRoutes(rootRoute, routes);

  const router = createRouter({
    history: createMemoryHistory({ initialEntries: [path] }),
    routeTree: rootRoute.addChildren(childRoutes),
    context,
    scrollRestoration: false,
  });

  testRouters.add(router);

  return router;
};

export type TestRouter = ReturnType<typeof createTestRouter>;

export const RouterTestProvider = ({ router }: { router: TestRouter }) => (
  <RouterProvider router={router} />
);

export const renderStaticWithRouter = async (
  ui: ReactNode,
  options: CreateTestRouterOptions
) => {
  const routes = options.routes ?? [{ path: '/' }];
  const router = createTestRouter({
    ...options,
    routes: routes.map((route) => ({
      ...route,
      component: route.component ?? (() => ui),
    })),
  });

  await router.load();

  return renderToStaticMarkup(<RouterTestProvider router={router} />);
};

afterEach(() => {
  testRouters.forEach((router) => {
    router.clearCache();
    router.history.destroy();
  });
  testRouters.clear();
});
