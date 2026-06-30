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

import type { OrtRun, PreconfiguredPluginDescriptor } from '@/api';
import type { DeepPartial } from './deep-partial';

/**
 * Builds a {@link PreconfiguredPluginDescriptor} with sensible defaults. Tests
 * override only the fields they assert on (typically `id`, `type`, `options`).
 */
export const createPluginDescriptor = (
  overrides: Partial<PreconfiguredPluginDescriptor> = {}
): PreconfiguredPluginDescriptor => ({
  id: 'TestPlugin',
  type: 'SCANNER',
  displayName: 'Test Plugin',
  summary: 'A test plugin.',
  description: 'A test plugin.',
  options: [],
  ...overrides,
});

/**
 * Builds a minimal {@link OrtRun} for tests that only read `revision`, `path`,
 * `jobConfigs`, and `labels`. Overrides may be partial at any depth; the single
 * cast keeps call sites free of `as unknown as OrtRun`.
 */
export const createOrtRun = (overrides: DeepPartial<OrtRun> = {}): OrtRun =>
  ({
    revision: 'main',
    path: '',
    jobConfigs: {},
    labels: {},
    ...overrides,
  }) as unknown as OrtRun;
