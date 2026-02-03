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

import { RepositoryType } from '@/api';
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

const environmentDefinitionValidators: Record<string, z.ZodTypeAny> = {
  npm: npmEnvironmentDefinition,
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

// Some input types for creating runs are merely strings in the API, but they actually take a
// limited set of values. Define the possible values here to use in the UI.
// Note: with the upcoming plugin support, some of these types may be deprecated or changed.

export const packageManagers = [
  {
    id: 'Bazel',
    label: 'Bazel (C++, Java, and others)',
  },
  {
    id: 'Bower',
    label: 'Bower (JavaScript / Node.js)',
  },
  {
    id: 'Bundler',
    label: 'Bundler (Ruby)',
  },
  {
    id: 'Cargo',
    label: 'Cargo (Rust)',
  },
  {
    id: 'Carthage',
    label: 'Carthage (Objective-C / Swift)',
  },
  {
    id: 'CocoaPods',
    label: 'CocoaPods (Objective-C / Swift)',
  },
  {
    id: 'Composer',
    label: 'Composer (PHP)',
  },
  {
    id: 'Conan',
    label: 'Conan 1.x (C / C++)',
  },
  {
    id: 'GoMod',
    label: 'GoMod (Go)',
  },
  {
    id: 'Gradle',
    label: 'Gradle Legacy (Java), mutually exclusive with Gradle',
  },
  {
    id: 'GradleInspector',
    label: 'Gradle (Java), mutually exclusive with Gradle Legacy',
  },
  {
    id: 'Maven',
    label: 'Maven (Java)',
  },
  {
    id: 'NPM',
    label: 'NPM (JavaScript / Node.js)',
  },
  {
    id: 'NuGet',
    label: 'NuGet (C# and DotNet in general)',
  },
  {
    id: 'PIP',
    label: 'PIP (Python)',
  },
  {
    id: 'Pipenv',
    label: 'Pipenv (Python)',
  },
  {
    id: 'PNPM',
    label: 'PNPM (JavaScript / Node.js)',
  },
  {
    id: 'Poetry',
    label: 'Poetry (Python)',
  },
  {
    id: 'Pub',
    label: 'Pub (Dart / Flutter)',
  },
  {
    id: 'SBT',
    label: 'SBT (Scala)',
  },
  {
    id: 'SpdxDocumentFile',
    label: 'SPDX Document File',
  },
  {
    id: 'Stack',
    label: 'Stack (Haskell)',
  },
  {
    id: 'SwiftPM',
    label: 'SwiftPM (Swift)',
  },
  {
    id: 'Tycho',
    label: 'Tycho (Java)',
  },
  {
    id: 'Yarn',
    label: 'Yarn 1 (JavaScript / Node.js)',
  },
  {
    id: 'Yarn2',
    label: 'Yarn 2+ (JavaScript / Node.js)',
  },
] as const;

// To make sure the package manager ids are used type-safely elsewhere,
// export them as a type.
export type PackageManagerId = (typeof packageManagers)[number]['id'];

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

// Type and constant for color themes. New themes can be added to the project
// by extending these definitions, and adding the corresponding CSS definitions
// and labeling them with the `data-theme` attribute.
export type ColorTheme = 'material-design' | 'shadcn' | 'default';
export const colorThemes: ColorTheme[] = [
  'material-design',
  'shadcn',
  'default',
];
