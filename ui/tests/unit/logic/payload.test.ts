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

import { defaultValues } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/create-run/-components/default-values';
import { formValuesToPayload } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/create-run/-components/payload';
import type { CreateRunFormValues } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/create-run/-components/run-schema';

function createFormValues(): CreateRunFormValues {
  return {
    ...defaultValues(null, [], [], false, [], []),
    revision: 'main',
    path: '',
  };
}

describe('formValuesToPayload', () => {
  it('adds selected evaluator package configuration providers', () => {
    const values = createFormValues();
    values.jobConfigs.evaluator.packageConfigurationProviders = ['OrtConfig'];
    values.jobConfigs.evaluator.packageConfigurationProviderConfig = {
      OrtConfig: {
        options: {
          path: 'evaluator-package-configurations.yml',
        },
        secrets: {
          token: 'evaluator-token',
        },
      },
      File: {
        options: {
          path: 'ignored-package-configurations.yml',
        },
        secrets: {},
      },
    };

    const payload = formValuesToPayload(values);

    expect(payload.jobConfigs.evaluator?.packageConfigurationProviders).toEqual(
      [
        {
          type: 'OrtConfig',
          id: 'OrtConfig',
          enabled: true,
          options: {
            path: 'evaluator-package-configurations.yml',
          },
          secrets: {
            token: 'evaluator-token',
          },
        },
      ]
    );
  });

  it('adds selected package curation providers', () => {
    const values = createFormValues();
    values.jobConfigs.analyzer.packageCurationProviders = ['ClearlyDefined'];
    values.jobConfigs.analyzer.packageCurationProviderConfig = {
      ClearlyDefined: {
        options: {
          serverUrl: 'https://api.clearlydefined.io',
        },
        secrets: {
          token: 'clearly-defined-token',
        },
      },
      File: {
        options: {
          path: 'curations.yml',
        },
        secrets: {},
      },
    };

    const payload = formValuesToPayload(values);

    expect(payload.jobConfigs.analyzer?.packageCurationProviders).toEqual([
      {
        type: 'ClearlyDefined',
        id: 'ClearlyDefined',
        enabled: true,
        options: {
          serverUrl: 'https://api.clearlydefined.io',
        },
        secrets: {
          token: 'clearly-defined-token',
        },
      },
    ]);
  });

  it('adds selected reporter package configuration providers when evaluator is disabled', () => {
    const values = createFormValues();
    values.jobConfigs.evaluator.enabled = false;
    values.jobConfigs.reporter.packageConfigurationProviders = ['OrtConfig'];
    values.jobConfigs.reporter.packageConfigurationProviderConfig = {
      OrtConfig: {
        options: {
          path: 'reporter-package-configurations.yml',
        },
        secrets: {
          token: 'reporter-token',
        },
      },
    };

    const payload = formValuesToPayload(values);

    expect(payload.jobConfigs.evaluator).toBeUndefined();
    expect(payload.jobConfigs.reporter?.packageConfigurationProviders).toEqual([
      {
        type: 'OrtConfig',
        id: 'OrtConfig',
        enabled: true,
        options: {
          path: 'reporter-package-configurations.yml',
        },
        secrets: {
          token: 'reporter-token',
        },
      },
    ]);
  });

  it('omits infrastructure services from environment configs', () => {
    const values = createFormValues();
    values.jobConfigs.analyzer.environmentDefinitions = {
      conan: [
        {
          service: 'service-a',
          name: 'private-conan',
          url: '',
          verifySsl: 'true',
        },
      ],
    };
    values.jobConfigs.analyzer.infrastructureServices = [
      {
        credentialsTypes: ['NETRC_FILE'],
        name: 'inherited-service',
        passwordSecretRef: 'password',
        url: 'https://example.org/repository',
        usernameSecretRef: 'username',
      },
    ];

    const payload = formValuesToPayload(values);

    expect(payload.jobConfigs.analyzer?.environmentConfig).not.toHaveProperty(
      'infrastructureServices'
    );
  });

  it('omits package configuration providers if none are selected', () => {
    const values = createFormValues();
    values.jobConfigs.evaluator.packageConfigurationProviders = [];
    values.jobConfigs.evaluator.packageConfigurationProviderConfig = {
      OrtConfig: {
        options: {
          path: 'evaluator-package-configurations.yml',
        },
        secrets: {},
      },
    };
    values.jobConfigs.reporter.packageConfigurationProviders = [];
    values.jobConfigs.reporter.packageConfigurationProviderConfig = {
      OrtConfig: {
        options: {
          path: 'reporter-package-configurations.yml',
        },
        secrets: {},
      },
    };

    const payload = formValuesToPayload(values);

    expect(
      payload.jobConfigs.evaluator?.packageConfigurationProviders
    ).toBeUndefined();
    expect(
      payload.jobConfigs.reporter?.packageConfigurationProviders
    ).toBeUndefined();
  });

  it('omits package curation providers if none are selected', () => {
    const values = createFormValues();
    values.jobConfigs.analyzer.packageCurationProviders = [];
    values.jobConfigs.analyzer.packageCurationProviderConfig = {
      ClearlyDefined: {
        options: {
          serverUrl: 'https://api.clearlydefined.io',
        },
        secrets: {},
      },
    };

    const payload = formValuesToPayload(values);

    expect(
      payload.jobConfigs.analyzer?.packageCurationProviders
    ).toBeUndefined();
  });

  it('omits reporter package configuration providers when evaluator is enabled', () => {
    const values = createFormValues();
    values.jobConfigs.evaluator.packageConfigurationProviders = ['OrtConfig'];
    values.jobConfigs.evaluator.packageConfigurationProviderConfig = {
      OrtConfig: {
        options: {
          path: 'evaluator-package-configurations.yml',
        },
        secrets: {},
      },
    };
    values.jobConfigs.reporter.packageConfigurationProviders = ['File'];
    values.jobConfigs.reporter.packageConfigurationProviderConfig = {
      File: {
        options: {
          path: 'reporter-package-configurations.yml',
        },
        secrets: {},
      },
    };

    const payload = formValuesToPayload(values);

    expect(payload.jobConfigs.evaluator?.packageConfigurationProviders).toEqual(
      [
        {
          type: 'OrtConfig',
          id: 'OrtConfig',
          enabled: true,
          options: {
            path: 'evaluator-package-configurations.yml',
          },
        },
      ]
    );
    expect(
      payload.jobConfigs.reporter?.packageConfigurationProviders
    ).toBeUndefined();
  });

  it('omits the environment config when no environment settings are configured', () => {
    const values = createFormValues();
    values.jobConfigs.analyzer.infrastructureServices = [
      {
        credentialsTypes: ['NETRC_FILE'],
        name: 'inherited-service',
        passwordSecretRef: 'password',
        url: 'https://example.org/repository',
        usernameSecretRef: 'username',
      },
    ];

    const payload = formValuesToPayload(values);

    expect(payload.jobConfigs.analyzer?.environmentConfig).toBeUndefined();
  });

  it('preserves multiple environment definitions for the same ecosystem', () => {
    const values = createFormValues();
    values.jobConfigs.analyzer.environmentDefinitions = {
      maven: [
        {
          service: 'service-a',
          id: 'repository-a',
          mirrorOf: '',
        },
        {
          service: 'service-b',
          id: 'repository-b',
          mirrorOf: 'central',
        },
      ],
    };

    const payload = formValuesToPayload(values);

    expect(
      payload.jobConfigs.analyzer?.environmentConfig?.environmentDefinitions
    ).toEqual(values.jobConfigs.analyzer.environmentDefinitions);
  });
});
