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

import { afterEach, describe, expect, it, vi } from 'vitest';

describe('API client', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.resetModules();
  });

  it('puts the API URL of the deployment into query options built when a module is loaded', async () => {
    vi.stubEnv('VITE_API_URL', 'https://ort.example.com');
    vi.resetModules();

    const { getOrganizationsOptions } =
      await import('@/api/@tanstack/react-query.gen');

    expect(getOrganizationsOptions().queryKey[0].baseURL).toBe(
      'https://ort.example.com'
    );
  });
});
