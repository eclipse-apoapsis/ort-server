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

import { Route } from '@/routes/organizations/$orgId/default-plugins/index';
import { getQueryKeyRequest } from '../fixtures/loader-test-utils';

describe('/organizations/$orgId/default-plugins loader', () => {
  it('prefetches the default plugins and the plugins of the organization', async () => {
    const prefetchQuery = vi.fn().mockResolvedValue(undefined);
    const loader = Route.options.loader as unknown as (
      options: unknown
    ) => Promise<void>;

    await loader({
      context: { queryClient: { prefetchQuery } },
      params: { orgId: '42' },
    });

    const requests = prefetchQuery.mock.calls.map(([options]) =>
      getQueryKeyRequest(options)
    );

    expect(requests.map((request) => request._id)).toEqual([
      'getOrganizationDefaultPlugins',
      'getPluginsForOrganization',
    ]);
    requests.forEach((request) =>
      expect(request.path).toEqual({ organizationId: 42 })
    );
  });
});
