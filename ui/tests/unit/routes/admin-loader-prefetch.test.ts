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

import { describe, expect, it, vi } from 'vitest';

import { client } from '@/api/client.gen';
import { Route as AdminOverviewRoute } from '@/routes/admin/index';
import { Route as AdminPluginTemplatesRoute } from '@/routes/admin/plugins/$pluginType/$pluginId/index';
import { Route as AdminDefaultPluginsRoute } from '@/routes/admin/plugins/defaults/index';
import { Route as AdminProductsRoute } from '@/routes/admin/products/index';
import { Route as AdminRepositoriesRoute } from '@/routes/admin/repositories/index';
import { Route as AdminRunsRoute } from '@/routes/admin/runs/index';
import { Route as AdminUsersRoute } from '@/routes/admin/users/index';
import {
  createDeferred,
  getQueryKeyRequest,
} from '../fixtures/loader-test-utils';

const expectLoaderToAwaitQuery = async (
  loader: (options: unknown) => Promise<void>,
  queryMethod: 'ensureQueryData' | 'prefetchQuery',
  options: unknown,
  expectedQueryCalls = 1
) => {
  const deferredQuery = createDeferred<undefined>();
  const queryFn = vi.fn().mockReturnValue(deferredQuery.promise);
  let resolved = false;

  const loaderPromise = loader({
    ...(options as object),
    context: {
      queryClient: { [queryMethod]: queryFn },
    },
  }).then(() => {
    resolved = true;
  });

  await Promise.resolve();

  expect(queryFn).toHaveBeenCalledTimes(expectedQueryCalls);
  expect(resolved).toBe(false);

  deferredQuery.resolve(undefined);
  await loaderPromise;

  expect(resolved).toBe(true);

  return queryFn;
};

describe('admin route loaders', () => {
  it('starts all overview queries without waiting for them', () => {
    const loader = AdminOverviewRoute.options.loader as unknown as (
      options: unknown
    ) => unknown;
    const queryFn = vi.fn().mockReturnValue(createDeferred().promise);

    const result = loader({
      deps: {},
      params: {},
      context: { queryClient: { prefetchQuery: queryFn } },
    });

    expect(result).toBeUndefined();
    expect(
      queryFn.mock.calls.map(([options]) => {
        const { _id, query } = getQueryKeyRequest(options);
        return { _id, query };
      })
    ).toEqual([
      { _id: 'getOrganizations', query: { limit: 1 } },
      { _id: 'getProducts', query: { limit: 1 } },
      { _id: 'getRepositories', query: { limit: 1 } },
      { _id: 'getUsers', query: { limit: 1 } },
      { _id: 'getInstalledPlugins', query: undefined },
      { _id: 'getGlobalDefaultPlugins', query: undefined },
      { _id: 'getRuns', query: { limit: 1 } },
      { _id: 'getRuns', query: { limit: 1, status: 'finished' } },
      { _id: 'getRuns', query: { limit: 1, status: 'finished_with_issues' } },
      { _id: 'getRuns', query: { limit: 1, status: 'failed' } },
      { _id: 'getRuns', query: { limit: 1, status: 'active' } },
      { _id: 'getRuns', query: { limit: 1, status: 'created' } },
    ]);
  });

  it.each([
    { name: 'overview', route: AdminOverviewRoute, queryCount: 12 },
    { name: 'products', route: AdminProductsRoute, queryCount: 2 },
    { name: 'repositories', route: AdminRepositoriesRoute, queryCount: 2 },
  ])(
    'requests the $name page from the base URL set after the module is loaded',
    async ({ route, queryCount }) => {
      const loader = route.options.loader as unknown as (
        options: unknown
      ) => unknown;
      const queryFn = vi.fn().mockResolvedValue(undefined);
      const { baseURL } = client.getConfig();
      client.setConfig({ baseURL: 'https://ort.example.com' });

      try {
        await loader({
          deps: { page: 1, pageSize: 10 },
          params: {},
          context: { queryClient: { prefetchQuery: queryFn } },
        });
      } finally {
        client.setConfig({ baseURL });
      }

      expect(
        queryFn.mock.calls.map(
          ([options]) =>
            (getQueryKeyRequest(options) as { baseURL?: string }).baseURL
        )
      ).toEqual(Array(queryCount).fill('https://ort.example.com'));
    }
  );

  it('awaits the runs and total count prefetches before resolving', async () => {
    const loader = AdminRunsRoute.options.loader as unknown as (
      options: unknown
    ) => Promise<void>;

    const queryFn = await expectLoaderToAwaitQuery(
      loader,
      'prefetchQuery',
      {
        deps: {
          page: 2,
          pageSize: 25,
          status: ['FINISHED'],
        },
        params: {},
      },
      2
    );

    const runsRequest = getQueryKeyRequest(queryFn.mock.calls[0]![0]);
    expect(runsRequest._id).toBe('getRuns');
    expect(runsRequest.query).toEqual({
      limit: 25,
      offset: 25,
      status: 'FINISHED',
    });

    const totalCountRequest = getQueryKeyRequest(queryFn.mock.calls[1]![0]);
    expect(totalCountRequest._id).toBe('getRuns');
    expect(totalCountRequest.query).toEqual({ limit: 1 });
  });

  it('awaits the requested page of all products and their total count before resolving', async () => {
    const loader = AdminProductsRoute.options.loader as unknown as (
      options: unknown
    ) => Promise<void>;

    const queryFn = await expectLoaderToAwaitQuery(
      loader,
      'prefetchQuery',
      {
        deps: { page: 3, pageSize: 25, filter: 'shop' },
        params: {},
      },
      2
    );

    const request = getQueryKeyRequest(queryFn.mock.calls[0]![0]);
    expect(request._id).toBe('getProducts');
    expect(request.query).toEqual({ limit: 25, offset: 50, filter: 'shop' });

    const totalCountRequest = getQueryKeyRequest(queryFn.mock.calls[1]![0]);
    expect(totalCountRequest._id).toBe('getProducts');
    expect(totalCountRequest.query).toEqual({ limit: 1 });
  });

  it('awaits the requested page of all repositories and their total count before resolving', async () => {
    const loader = AdminRepositoriesRoute.options.loader as unknown as (
      options: unknown
    ) => Promise<void>;

    const queryFn = await expectLoaderToAwaitQuery(
      loader,
      'prefetchQuery',
      {
        deps: { page: 2, pageSize: 50, filter: 'shop' },
        params: {},
      },
      2
    );

    const request = getQueryKeyRequest(queryFn.mock.calls[0]![0]);
    expect(request._id).toBe('getRepositories');
    expect(request.query).toEqual({ limit: 50, offset: 50, filter: 'shop' });

    const totalCountRequest = getQueryKeyRequest(queryFn.mock.calls[1]![0]);
    expect(totalCountRequest._id).toBe('getRepositories');
    expect(totalCountRequest.query).toEqual({ limit: 1 });
  });

  it('forwards the admin user list parameters to the awaited query', async () => {
    const loader = AdminUsersRoute.options.loader as unknown as (
      options: unknown
    ) => Promise<void>;

    const queryFn = await expectLoaderToAwaitQuery(loader, 'ensureQueryData', {
      deps: {
        page: 2,
        pageSize: 25,
        search: 'example',
        sortBy: [
          { id: 'lastName', desc: false },
          { id: 'firstName', desc: true },
        ],
      },
      params: {},
    });

    const request = getQueryKeyRequest(queryFn.mock.calls[0]![0]);
    expect(request._id).toBe('getUsers');
    expect(request.query).toEqual({
      limit: 25,
      offset: 25,
      search: 'example',
      sort: 'lastName,-firstName',
    });
  });

  it('normalizes absent admin user list parameters', async () => {
    const loader = AdminUsersRoute.options.loader as unknown as (
      options: unknown
    ) => Promise<void>;

    const queryFn = await expectLoaderToAwaitQuery(loader, 'ensureQueryData', {
      deps: {},
      params: {},
    });

    const request = getQueryKeyRequest(queryFn.mock.calls[0]![0]);
    expect(request._id).toBe('getUsers');
    expect(request.query).toEqual({
      limit: 10,
      offset: 0,
      search: undefined,
      sort: undefined,
    });
  });

  it('awaits the installed plugins and plugin templates queries before resolving', async () => {
    const loader = AdminPluginTemplatesRoute.options.loader as unknown as (
      options: unknown
    ) => Promise<void>;

    const queryFn = await expectLoaderToAwaitQuery(
      loader,
      'prefetchQuery',
      {
        deps: {},
        params: {
          pluginType: 'scanner',
          pluginId: 'ScanCode',
        },
      },
      2
    );

    expect(getQueryKeyRequest(queryFn.mock.calls[0]![0])._id).toBe(
      'getInstalledPlugins'
    );
    expect(getQueryKeyRequest(queryFn.mock.calls[1]![0])._id).toBe(
      'getPluginTemplates'
    );
  });

  it('awaits the default plugins queries before resolving', async () => {
    const loader = AdminDefaultPluginsRoute.options.loader as unknown as (
      options: unknown
    ) => Promise<void>;

    const queryFn = await expectLoaderToAwaitQuery(
      loader,
      'ensureQueryData',
      { deps: {}, params: {} },
      2
    );

    expect(getQueryKeyRequest(queryFn.mock.calls[0]![0])._id).toBe(
      'getGlobalDefaultPlugins'
    );
    expect(getQueryKeyRequest(queryFn.mock.calls[1]![0])._id).toBe(
      'getAvailableGlobalDefaultPlugins'
    );
  });
});
