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

import { ReactNode } from 'react';

import {
  conanEnvironmentDefinitions,
  EnvironmentDefinitionEntry,
  gradleEnvironmentDefinitions,
  mavenEnvironmentDefinitions,
  npmAuthModes,
  npmEnvironmentDefinitions,
  nugetAuthModes,
  nugetEnvironmentDefinitions,
  yarnAuthModes,
  yarnEnvironmentDefinitions,
} from '@/lib/types';

type ServiceFieldDef = { type: 'service' };

type StringFieldDef = {
  type: 'string';
  label: string;
  description: ReactNode;
  optional?: boolean;
};

type BooleanFieldDef = {
  type: 'boolean';
  label: string;
  description: ReactNode;
};

type EnumFieldDef = {
  type: 'enum';
  label: string;
  description: string;
  values: readonly string[];
  placeholder: string;
};

export type FieldDef =
  | ServiceFieldDef
  | StringFieldDef
  | BooleanFieldDef
  | EnumFieldDef;

export type FieldEntry = { key: string; def: FieldDef };

export type EnvironmentDefinitionSchema = {
  key: string;
  label: string;
  defaultEntries: EnvironmentDefinitionEntry[];
  fields: FieldEntry[];
};

export const ENVIRONMENT_DEFINITION_SCHEMAS: EnvironmentDefinitionSchema[] = [
  {
    key: 'conan',
    label: 'Conan',
    defaultEntries: conanEnvironmentDefinitions['conan'] ?? [],
    fields: [
      { key: 'service', def: { type: 'service' } },
      {
        key: 'name',
        def: {
          type: 'string',
          label: 'Remote name',
          description: (
            <>
              Name of the Conan remote, used in commands like{' '}
              <code>conan list</code>.
            </>
          ),
        },
      },
      {
        key: 'url',
        def: {
          type: 'string',
          label: 'URL',
          description:
            'URL for Conan to search for recipes and binaries. Falls back to the infrastructure service URL if not set.',
          optional: true,
        },
      },
      {
        key: 'verifySsl',
        def: {
          type: 'boolean',
          label: 'Verify SSL',
          description: 'Verify the SSL certificate of the remote URL.',
        },
      },
    ],
  },
  {
    key: 'gradle',
    label: 'Gradle',
    defaultEntries: gradleEnvironmentDefinitions['gradle'] ?? [],
    fields: [{ key: 'service', def: { type: 'service' } }],
  },
  {
    key: 'maven',
    label: 'Maven',
    defaultEntries: mavenEnvironmentDefinitions['maven'] ?? [],
    fields: [
      { key: 'service', def: { type: 'service' } },
      {
        key: 'id',
        def: {
          type: 'string',
          label: 'ID',
          description: (
            <>
              Repository ID referenced in <code>pom.xml</code> files. Appears as
              the <code>&lt;id&gt;</code> of the corresponding server in
              Maven&apos;s <code>settings.xml</code>.
            </>
          ),
        },
      },
      {
        key: 'mirrorOf',
        def: {
          type: 'string',
          label: 'Mirror of',
          description: (
            <>
              If set, adds this entry to the <code>&lt;mirrors&gt;</code>{' '}
              section of <code>settings.xml</code>. Value is the repository ID
              to mirror (e.g. <code>central</code>, or <code>*</code> for all
              repositories).
            </>
          ),
          optional: true,
        },
      },
    ],
  },
  {
    key: 'npm',
    label: 'NPM',
    defaultEntries: npmEnvironmentDefinitions['npm'] ?? [],
    fields: [
      { key: 'service', def: { type: 'service' } },
      {
        key: 'scope',
        def: {
          type: 'string',
          label: 'Scope',
          description: 'Optional NPM scope that this configuration applies to.',
          optional: true,
        },
      },
      {
        key: 'email',
        def: {
          type: 'string',
          label: 'Email',
          description: 'Optional email address used by the NPM registry.',
          optional: true,
        },
      },
      {
        key: 'authMode',
        def: {
          type: 'enum',
          label: 'Authorization mode',
          description: 'Pick how the NPM registry authenticates requests.',
          values: npmAuthModes,
          placeholder: 'Select the authorization mode',
        },
      },
    ],
  },
  {
    key: 'nuget',
    label: 'NuGet',
    defaultEntries: nugetEnvironmentDefinitions['nuget'] ?? [],
    fields: [
      { key: 'service', def: { type: 'service' } },
      {
        key: 'sourceName',
        def: {
          type: 'string',
          label: 'Source name',
          description: 'The name to assign to the package source.',
        },
      },
      {
        key: 'sourcePath',
        def: {
          type: 'string',
          label: 'Source path',
          description: 'The path or URL of the package source.',
        },
      },
      {
        key: 'sourceProtocolVersion',
        def: {
          type: 'string',
          label: 'Source protocol version',
          description:
            'NuGet server protocol version (e.g. 3). Defaults to 2 for non-JSON source URLs. Requires NuGet 3.0+.',
          optional: true,
        },
      },
      {
        key: 'authMode',
        def: {
          type: 'enum',
          label: 'Authorization mode',
          description: 'Authentication type for this package source.',
          values: nugetAuthModes,
          placeholder: 'Select the authorization mode',
        },
      },
    ],
  },
  {
    key: 'yarn',
    label: 'Yarn',
    defaultEntries: yarnEnvironmentDefinitions['yarn'] ?? [],
    fields: [
      { key: 'service', def: { type: 'service' } },
      {
        key: 'authMode',
        def: {
          type: 'enum',
          label: 'Authorization mode',
          description: 'Authentication method for this private registry.',
          values: yarnAuthModes,
          placeholder: 'Select the authorization mode',
        },
      },
      {
        key: 'alwaysAuth',
        def: {
          type: 'boolean',
          label: 'Always authenticate',
          description: (
            <>
              Always send authentication information to the registry via the{' '}
              <code>npmAlwaysAuth</code> property.
            </>
          ),
        },
      },
    ],
  },
];
