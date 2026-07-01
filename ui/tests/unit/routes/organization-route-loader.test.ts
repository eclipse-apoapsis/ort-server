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

import { Route } from '@/routes/organizations/$orgId/route';
import { getQueryKeyRequest } from '../fixtures/loader-test-utils';

describe('/organizations/$orgId layout loader', () => {
  it('revalidates stale organization data and updates route context', async () => {
    const ensureQueryData = vi.fn().mockResolvedValue({
      id: 1,
      name: 'Acme',
    });
    const fetchQuery = vi.fn().mockImplementation((options: unknown) => {
      const request = getQueryKeyRequest(options);

      if (request._id === 'getSuperuser') return false;

      return {
        organizationPermissions: ['WRITE'],
      };
    });

    const context: {
      queryClient: {
        ensureQueryData: typeof ensureQueryData;
        fetchQuery: typeof fetchQuery;
      };
      breadcrumbs: { organization?: string };
      permissions: {
        organization?: { includes: (permission: string) => boolean };
      };
    } = {
      queryClient: {
        ensureQueryData,
        fetchQuery,
      },
      breadcrumbs: {},
      permissions: {},
    };

    const loader = Route.options.loader as unknown as (
      options: unknown
    ) => Promise<void>;

    await loader({
      context,
      params: {
        orgId: '1',
      },
    });

    expect(ensureQueryData).toHaveBeenCalledTimes(1);

    const organizationOptions = ensureQueryData.mock.calls[0]![0];
    const organizationRequest = getQueryKeyRequest(organizationOptions);
    expect(organizationRequest._id).toBe('getOrganization');
    expect(organizationRequest.path).toEqual({ organizationId: 1 });
    expect(organizationOptions).toMatchObject({ revalidateIfStale: true });

    expect(context.breadcrumbs.organization).toBe('Acme');
    expect(context.permissions.organization?.includes('WRITE')).toBe(true);
    expect(context.permissions.organization?.includes('MANAGE_GROUPS')).toBe(
      false
    );
  });
});
