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

import { expect, it } from 'vitest';

import { defaultValues } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/create-run/-components/default-values';
import { createOrtRun, createPluginDescriptor } from '../fixtures/create-run';

it('preserves multiple environment definitions from reruns', () => {
  const ortRun = createOrtRun({
    jobConfigs: {
      analyzer: {
        environmentConfig: {
          infrastructureServices: [],
          environmentDefinitions: {
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
          },
        },
      },
    },
  });

  const defaults = defaultValues(ortRun, [], [], false, [], []);

  expect(defaults.jobConfigs.analyzer.environmentDefinitions).toEqual(
    ortRun.jobConfigs.analyzer?.environmentConfig?.environmentDefinitions
  );
});

it('preserves package configuration provider config from reruns', () => {
  const ortRun = createOrtRun({
    jobConfigs: {
      evaluator: {
        packageConfigurationProviders: [
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
        ],
      },
      reporter: {
        packageConfigurationProviders: [
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
        ],
      },
    },
  });

  const defaults = defaultValues(ortRun, [], [], false, [], []);

  expect(defaults.jobConfigs.evaluator.packageConfigurationProviders).toEqual([
    'OrtConfig',
  ]);
  expect(
    defaults.jobConfigs.evaluator.packageConfigurationProviderConfig
  ).toEqual({
    OrtConfig: {
      options: {
        path: 'evaluator-package-configurations.yml',
      },
      secrets: {
        token: 'evaluator-token',
      },
    },
  });
  expect(defaults.jobConfigs.reporter.packageConfigurationProviders).toEqual([
    'OrtConfig',
  ]);
  expect(
    defaults.jobConfigs.reporter.packageConfigurationProviderConfig
  ).toEqual({
    OrtConfig: {
      options: {
        path: 'reporter-package-configurations.yml',
      },
      secrets: {
        token: 'reporter-token',
      },
    },
  });
});

it('preserves package curation provider config from reruns', () => {
  const ortRun = createOrtRun({
    jobConfigs: {
      analyzer: {
        packageCurationProviders: [
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
        ],
      },
    },
  });

  const defaults = defaultValues(ortRun, [], [], false, [], []);

  expect(defaults.jobConfigs.analyzer.packageCurationProviders).toEqual([
    'ClearlyDefined',
  ]);
  expect(defaults.jobConfigs.analyzer.packageCurationProviderConfig).toEqual({
    ClearlyDefined: {
      options: {
        serverUrl: 'https://api.clearlydefined.io',
      },
      secrets: {
        token: 'clearly-defined-token',
      },
    },
  });
});

it('uses package configuration provider plugin default values for fresh runs', () => {
  const defaults = defaultValues(
    null,
    [],
    [],
    false,
    [],
    [
      createPluginDescriptor({
        id: 'OrtConfig',
        type: 'PACKAGE_CONFIGURATION_PROVIDER',
        options: [
          {
            name: 'path',
            description: 'Provider path.',
            type: 'STRING',
            defaultValue: 'package-configurations.yml',
            isFixed: true,
            isNullable: false,
            isRequired: true,
          },
        ],
      }),
    ]
  );

  expect(defaults.jobConfigs.evaluator.packageConfigurationProviders).toEqual(
    []
  );
  expect(
    defaults.jobConfigs.evaluator.packageConfigurationProviderConfig
  ).toEqual({
    OrtConfig: {
      options: {
        path: 'package-configurations.yml',
      },
      secrets: {},
    },
  });
  expect(defaults.jobConfigs.reporter.packageConfigurationProviders).toEqual(
    []
  );
  expect(
    defaults.jobConfigs.reporter.packageConfigurationProviderConfig
  ).toEqual({
    OrtConfig: {
      options: {
        path: 'package-configurations.yml',
      },
      secrets: {},
    },
  });
});

it('uses package curation provider plugin default values for fresh runs', () => {
  const defaults = defaultValues(
    null,
    [],
    [],
    false,
    [
      createPluginDescriptor({
        id: 'ClearlyDefined',
        type: 'PACKAGE_CURATION_PROVIDER',
        options: [
          {
            name: 'serverUrl',
            description: 'Backend URL.',
            type: 'STRING',
            defaultValue: 'https://api.clearlydefined.io',
            isFixed: true,
            isNullable: false,
            isRequired: true,
          },
        ],
      }),
    ],
    []
  );

  expect(defaults.jobConfigs.analyzer.packageCurationProviders).toEqual([]);
  expect(defaults.jobConfigs.analyzer.packageCurationProviderConfig).toEqual({
    ClearlyDefined: {
      options: {
        serverUrl: 'https://api.clearlydefined.io',
      },
      secrets: {},
    },
  });
});

it('uses scanner plugin default values for fresh runs', () => {
  const defaults = defaultValues(
    null,
    [],
    [
      createPluginDescriptor({
        id: 'DOS',
        type: 'SCANNER',
        options: [
          {
            name: 'url',
            description: 'Backend URL.',
            type: 'STRING',
            defaultValue: 'https://dos-api.example/api/',
            isFixed: true,
            isNullable: false,
            isRequired: true,
          },
        ],
      }),
    ],
    false,
    [],
    []
  );

  expect(defaults.jobConfigs.scanner.config).toEqual({
    DOS: {
      options: {
        url: 'https://dos-api.example/api/',
      },
      secrets: {},
    },
  });
});

it('applies fixed plugin option precedence for advisor and scanner reruns', () => {
  const ortRun = createOrtRun({
    jobConfigs: {
      advisor: {
        advisors: ['OSV'],
        config: {
          OSV: {
            options: {
              url: 'https://rerun.example/advisor',
              fixedNoDefault: 'legacy-advisor-fixed',
              token: 'legacy-advisor-token',
            },
            secrets: {
              fixedSecret: 'legacy-advisor-secret',
            },
          },
        },
      },
      scanner: {
        scanners: ['SCANOSS'],
        config: {
          SCANOSS: {
            options: {
              url: 'https://rerun.example/scanner',
              fixedNoDefault: 'legacy-scanner-fixed',
              token: 'legacy-scanner-token',
            },
            secrets: {
              fixedSecret: 'legacy-scanner-secret',
            },
          },
        },
      },
    },
  });

  const advisorPlugins = [
    createPluginDescriptor({
      id: 'OSV',
      type: 'ADVISOR',
      options: [
        {
          name: 'url',
          description: 'Base URL.',
          type: 'STRING',
          defaultValue: 'https://default.example/advisor',
          isFixed: true,
          isNullable: false,
          isRequired: true,
        },
        {
          name: 'fixedNoDefault',
          description: 'Fixed without default.',
          type: 'STRING',
          isFixed: true,
          isNullable: false,
          isRequired: false,
        },
        {
          name: 'fixedSecret',
          description: 'Fixed secret option.',
          type: 'SECRET',
          isFixed: true,
          isNullable: false,
          isRequired: false,
        },
        {
          name: 'token',
          description: 'Editable token.',
          type: 'STRING',
          defaultValue: 'default-token',
          isFixed: false,
          isNullable: false,
          isRequired: false,
        },
      ],
    }),
  ];

  const scannerPlugins = [
    createPluginDescriptor({
      id: 'SCANOSS',
      type: 'SCANNER',
      options: [
        {
          name: 'url',
          description: 'Base URL.',
          type: 'STRING',
          defaultValue: 'https://default.example/scanner',
          isFixed: true,
          isNullable: false,
          isRequired: true,
        },
        {
          name: 'fixedNoDefault',
          description: 'Fixed without default.',
          type: 'STRING',
          isFixed: true,
          isNullable: false,
          isRequired: false,
        },
        {
          name: 'fixedSecret',
          description: 'Fixed secret option.',
          type: 'SECRET',
          isFixed: true,
          isNullable: false,
          isRequired: false,
        },
        {
          name: 'token',
          description: 'Editable token.',
          type: 'STRING',
          defaultValue: 'default-token',
          isFixed: false,
          isNullable: false,
          isRequired: false,
        },
      ],
    }),
  ];

  const defaults = defaultValues(
    ortRun,
    advisorPlugins,
    scannerPlugins,
    false,
    [],
    []
  );

  expect(defaults.jobConfigs.advisor.config).toEqual({
    OSV: {
      options: {
        url: 'https://default.example/advisor',
        token: 'legacy-advisor-token',
      },
      secrets: {},
    },
  });

  expect(defaults.jobConfigs.scanner.config).toEqual({
    SCANOSS: {
      options: {
        url: 'https://default.example/scanner',
        token: 'legacy-scanner-token',
      },
      secrets: {},
    },
  });
});
