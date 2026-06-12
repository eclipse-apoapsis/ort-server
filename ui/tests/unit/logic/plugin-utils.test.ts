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

import { ADMIN_SECRET_VALUE } from '@/components/form/plugin-multi-select-field-utils';
import {
  createPluginPayload,
  createProviderPluginPayload,
  getPluginDefaultValues,
  mergePluginConfigs,
  providerPluginConfigsToFormValues,
  reconstructScannerSelection,
} from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/create-run/-components/plugin-utils';
import { createPluginDescriptor } from '../fixtures/create-run';

it('createPluginPayload omits blank secret values from the payload', () => {
  const payload = createPluginPayload(
    {
      SCANOSS: {
        options: {
          apiUrl: 'https://api.osskb.org',
        },
        secrets: {
          apiKey: '',
        },
      },
    },
    ['SCANOSS']
  );

  expect(payload).toEqual({
    SCANOSS: {
      options: {
        apiUrl: 'https://api.osskb.org',
      },
      secrets: {},
    },
  });
});

it('createProviderPluginPayload creates provider configurations for selected plugins', () => {
  const payload = createProviderPluginPayload(
    {
      ClearlyDefined: {
        options: {
          serverUrl: 'https://api.clearlydefined.io',
        },
        secrets: {
          token: '',
        },
      },
      File: {
        options: {
          path: 'curations.yml',
        },
        secrets: {},
      },
    },
    ['ClearlyDefined']
  );

  expect(payload).toEqual([
    {
      type: 'ClearlyDefined',
      id: 'ClearlyDefined',
      enabled: true,
      options: {
        serverUrl: 'https://api.clearlydefined.io',
      },
    },
  ]);
});

it('getPluginDefaultValues initializes admin-provided secret options with the admin placeholder', () => {
  const defaults = getPluginDefaultValues([
    createPluginDescriptor({
      id: 'SCANOSS',
      options: [
        {
          name: 'apiKey',
          description: 'The API key.',
          type: 'SECRET',
          // A non-null default value for a secret option indicates that an administrator provided
          // a value via a plugin template.
          defaultValue: '[set by admin]',
          isFixed: false,
          isNullable: false,
          isRequired: false,
        },
      ],
    }),
  ]);

  expect(defaults.SCANOSS?.secrets).toEqual({ apiKey: ADMIN_SECRET_VALUE });
});

it('getPluginDefaultValues does not initialize secret options without a default value', () => {
  const defaults = getPluginDefaultValues([
    createPluginDescriptor({
      id: 'SCANOSS',
      options: [
        {
          name: 'apiKey',
          description: 'The API key.',
          type: 'SECRET',
          defaultValue: null,
          isFixed: false,
          isNullable: false,
          isRequired: false,
        },
      ],
    }),
  ]);

  expect(defaults.SCANOSS?.secrets).toEqual({});
});

it('createPluginPayload omits admin-provided secret placeholders from the payload', () => {
  const payload = createPluginPayload(
    {
      SCANOSS: {
        options: {},
        secrets: {
          apiKey: ADMIN_SECRET_VALUE,
        },
      },
    },
    ['SCANOSS']
  );

  expect(payload).toEqual({
    SCANOSS: {
      options: {},
      secrets: {},
    },
  });
});

it('providerPluginConfigsToFormValues reconstructs selected providers and config', () => {
  const formValues = providerPluginConfigsToFormValues([
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
    {
      type: 'File',
      id: 'File',
      enabled: false,
    },
  ]);

  expect(formValues).toEqual({
    selectedPluginIds: ['ClearlyDefined'],
    config: {
      ClearlyDefined: {
        options: {
          serverUrl: 'https://api.clearlydefined.io',
        },
        secrets: {
          token: 'clearly-defined-token',
        },
      },
      File: {
        options: {},
        secrets: {},
      },
    },
  });
});

it('reconstructScannerSelection rebuilds scanner scopes for rerun values', () => {
  const defaults = {
    scanners: ['ScanCode'],
    scannerScopes: {
      ScanCode: 'both' as const,
    },
  };

  const result = reconstructScannerSelection(
    ['SCANOSS'],
    ['SCANOSS'],
    defaults
  );

  expect(result).toEqual({
    scanners: ['SCANOSS'],
    scannerScopes: {
      SCANOSS: 'both',
    },
  });
});

it('mergePluginConfigs enforces fixed option precedence and clears missing fixed defaults', () => {
  const plugins = [
    createPluginDescriptor({
      id: 'SCANOSS',
      type: 'SCANNER',
      options: [
        {
          name: 'url',
          description: 'The API URL.',
          type: 'STRING',
          defaultValue: 'https://scanner.example/api',
          isFixed: true,
          isNullable: false,
          isRequired: false,
        },
        {
          name: 'fixedNoDefault',
          description: 'A fixed option without default.',
          type: 'STRING',
          isFixed: true,
          isNullable: false,
          isRequired: false,
        },
        {
          name: 'fixedSecret',
          description: 'A fixed secret option.',
          type: 'SECRET',
          defaultValue: 'admin-provided',
          isFixed: true,
          isNullable: false,
          isRequired: true,
        },
        {
          name: 'timeout',
          description: 'Timeout in seconds.',
          type: 'INTEGER',
          defaultValue: '30',
          isFixed: false,
          isNullable: false,
          isRequired: false,
        },
      ],
    }),
  ];

  const merged = mergePluginConfigs(
    {
      SCANOSS: {
        options: {
          url: 'https://old.example/api',
          fixedNoDefault: 'legacy-value',
          timeout: '90',
        },
        secrets: {
          fixedSecret: 'legacy-secret',
        },
      },
    },
    getPluginDefaultValues(plugins),
    plugins
  );

  expect(merged.SCANOSS).toEqual({
    options: {
      url: 'https://scanner.example/api',
      timeout: '90',
    },
    secrets: {
      fixedSecret: ADMIN_SECRET_VALUE,
    },
  });
});
