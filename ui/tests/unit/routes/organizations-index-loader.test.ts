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

import { Route } from '@/routes/organizations/index';
import { getQueryKeyRequest } from '../fixtures/loader-test-utils';

describe('/organizations loader', () => {
  it('includes the name filter in loader deps and the prefetched query key', async () => {
    const deps = Route.options.loaderDeps?.({
      search: {
        page: 2,
        pageSize: 25,
        filter: 'acme',
      },
    } as never);

    expect(deps).toEqual({
      page: 2,
      pageSize: 25,
      filter: 'acme',
    });

    const prefetchQuery = vi.fn().mockResolvedValue(undefined);

    const loader = Route.options.loader as unknown as (
      options: unknown
    ) => Promise<void>;

    await loader({
      context: {
        queryClient: { prefetchQuery },
      },
      deps,
      params: {},
    });

    expect(prefetchQuery).toHaveBeenCalledTimes(2);

    const organizationsRequest = getQueryKeyRequest(
      prefetchQuery.mock.calls[0]![0]
    );
    expect(organizationsRequest._id).toBe('getOrganizations');
    expect(organizationsRequest.query).toMatchObject({
      limit: 25,
      offset: 25,
      filter: 'acme',
    });

    const totalCountRequest = getQueryKeyRequest(
      prefetchQuery.mock.calls[1]![0]
    );
    expect(totalCountRequest._id).toBe('getOrganizations');
    expect(totalCountRequest.query).toEqual({ limit: 1 });
  });
});
