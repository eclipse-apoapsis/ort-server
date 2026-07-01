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

import { Route as AdminPluginTemplatesRoute } from '@/routes/admin/plugins/$pluginType/$pluginId/index';
import { Route as AdminRunsRoute } from '@/routes/admin/runs/index';
import { Route as AdminUsersRoute } from '@/routes/admin/users/index';
import { createDeferred } from '../fixtures/loader-test-utils';

const expectLoaderToAwaitQuery = async (
  loader: (options: unknown) => Promise<void>,
  queryMethod: 'ensureQueryData' | 'prefetchQuery',
  options: unknown
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

  expect(queryFn).toHaveBeenCalledTimes(1);
  expect(resolved).toBe(false);

  deferredQuery.resolve(undefined);
  await loaderPromise;

  expect(resolved).toBe(true);
};

describe('admin route loaders', () => {
  it('awaits the runs prefetch before resolving', async () => {
    const loader = AdminRunsRoute.options.loader as unknown as (
      options: unknown
    ) => Promise<void>;

    await expectLoaderToAwaitQuery(loader, 'prefetchQuery', {
      deps: {
        page: 2,
        pageSize: 25,
        status: ['FINISHED'],
      },
      params: {},
    });
  });

  it('awaits the users query before resolving', async () => {
    const loader = AdminUsersRoute.options.loader as unknown as (
      options: unknown
    ) => Promise<void>;

    await expectLoaderToAwaitQuery(loader, 'ensureQueryData', {
      deps: {},
      params: {},
    });
  });

  it('awaits the plugin templates query before resolving', async () => {
    const loader = AdminPluginTemplatesRoute.options.loader as unknown as (
      options: unknown
    ) => Promise<void>;

    await expectLoaderToAwaitQuery(loader, 'prefetchQuery', {
      deps: {},
      params: {
        pluginType: 'scanner',
        pluginId: 'ScanCode',
      },
    });
  });
});
