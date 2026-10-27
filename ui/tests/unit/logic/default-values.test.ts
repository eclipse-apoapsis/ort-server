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

import type { OrtRun, PluginType } from '@/api';
import {
  createDefaultPlugins,
  createFormDefaultValues,
  createOrtRun,
  createPluginDescriptor,
} from '../fixtures/create-run';

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

  const defaults = createFormDefaultValues({ ortRun });

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

  const defaults = createFormDefaultValues({ ortRun });

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

  const defaults = createFormDefaultValues({ ortRun });

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
  const defaults = createFormDefaultValues({
    plugins: {
      packageConfigurationProviderPlugins: [
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
      ],
    },
  });

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
  const defaults = createFormDefaultValues({
    plugins: {
      packageCurationProviderPlugins: [
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
    },
  });

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
  const defaults = createFormDefaultValues({
    plugins: {
      scannerPlugins: [
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
    },
  });

  expect(defaults.jobConfigs.scanner.config).toEqual({
    DOS: {
      options: {
        url: 'https://dos-api.example/api/',
      },
      secrets: {},
    },
  });
});

it('uses reporter plugin default values for fresh runs', () => {
  const defaults = createFormDefaultValues({
    plugins: {
      reporterPlugins: [
        createPluginDescriptor({
          id: 'WebApp',
          type: 'REPORTER',
          options: [
            {
              name: 'deduplicateDependencyTree',
              description: 'Deduplicate the dependency tree.',
              type: 'BOOLEAN',
              defaultValue: 'true',
              isFixed: true,
              isNullable: false,
              isRequired: false,
            },
          ],
        }),
      ],
    },
  });

  expect(defaults.jobConfigs.reporter.config).toEqual({
    WebApp: {
      options: {
        deduplicateDependencyTree: true,
      },
      secrets: {},
    },
  });
});

it('applies fixed plugin option precedence for advisor, scanner, and reporter reruns', () => {
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
      reporter: {
        formats: ['WebApp'],
        config: {
          WebApp: {
            options: {
              url: 'https://rerun.example/reporter',
              fixedNoDefault: 'legacy-reporter-fixed',
              token: 'legacy-reporter-token',
            },
            secrets: {
              fixedSecret: 'legacy-reporter-secret',
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

  const reporterPlugins = [
    createPluginDescriptor({
      id: 'WebApp',
      type: 'REPORTER',
      options: [
        {
          name: 'url',
          description: 'Base URL.',
          type: 'STRING',
          defaultValue: 'https://default.example/reporter',
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

  const defaults = createFormDefaultValues({
    ortRun,
    plugins: { advisorPlugins, scannerPlugins, reporterPlugins },
  });

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

  expect(defaults.jobConfigs.reporter.config).toEqual({
    WebApp: {
      options: {
        url: 'https://default.example/reporter',
        token: 'legacy-reporter-token',
      },
      secrets: {},
    },
  });
});

const pluginsOfType = (type: PluginType, ...ids: string[]) =>
  ids.map((id) => createPluginDescriptor({ id, type }));

const defaultPluginsTestValues = (ortRun: OrtRun | null) =>
  createFormDefaultValues({
    ortRun,
    plugins: {
      advisorPlugins: pluginsOfType(
        'ADVISOR',
        'OSV',
        'VulnerableCode',
        'Other'
      ),
      scannerPlugins: pluginsOfType('SCANNER', 'ScanCode', 'Other'),
      reporterPlugins: pluginsOfType(
        'REPORTER',
        'CycloneDX',
        'WebApp',
        'Other'
      ),
      packageCurationProviderPlugins: pluginsOfType(
        'PACKAGE_CURATION_PROVIDER',
        'ClearlyDefined',
        'Other'
      ),
      packageConfigurationProviderPlugins: pluginsOfType(
        'PACKAGE_CONFIGURATION_PROVIDER',
        'OrtConfig',
        'Other'
      ),
      packageManagerPlugins: pluginsOfType('PACKAGE_MANAGER', 'Maven', 'Other'),
    },
    defaultPlugins: createDefaultPlugins({
      PACKAGE_CURATION_PROVIDER: ['ClearlyDefined'],
      PACKAGE_CONFIGURATION_PROVIDER: ['OrtConfig'],
    }),
  });

it('enables the available default plugins for fresh runs', () => {
  const { jobConfigs } = defaultPluginsTestValues(null);

  expect(jobConfigs.analyzer.packageManagers).toEqual(['Maven']);
  expect(jobConfigs.analyzer.packageCurationProviders).toEqual([
    'ClearlyDefined',
  ]);
  expect(jobConfigs.advisor.advisors).toEqual(['OSV', 'VulnerableCode']);
  expect(jobConfigs.scanner.scanners).toEqual(['ScanCode']);
  expect(jobConfigs.scanner.scannerScopes).toEqual({ ScanCode: 'both' });
  expect(jobConfigs.evaluator.packageConfigurationProviders).toEqual([
    'OrtConfig',
  ]);
  // The default reporter "SpdxDocument" is not available.
  expect(jobConfigs.reporter.formats).toEqual(['CycloneDX', 'WebApp']);
  expect(jobConfigs.reporter.packageConfigurationProviders).toEqual([
    'OrtConfig',
  ]);
});

const scannerDefaultValues = (scanners: string[], projectScanners: string[]) =>
  createFormDefaultValues({
    plugins: {
      scannerPlugins: pluginsOfType('SCANNER', 'ScanCode', 'SCANOSS', 'Other'),
    },
    defaultPlugins: createDefaultPlugins({
      SCANNER: scanners,
      PROJECT_SCANNER: projectScanners,
    }),
  }).jobConfigs.scanner;

it('sets the scopes of the default scanners from the default project scanners', () => {
  const scanner = scannerDefaultValues(
    ['ScanCode', 'Other'],
    ['SCANOSS', 'ScanCode']
  );

  expect(scanner.scanners).toEqual(['ScanCode', 'Other', 'SCANOSS']);
  expect(scanner.scannerScopes).toEqual({
    ScanCode: 'both',
    Other: 'packages',
    SCANOSS: 'projects',
  });
});

it('scans packages and projects with the default scanners if the project scanners are the same', () => {
  const scanner = scannerDefaultValues(
    ['ScanCode', 'SCANOSS'],
    ['SCANOSS', 'ScanCode']
  );

  expect(scanner.scanners).toEqual(['ScanCode', 'SCANOSS']);
  expect(scanner.scannerScopes).toEqual({ ScanCode: 'both', SCANOSS: 'both' });
});

it('scans only packages with the default scanners if there are no default project scanners', () => {
  const scanner = scannerDefaultValues(['ScanCode'], []);

  expect(scanner.scanners).toEqual(['ScanCode']);
  expect(scanner.scannerScopes).toEqual({ ScanCode: 'packages' });
});

it('uses the default plugins for plugin lists that are not set in reruns', () => {
  const ortRun = createOrtRun({
    jobConfigs: {
      analyzer: {},
      advisor: {},
      scanner: {},
      evaluator: {},
      reporter: {},
    },
  });

  const { jobConfigs } = defaultPluginsTestValues(ortRun);

  expect(jobConfigs.analyzer.packageManagers).toEqual(['Maven']);
  expect(jobConfigs.analyzer.packageCurationProviders).toEqual([
    'ClearlyDefined',
  ]);
  expect(jobConfigs.advisor.advisors).toEqual(['OSV', 'VulnerableCode']);
  expect(jobConfigs.scanner.scanners).toEqual(['ScanCode']);
  expect(jobConfigs.evaluator.packageConfigurationProviders).toEqual([
    'OrtConfig',
  ]);
  expect(jobConfigs.reporter.formats).toEqual(['CycloneDX', 'WebApp']);
  expect(jobConfigs.reporter.packageConfigurationProviders).toEqual([
    'OrtConfig',
  ]);
});

it('keeps empty provider lists from reruns', () => {
  const ortRun = createOrtRun({
    jobConfigs: {
      analyzer: { packageCurationProviders: [] },
      evaluator: { packageConfigurationProviders: [] },
      reporter: { packageConfigurationProviders: [] },
    },
  });

  const { jobConfigs } = defaultPluginsTestValues(ortRun);

  expect(jobConfigs.analyzer.packageCurationProviders).toEqual([]);
  expect(jobConfigs.evaluator.packageConfigurationProviders).toEqual([]);
  expect(jobConfigs.reporter.packageConfigurationProviders).toEqual([]);
});

it('takes the includes and excludes from reruns, excludes first', () => {
  const ortRun = createOrtRun({
    scopes: {
      includes: [{ pattern: 'releaseRuntimeClasspath', reason: 'SOURCE_OF' }],
      excludes: [
        { pattern: 'test.*', reason: 'TEST_DEPENDENCY_OF', comment: 'Tests' },
      ],
    },
    paths: { excludes: [{ pattern: 'docs/**', reason: 'DOCUMENTATION_OF' }] },
  });

  const defaults = createFormDefaultValues({ ortRun });

  expect(defaults.scopes).toEqual([
    {
      direction: 'exclude',
      pattern: 'test.*',
      reason: 'TEST_DEPENDENCY_OF',
      comment: 'Tests',
    },
    {
      direction: 'include',
      pattern: 'releaseRuntimeClasspath',
      reason: 'SOURCE_OF',
      comment: '',
    },
  ]);
  expect(defaults.paths).toEqual([
    {
      direction: 'exclude',
      pattern: 'docs/**',
      reason: 'DOCUMENTATION_OF',
      comment: '',
    },
  ]);
});
