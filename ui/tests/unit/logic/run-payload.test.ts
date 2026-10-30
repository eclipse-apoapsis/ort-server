/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import { describe, expect, it } from 'vitest';

import { runToPayload } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/config/-components/run-payload';
import { createOrtRun } from '../fixtures/create-run';

describe('runToPayload', () => {
  it('keeps the settings of the request and leaves out what the server set', () => {
    const jobConfigs = { analyzer: { allowDynamicVersions: true } };

    const payload = runToPayload(
      createOrtRun({
        revision: 'main',
        path: 'backend',
        paths: {
          excludes: [{ pattern: 'docs/**', reason: 'DOCUMENTATION_OF' }],
        },
        jobConfigContext: 'v2',
        environmentConfigPath: 'ort.env.yml',
        labels: { team: 'core' },
        jobConfigs,
        resolvedJobConfigs: { analyzer: { allowDynamicVersions: false } },
        resolvedRevision: '0a945b8',
        status: 'FINISHED',
        jobs: { analyzer: { status: 'FINISHED' } },
      })
    );

    expect(payload).toEqual({
      revision: 'main',
      path: 'backend',
      paths: { excludes: [{ pattern: 'docs/**', reason: 'DOCUMENTATION_OF' }] },
      jobConfigContext: 'v2',
      environmentConfigPath: 'ort.env.yml',
      labels: { team: 'core' },
      jobConfigs,
    });
  });
});
