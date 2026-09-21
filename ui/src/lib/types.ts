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

import { z } from 'zod';

import { PluginType, RepositoryType } from '@/api';
import { zEnvironmentConfig } from '@/api/zod.gen';

/**
 * Redefine or extend some data types coming from the OpenAPI Query Client for UI purposes.
 * Also define types and constants for UI usage which are not included in the query client.
 */

// Schemas, consts and types for environment definitions leveraging the auto-generated
// EnvironmentConfig.

export enum NpmAuthMode {
  PASSWORD = 'PASSWORD',
  PASSWORD_BASE64 = 'PASSWORD_BASE64',
  PASSWORD_AUTH = 'PASSWORD_AUTH',
  PASSWORD_AUTH_TOKEN = 'PASSWORD_AUTH_TOKEN',
  USERNAME_PASSWORD_AUTH = 'USERNAME_PASSWORD_AUTH',
}

export const npmAuthModes = Object.values(NpmAuthMode) as NpmAuthMode[];

const environmentDefinitionsBase =
  zEnvironmentConfig.shape.environmentDefinitions.unwrap();

export type EnvironmentDefinitions = z.infer<typeof environmentDefinitionsBase>;
export type EnvironmentDefinitionEntry =
  EnvironmentDefinitions[keyof EnvironmentDefinitions][number];

const npmEnvironmentDefinition = z
  .object({
    service: z.string(),
    authMode: z.enum(NpmAuthMode),
    scope: z.string().optional(),
    email: z.string().optional(),
  })
  .catchall(z.string());

const conanEnvironmentDefinition = z
  .object({
    service: z.string(),
    name: z.string(),
    url: z.string().optional(),
    verifySsl: z.string().optional(),
  })
  .catchall(z.string());

const gradleEnvironmentDefinition = z
  .object({ service: z.string() })
  .catchall(z.string());

const mavenEnvironmentDefinition = z
  .object({
    service: z.string(),
    id: z.string(),
    mirrorOf: z.string().optional(),
  })
  .catchall(z.string());

export enum NuGetAuthMode {
  PASSWORD = 'PASSWORD',
  API_KEY = 'API_KEY',
}

export const nugetAuthModes = Object.values(NuGetAuthMode) as NuGetAuthMode[];

const nugetEnvironmentDefinition = z
  .object({
    service: z.string(),
    sourceName: z.string(),
    sourcePath: z.string(),
    sourceProtocolVersion: z.string().optional(),
    authMode: z.enum(NuGetAuthMode),
  })
  .catchall(z.string());

export enum YarnAuthMode {
  AUTH_IDENT = 'AUTH_IDENT',
  AUTH_TOKEN = 'AUTH_TOKEN',
}

export const yarnAuthModes = Object.values(YarnAuthMode) as YarnAuthMode[];

const yarnEnvironmentDefinition = z
  .object({
    service: z.string(),
    authMode: z.enum(YarnAuthMode),
    alwaysAuth: z.string().optional(),
  })
  .catchall(z.string());

const environmentDefinitionValidators: Record<string, z.ZodTypeAny> = {
  conan: conanEnvironmentDefinition,
  gradle: gradleEnvironmentDefinition,
  maven: mavenEnvironmentDefinition,
  npm: npmEnvironmentDefinition,
  nuget: nugetEnvironmentDefinition,
  yarn: yarnEnvironmentDefinition,
};

export const environmentDefinitionsSchema =
  environmentDefinitionsBase.superRefine((definitions, ctx) => {
    for (const [pluginId, entries] of Object.entries(definitions)) {
      entries.forEach((entry, index) => {
        if (!entry.service) {
          ctx.addIssue({
            code: 'custom',
            message: 'Service is required',
            path: [pluginId, index, 'service'],
          });
        }

        const validator =
          environmentDefinitionValidators[pluginId.toLowerCase()];
        const result = validator?.safeParse(entry);

        if (result && !result.success) {
          for (const issue of result.error.issues) {
            ctx.addIssue({
              ...issue,
              path: [pluginId, index, ...issue.path],
            });
          }
        }
      });
    }
  });

export const npmEnvironmentDefinitions: EnvironmentDefinitions = {
  npm: [
    {
      service: '',
      scope: '',
      email: '',
      authMode: NpmAuthMode.PASSWORD,
    },
  ],
};

export const conanEnvironmentDefinitions: EnvironmentDefinitions = {
  conan: [
    {
      service: '',
      name: '',
      url: '',
      verifySsl: 'true',
    },
  ],
};

export const gradleEnvironmentDefinitions: EnvironmentDefinitions = {
  gradle: [{ service: '' }],
};

export const mavenEnvironmentDefinitions: EnvironmentDefinitions = {
  maven: [{ service: '', id: '', mirrorOf: '' }],
};

export const nugetEnvironmentDefinitions: EnvironmentDefinitions = {
  nuget: [
    {
      service: '',
      sourceName: '',
      sourcePath: '',
      sourceProtocolVersion: '',
      authMode: NuGetAuthMode.API_KEY,
    },
  ],
};

export const yarnEnvironmentDefinitions: EnvironmentDefinitions = {
  yarn: [
    {
      service: '',
      authMode: YarnAuthMode.AUTH_TOKEN,
      alwaysAuth: 'true',
    },
  ],
};

// Some types coming from the query client need to be shown in a more user-friendly way.

const repositoryTypeLabels: Record<RepositoryType, string> = {
  GIT: 'Git',
  GIT_REPO: 'Git-Repo',
  MERCURIAL: 'Mercurial',
  SUBVERSION: 'Subversion',
};

export function getRepositoryTypeLabel(type: RepositoryType | string): string {
  if (type in repositoryTypeLabels) {
    // TS narrows `type` to RepositoryType because of the Record key check.
    return repositoryTypeLabels[type as RepositoryType];
  }
  return type ? `"${type}"` : 'Unset';
}

const pluginTypeLabels: Record<PluginType, string> = {
  ADVISOR: 'Advisor',
  PACKAGE_CONFIGURATION_PROVIDER: 'Package Configuration Provider',
  PACKAGE_CURATION_PROVIDER: 'Package Curation Provider',
  PACKAGE_MANAGER: 'Package Manager',
  REPORTER: 'Reporter',
  SCANNER: 'Scanner',
};

export function getPluginTypeLabel(type: PluginType | string): string {
  if (type in pluginTypeLabels) {
    return pluginTypeLabels[type as PluginType];
  }
  return type ? `"${type}"` : 'Unknown';
}

// Type and constant for color themes. New themes can be added to the project
// by extending these definitions, and adding the corresponding CSS definitions
// and labeling them with the `data-theme` attribute.
export type ColorTheme = 'material-design' | 'shadcn' | 'default';
export const colorThemes: ColorTheme[] = [
  'material-design',
  'shadcn',
  'default',
];
