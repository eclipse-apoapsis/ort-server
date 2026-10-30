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

import { createContext, useContext } from 'react';

import {
  JobConfigurations,
  PluginType,
  PreconfiguredPluginDescriptor,
} from '@/api';
import { ENVIRONMENT_DEFINITION_SCHEMAS } from '@/lib/environment-definition-fields';
import { JobConfigField, jobConfigFields } from '@/lib/job-config-fields';

export const jobSections = [
  { value: 'analyzer', title: 'Analyzer' },
  { value: 'advisor', title: 'Advisor' },
  { value: 'scanner', title: 'Scanner' },
  { value: 'evaluator', title: 'Evaluator' },
  { value: 'reporter', title: 'Reporter' },
  { value: 'notifier', title: 'Notifier' },
] as const;

export type ConfigPath = (string | number)[];

/**
 * The settings of a job that have no effect for the run, which are not shown. The reporter uses
 * the package configuration providers of the evaluator if the run has one, as on the create-run
 * form.
 */
export const hiddenJobKeys = (jobConfigs: JobConfigurations, job: string) =>
  job === 'reporter' && jobConfigs.evaluator
    ? ['packageConfigurationProviders']
    : [];

type NamedValue = { name: string; value?: string | null; secretName?: string };

export const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

export const isStringMap = (value: unknown): value is Record<string, string> =>
  isRecord(value) &&
  Object.values(value).every((entry) => typeof entry === 'string');

/**
 * Whether the value is a list of names with a literal value or a secret, like the environment
 * variables of the analyzer.
 */
export const isNamedValueList = (value: unknown): value is NamedValue[] =>
  Array.isArray(value) &&
  value.length > 0 &&
  value.every(
    (entry) =>
      isRecord(entry) &&
      typeof entry.name === 'string' &&
      Object.keys(entry).every((key) =>
        ['name', 'value', 'secretName'].includes(key)
      )
  );

export const isEmpty = (value: unknown) =>
  value === null ||
  value === undefined ||
  value === '' ||
  (Array.isArray(value) && value.length === 0) ||
  (isRecord(value) && Object.keys(value).length === 0);

/**
 * Whether the value is shown as a whole in one row, rather than as a group of nested rows.
 */
export const isLeafValue = (value: unknown): boolean =>
  isEmpty(value) ||
  typeof value !== 'object' ||
  (Array.isArray(value) &&
    value.every((entry) => typeof entry !== 'object' || entry === null)) ||
  isStringMap(value) ||
  isNamedValueList(value);

export const lastKey = (path: ConfigPath) => {
  const key = path.at(-1);
  return typeof key === 'string' ? key : '';
};

/**
 * Whether the string at this path names a secret instead of being a value itself.
 */
export const isSecretPath = (path: ConfigPath) => {
  const key = lastKey(path);
  return (
    key === 'secretName' ||
    key.endsWith('SecretRef') ||
    path.at(-2) === 'secrets'
  );
};

export const isMonospacePath = (path: ConfigPath) =>
  /path$|pattern$/i.test(lastKey(path));

/**
 * The descriptors of the plugins available to the repository, used to name plugins and to
 * explain their options. Empty while they are loading or if they could not be loaded.
 */
export const PluginDescriptorsContext = createContext<
  PreconfiguredPluginDescriptor[]
>([]);

const fieldPatterns = Object.entries(jobConfigFields).map(
  ([pattern, field]) => ({
    segments: pattern.split('.'),
    field: field as JobConfigField,
  })
);

const environmentDefinitionsPath = [
  'analyzer',
  'environmentConfig',
  'environmentDefinitions',
];

/**
 * The plugin collections in the job configurations, with the type of their plugins. A map is
 * keyed by plugin id, a list names the plugin of each entry in `type`.
 */
export const pluginCollections: Record<string, PluginType> = {
  'analyzer.packageManagerOptions': 'PACKAGE_MANAGER',
  'analyzer.packageCurationProviders': 'PACKAGE_CURATION_PROVIDER',
  'advisor.config': 'ADVISOR',
  'scanner.config': 'SCANNER',
  'evaluator.packageConfigurationProviders': 'PACKAGE_CONFIGURATION_PROVIDER',
  'reporter.config': 'REPORTER',
  'reporter.packageConfigurationProviders': 'PACKAGE_CONFIGURATION_PROVIDER',
};

/** The lists of plugin ids in the job configurations, with the type of their plugins. */
const pluginLists: Record<string, PluginType> = {
  'analyzer.enabledPackageManagers': 'PACKAGE_MANAGER',
  'analyzer.disabledPackageManagers': 'PACKAGE_MANAGER',
  'advisor.advisors': 'ADVISOR',
  'scanner.scanners': 'SCANNER',
  'scanner.projectScanners': 'SCANNER',
  'reporter.formats': 'REPORTER',
};

/**
 * Get the text that stands for an entry of the list at the path: the name of a plugin as on the
 * create-run form, or the entry itself. A plugin without a descriptor keeps its id.
 */
export const useListEntryName = (path: ConfigPath) => {
  const plugins = useContext(PluginDescriptorsContext);
  const type = pluginLists[path.join('.')];

  return (entry: string) =>
    plugins.find((plugin) => plugin.type === type && plugin.id === entry)
      ?.displayName ?? entry;
};

const startsWith = (path: ConfigPath, prefix: ConfigPath) =>
  path.length >= prefix.length &&
  prefix.every((segment, index) => path[index] === segment);

/**
 * Make a label from a key, for example "Asset files groups" from `assetFilesGroups`.
 */
export const labelFromKey = (key: string) => {
  const words = key
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/[_-]+/g, ' ')
    .toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
};

type FieldMetadata = JobConfigField & { position: number };

const findEnvironmentDefinitionField = (
  path: ConfigPath
): FieldMetadata | undefined => {
  if (!startsWith(path, environmentDefinitionsPath)) return undefined;

  const [packageManager, , key] = path.slice(environmentDefinitionsPath.length);
  const schema = ENVIRONMENT_DEFINITION_SCHEMAS.find(
    (entry) => entry.key === packageManager
  );

  if (!schema) return undefined;

  if (path.length === environmentDefinitionsPath.length + 1) {
    return {
      label: schema.label,
      position: ENVIRONMENT_DEFINITION_SCHEMAS.indexOf(schema),
    };
  }

  if (path.length !== environmentDefinitionsPath.length + 3) return undefined;

  const position = schema.fields.findIndex((entry) => entry.key === key);
  const def = schema.fields[position]?.def;

  if (!def || def.type === 'service') return undefined;

  return { label: def.label, description: def.description, position };
};

/**
 * Find the label, help text and position of the setting at the given path.
 */
const findField = (path: ConfigPath): FieldMetadata | undefined => {
  const environmentDefinitionField = findEnvironmentDefinitionField(path);

  if (environmentDefinitionField) return environmentDefinitionField;

  const position = fieldPatterns.findIndex(
    ({ segments }) =>
      segments.length === path.length &&
      segments.every(
        (segment, index) => segment === '*' || segment === String(path[index])
      )
  );

  const match = fieldPatterns[position];

  return match && { ...match.field, position };
};

export const sortEntries = (entries: [string, unknown][], path: ConfigPath) =>
  entries
    .map(([key, value]) => {
      const field = findField([...path, key]);
      return {
        key,
        value,
        label: field?.label ?? labelFromKey(key),
        description: field?.description,
        position: field?.position ?? Number.POSITIVE_INFINITY,
      };
    })
    .sort(
      (first, second) =>
        first.position - second.position ||
        first.label.localeCompare(second.label)
    );
