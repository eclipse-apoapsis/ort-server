/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import type { PreconfiguredPluginDescriptor } from '@/api';
import { defaultValues } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/create-run/-components/default-values';
import { createRunFormSchema } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/create-run/-components/run-schema';

const packageConfigurationProviderPlugin: PreconfiguredPluginDescriptor = {
  id: 'Dir',
  type: 'PACKAGE_CONFIGURATION_PROVIDER',
  displayName: 'Directory',
  summary: 'A package configuration provider plugin.',
  description: 'A package configuration provider plugin.',
  options: [
    {
      name: 'path',
      description: 'Provider path.',
      type: 'STRING',
      isFixed: false,
      isNullable: false,
      isRequired: true,
    },
  ],
};

function createValidFormData() {
  const formData = defaultValues(
    null,
    [],
    [],
    false,
    [],
    [packageConfigurationProviderPlugin]
  );

  formData.revision = 'main';
  formData.jobConfigs.evaluator.packageConfigurationProviders = ['Dir'];
  formData.jobConfigs.evaluator.packageConfigurationProviderConfig = {
    Dir: {
      options: {
        path: 'evaluator-package-configurations',
      },
      secrets: {},
    },
  };
  formData.jobConfigs.reporter.packageConfigurationProviders = ['Dir'];
  formData.jobConfigs.reporter.packageConfigurationProviderConfig = {
    Dir: {
      options: {},
      secrets: {},
    },
  };

  return formData;
}

describe('createRunFormSchema', () => {
  it('ignores reporter package configuration providers when evaluator is enabled', () => {
    const schema = createRunFormSchema(
      [],
      [],
      [],
      [packageConfigurationProviderPlugin]
    );

    const result = schema.safeParse(createValidFormData());

    expect(result.success).toBe(true);
  });

  it('validates reporter package configuration providers when evaluator is disabled', () => {
    const schema = createRunFormSchema(
      [],
      [],
      [],
      [packageConfigurationProviderPlugin]
    );
    const formData = createValidFormData();
    formData.jobConfigs.evaluator.enabled = false;

    const result = schema.safeParse(formData);

    expect(result.success).toBe(false);
    expect(result.error?.issues.map((issue) => issue.path)).toContainEqual([
      'jobConfigs',
      'reporter',
      'packageConfigurationProviderConfig',
      'Dir',
      'options',
      'path',
    ]);
  });
});
