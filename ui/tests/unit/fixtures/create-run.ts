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

import type {
  OrganizationPermission,
  OrtRun,
  PreconfiguredPluginDescriptor,
  ProductPermission,
  RepositoryPermission,
  Secret,
} from '@/api';
import {
  OrganizationPermissions,
  ProductPermissions,
  RepositoryPermissions,
} from '@/lib/permissions';
import type { DeepPartial } from './deep-partial';

/**
 * Builds a {@link PreconfiguredPluginDescriptor} with sensible defaults. Tests
 * override only the fields they assert on (typically `id`, `type`, `options`).
 */
export const createPluginDescriptor = (
  overrides: DeepPartial<PreconfiguredPluginDescriptor> = {}
): PreconfiguredPluginDescriptor =>
  ({
    id: 'TestPlugin',
    type: 'SCANNER',
    displayName: 'Test Plugin',
    summary: 'A test plugin.',
    description: 'A test plugin.',
    options: [],
    ...overrides,
  }) as PreconfiguredPluginDescriptor;

/** Build the permission context expected by create-run field components. */
export const createPermissions = (
  overrides: DeepPartial<{
    organization: {
      id: number;
      isSuperuser: boolean;
      permissions: OrganizationPermission[];
    };
    product: {
      id: number;
      isSuperuser: boolean;
      permissions: ProductPermission[];
    };
    repository: {
      id: number;
      isSuperuser: boolean;
      permissions: RepositoryPermission[];
    };
  }> = {}
) => ({
  organization: new OrganizationPermissions(
    overrides.organization?.id ?? 1,
    overrides.organization?.isSuperuser ?? false,
    overrides.organization?.permissions ?? []
  ),
  product: new ProductPermissions(
    overrides.product?.id ?? 2,
    overrides.product?.isSuperuser ?? false,
    overrides.product?.permissions ?? []
  ),
  repository: new RepositoryPermissions(
    overrides.repository?.id ?? 3,
    overrides.repository?.isSuperuser ?? false,
    overrides.repository?.permissions ?? []
  ),
});

/** Build secrets offered to plugin option fields. */
export const createPluginSecrets = (
  overrides: DeepPartial<Secret>[] = [{}]
): Secret[] =>
  overrides.map((secret, index) => ({
    name: secret.name ?? `test-secret-${index + 1}`,
    description: secret.description,
  }));

/** Build package curation provider plugins for analyzer field tests. */
export const createPackageCurationProviderPlugins = (
  overrides: DeepPartial<PreconfiguredPluginDescriptor>[] = [{}]
): PreconfiguredPluginDescriptor[] =>
  overrides.map((plugin, index) =>
    createPluginDescriptor({
      id: `PackageCurationProvider${index + 1}`,
      type: 'PACKAGE_CURATION_PROVIDER',
      displayName: `Package Curation Provider ${index + 1}`,
      ...plugin,
    })
  );

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
