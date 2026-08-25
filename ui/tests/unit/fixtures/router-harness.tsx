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
  createRootRoute,
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
  path: string;
  component?: RouteComponent;
  children?: ReadonlyArray<TestRouteDefinition>;
}

export interface CreateTestRouterOptions {
  path: string;
  routes?: ReadonlyArray<TestRouteDefinition>;
}

const testRouters = new Set<TestRouter>();

export const createTestRouter = ({
  path,
  routes = [{ path: '/' }],
}: CreateTestRouterOptions) => {
  const rootRoute = createRootRoute({
    component: Outlet,
  });

  const createChildRoutes = (
    parentRoute: AnyRoute,
    definitions: ReadonlyArray<TestRouteDefinition>
  ): AnyRoute[] =>
    definitions.map((definition) => {
      const route = createRoute({
        getParentRoute: () => parentRoute,
        path: definition.path,
        component: definition.component,
      });

      return definition.children
        ? route.addChildren(createChildRoutes(route, definition.children))
        : route;
    });

  const childRoutes = createChildRoutes(rootRoute, routes);

  const router = createRouter({
    history: createMemoryHistory({ initialEntries: [path] }),
    routeTree: rootRoute.addChildren(childRoutes),
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
