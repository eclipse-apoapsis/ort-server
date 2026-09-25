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

import { z, ZodType } from 'zod';

import {
  PackageManagerConfiguration,
  PluginConfig,
  PluginOptionType,
  PluginType,
  PreconfiguredPluginDescriptor,
  ProviderPluginConfiguration,
} from '@/api';
import { ADMIN_SECRET_VALUE } from '@/components/form/plugin-multi-select-field';

/**
 * The ID of the package manager which is always enabled and therefore neither selectable in the
 * form nor part of the package manager plugins offered by it.
 */
export const UNMANAGED_PACKAGE_MANAGER_ID = 'Unmanaged';

function isNonBlankString(value: unknown): value is string {
  return typeof value === 'string' && value.trim() !== '';
}

/** Convert a serialized plugin option value from the API to its form representation. */
function pluginOptionValueToFormValue(
  value: unknown,
  type: PluginOptionType
): string | boolean | string[] {
  switch (type) {
    case 'BOOLEAN':
      return typeof value === 'boolean' ? value : value === 'true';
    case 'ENUM_LIST':
    case 'STRING_LIST':
      if (Array.isArray(value)) return value as string[];

      return typeof value === 'string'
        ? value
            .split(',')
            .map((entry) => entry.trim())
            .filter(Boolean)
        : [];
    default:
      return String(value);
  }
}

function optionTypeToZodType(type: PluginOptionType): ZodType {
  switch (type) {
    case 'BOOLEAN':
      // Preprocess to coerce string representations ("true"/"false") that may
      // come from default values or API re-run data into actual booleans.
      return z.preprocess((val) => {
        if (typeof val === 'string') return val === 'true';
        return val;
      }, z.boolean());
    case 'ENUM':
      return z.string();
    case 'ENUM_LIST':
      // Preprocess to coerce the comma-separated representation used by the API
      // into the array representation used by the form.
      return z.preprocess((val) => {
        if (typeof val === 'string')
          return val
            .split(',')
            .map((s) => s.trim())
            .filter(Boolean);
        return val;
      }, z.array(z.string()));
    case 'INTEGER':
      return z.coerce.string();
    case 'LONG':
      return z.coerce.string();
    case 'SECRET':
      return z.string();
    case 'STRING':
      return z.string();
    case 'STRING_LIST':
      // Preprocess to coerce a comma-separated string into an array, handling
      // string representations that may come from default values or API re-run data.
      return z.preprocess((val) => {
        if (typeof val === 'string')
          return val
            .split(',')
            .map((s) => s.trim())
            .filter(Boolean);
        return val;
      }, z.array(z.string()));
    default:
      throw new Error(`Unsupported option type: ${type}`);
  }
}

/**
 * A superRefine validator that checks required options only for plugins that are actually
 * selected. Intended to be used with z.object(...).superRefine() on any job config object
 * that has a `config` map and a list of selected plugin IDs.
 *
 * @param plugins - All available plugin descriptors for this job type.
 * @param selectedPluginIds - The plugin IDs currently selected by the user.
 * @param config - The raw config map from the form data.
 * @param ctx - The Zod refinement context.
 */
export function validateRequiredPluginOptions(
  plugins: PreconfiguredPluginDescriptor[],
  selectedPluginIds: string[],
  config:
    | Record<string, Record<string, Record<string, unknown>> | undefined>
    | undefined,
  ctx: z.RefinementCtx,
  configPath: string | string[] = 'config'
): void {
  for (const plugin of plugins) {
    if (!selectedPluginIds.includes(plugin.id)) continue;

    const pluginConfig = config?.[plugin.id];

    for (const option of plugin.options) {
      if (!option.isRequired) continue;

      const section = option.type === 'SECRET' ? 'secrets' : 'options';
      const value = pluginConfig?.[section]?.[option.name];

      if (
        value === undefined ||
        value === null ||
        value === '' ||
        (Array.isArray(value) && value.length === 0)
      ) {
        ctx.addIssue({
          code: 'invalid_type',
          expected: 'string',
          received: 'undefined',
          path: [
            ...(Array.isArray(configPath) ? configPath : [configPath]),
            plugin.id,
            section,
            option.name,
          ],
          message: `Required option "${option.name}" is missing for "${plugin.displayName}".`,
        });
      }
    }
  }
}

export const createPluginConfigSchema = (
  plugin: PreconfiguredPluginDescriptor
) => {
  const optionsSchema: Record<string, z.ZodTypeAny> = {};
  const secretsSchema: Record<string, z.ZodTypeAny> = {};

  plugin.options.forEach((option) => {
    let schema = optionTypeToZodType(option.type);
    if (option.isNullable) {
      schema = schema.nullable();
    }
    // Always make optional in the base schema; required checks are done in superRefine.
    schema = schema.optional();

    if (option.type == 'SECRET') {
      secretsSchema[option.name] = schema;
    } else {
      optionsSchema[option.name] = schema;
    }
  });

  return z
    .object({
      options: z.object(optionsSchema).optional(),
      secrets: z.object(secretsSchema).optional(),
    })
    .optional();
};

/**
 * Merge the plugin configs from the last run with the default plugin configs.
 * For fixed options, values configured via plugin descriptors take precedence.
 * For non-fixed options, values from the last run take precedence.
 */
export function mergePluginConfigs(
  lastRunConfig: { [p: string]: PluginConfig } | null | undefined,
  defaultConfig: Record<string, PluginConfig>,
  plugins: PreconfiguredPluginDescriptor[]
): Record<string, PluginConfig> {
  const merged: Record<string, PluginConfig> = {};
  const pluginById = new Map(plugins.map((plugin) => [plugin.id, plugin]));

  for (const pluginId of Object.keys(defaultConfig)) {
    const defaultPlugin = defaultConfig[pluginId];
    const ortPlugin = lastRunConfig?.[pluginId];
    const pluginDescriptor = pluginById.get(pluginId);
    const optionByName = new Map(
      pluginDescriptor?.options.map((option) => [option.name, option])
    );
    const lastRunOptions = Object.fromEntries(
      Object.entries(ortPlugin?.options ?? {}).map(([name, value]) => {
        const option = optionByName.get(name);

        return [
          name,
          option ? pluginOptionValueToFormValue(value, option.type) : value,
        ];
      })
    ) as PluginConfig['options'];

    const mergedPlugin: PluginConfig = {
      options: {
        ...(defaultPlugin?.options ?? {}),
        ...lastRunOptions,
      },
      secrets: {
        ...(defaultPlugin?.secrets ?? {}),
        ...(ortPlugin?.secrets ?? {}),
      },
    };

    for (const option of pluginDescriptor?.options ?? []) {
      if (!option.isFixed) continue;

      const section = option.type === 'SECRET' ? 'secrets' : 'options';
      const defaultSection = defaultPlugin?.[section] ?? {};
      const defaultValue = defaultSection[option.name];

      if (defaultValue !== undefined) {
        mergedPlugin[section][option.name] = defaultValue;
      } else {
        delete mergedPlugin[section][option.name];
      }
    }

    merged[pluginId] = mergedPlugin;
  }

  if (lastRunConfig) {
    for (const pluginId of Object.keys(lastRunConfig)) {
      if (!merged[pluginId] && lastRunConfig[pluginId]) {
        merged[pluginId] = lastRunConfig[pluginId];
      }
    }
  }

  return merged;
}

/**
 * Reconstruct the UI scanner selection (scanners list + per-scanner scope) from
 * the API's `scanners` and `projectScanners` fields.
 *
 * API semantics:
 * - If `projectScanners` is null/empty, `scanners` scan both projects and packages.
 * - Otherwise `scanners` scan packages only, and `projectScanners` scan projects only.
 *   A scanner appearing in both lists scans both.
 */
export function reconstructScannerSelection(
  apiScanners: string[] | null | undefined,
  apiProjectScanners: string[] | null | undefined,
  baseDefaults: {
    scanners: string[];
    scannerScopes: Record<string, 'both' | 'packages' | 'projects'>;
  }
): {
  scanners: string[];
  scannerScopes: Record<string, 'both' | 'packages' | 'projects'>;
} {
  const hasProjectScannerOverride =
    apiProjectScanners != null && apiProjectScanners.length > 0;

  if (!apiScanners && !hasProjectScannerOverride) {
    return {
      scanners: baseDefaults.scanners,
      scannerScopes: baseDefaults.scannerScopes,
    };
  }

  const allScannerIds = Array.from(
    new Set([...(apiScanners ?? []), ...(apiProjectScanners ?? [])])
  );

  const scannerScopes: Record<string, 'both' | 'packages' | 'projects'> = {};
  for (const id of allScannerIds) {
    const inScanners = apiScanners?.includes(id) ?? false;
    const inProjectScanners = apiProjectScanners?.includes(id) ?? false;
    if (!hasProjectScannerOverride) {
      scannerScopes[id] = 'both';
    } else if (inScanners && inProjectScanners) {
      scannerScopes[id] = 'both';
    } else if (inProjectScanners) {
      scannerScopes[id] = 'projects';
    } else {
      scannerScopes[id] = 'packages';
    }
  }

  return {
    scanners: allScannerIds,
    scannerScopes,
  };
}

export function getPluginDefaultValues(
  plugins: PreconfiguredPluginDescriptor[]
) {
  return plugins.reduce(
    (acc, plugin) => {
      const options: Record<string, string | boolean | string[]> = {};
      const secrets: Record<string, string> = {};

      plugin.options.forEach((option) => {
        if (option.defaultValue !== undefined) {
          if (option.type === 'SECRET') {
            // A secret option never carries a real default value. A non-null default indicates
            // that an administrator provided a value via a plugin template. Initialize the
            // dropdown with a placeholder in this case.
            if (option.defaultValue !== null) {
              secrets[option.name] = ADMIN_SECRET_VALUE;
            }
            return;
          } else {
            options[option.name] = pluginOptionValueToFormValue(
              option.defaultValue,
              option.type
            );
          }
        }
      });

      // Cast to PluginConfig: the API type uses string maps, while the form representation
      // uses booleans and arrays for the corresponding option types.
      acc[plugin.id] = {
        options: options as { [key: string]: string },
        secrets: secrets,
      };
      return acc;
    },
    {} as Record<string, PluginConfig>
  );
}

type ProviderPluginFormValues = {
  selectedPluginIds: string[];
  config: Record<string, PluginConfig>;
};

/**
 * Convert provider plugin configurations from a previous run to the form representation.
 */
export function providerPluginConfigsToFormValues(
  providers: ProviderPluginConfiguration[] | null | undefined
): ProviderPluginFormValues {
  const result: ProviderPluginFormValues = {
    selectedPluginIds: [],
    config: {},
  };

  providers?.forEach((provider) => {
    const pluginId = provider.id || provider.type;

    if (provider.enabled !== false) {
      result.selectedPluginIds.push(pluginId);
    }

    result.config[pluginId] = {
      options: provider.options ?? {},
      secrets: provider.secrets ?? {},
    };
  });

  return result;
}

/**
 * Convert the plugin config from form values to the payload format expected by the back-end. Configuration for plugins
 * which are not enabled is not included in the payload. Options matching their plugin defaults are omitted.
 */
export function createPluginPayload(
  config: Record<string, unknown> | undefined,
  enabledPlugins: string[],
  plugins: PreconfiguredPluginDescriptor[],
  pluginType: PluginType
): { [key: string]: PluginConfig } | undefined {
  if (!config) return undefined;

  const pluginById = new Map(
    plugins
      .filter((plugin) => plugin.type === pluginType)
      .map((plugin) => [plugin.id, plugin])
  );
  const filtered = Object.fromEntries(
    Object.entries(config)
      .filter(([key]) => enabledPlugins.includes(key))
      .flatMap(([key, value]) => {
        if (value && typeof value === 'object') {
          const pluginConfig = value as Record<string, unknown>;
          const optionByName = new Map(
            pluginById.get(key)?.options.map((option) => [option.name, option])
          );
          const convertedConfig: PluginConfig = {
            options: {},
            secrets: {},
          };

          if (
            pluginConfig.options &&
            typeof pluginConfig.options === 'object'
          ) {
            convertedConfig.options = Object.fromEntries(
              Object.entries(pluginConfig.options as Record<string, unknown>)
                .filter(([, optValue]) => optValue != null)
                .filter(([optKey, optValue]) => {
                  const option = optionByName.get(optKey);
                  if (option?.defaultValue == null) return true;

                  const value = pluginOptionValueToFormValue(
                    optValue,
                    option.type
                  );
                  const defaultValue = pluginOptionValueToFormValue(
                    option.defaultValue,
                    option.type
                  );

                  return Array.isArray(value) && Array.isArray(defaultValue)
                    ? value.length !== defaultValue.length ||
                        value.some(
                          (entry, index) => entry !== defaultValue[index]
                        )
                    : value !== defaultValue;
                })
                .map(([optKey, optValue]) => [optKey, String(optValue)])
            );
          }

          if (
            pluginConfig.secrets &&
            typeof pluginConfig.secrets === 'object'
          ) {
            convertedConfig.secrets = Object.fromEntries(
              Object.entries(
                pluginConfig.secrets as Record<string, unknown>
              ).flatMap(([secKey, secValue]) =>
                isNonBlankString(secValue) && secValue !== ADMIN_SECRET_VALUE
                  ? [[secKey, secValue]]
                  : []
              )
            ) as { [key: string]: string };
          }

          return Object.keys(convertedConfig.options).length > 0 ||
            Object.keys(convertedConfig.secrets).length > 0
            ? [[key, convertedConfig]]
            : [];
        }
        return [];
      })
  );

  return Object.keys(filtered).length > 0
    ? (filtered as { [key: string]: PluginConfig })
    : undefined;
}

/**
 * Convert selected provider plugins and their configuration to the payload format expected by the back-end.
 */
export function createProviderPluginPayload(
  config: Record<string, unknown> | undefined,
  enabledPlugins: string[],
  plugins: PreconfiguredPluginDescriptor[],
  pluginType: PluginType
): ProviderPluginConfiguration[] | undefined {
  if (enabledPlugins.length === 0) return undefined;

  const pluginPayload = createPluginPayload(
    config,
    enabledPlugins,
    plugins,
    pluginType
  );

  return enabledPlugins.map((pluginId) => {
    const pluginConfig = pluginPayload?.[pluginId];

    return {
      type: pluginId,
      id: pluginId,
      enabled: true,
      ...(pluginConfig?.options && Object.keys(pluginConfig.options).length > 0
        ? { options: pluginConfig.options }
        : {}),
      ...(pluginConfig?.secrets && Object.keys(pluginConfig.secrets).length > 0
        ? { secrets: pluginConfig.secrets }
        : {}),
    };
  });
}

type PackageManagerFormValues = {
  config: Record<string, PluginConfig>;
  mustRunAfter: Record<string, string[]>;
};

/**
 * Convert the package manager configurations from a previous run to the form representation.
 * Package managers cannot have options of type secret, so only the options are converted.
 */
export function packageManagerConfigsToFormValues(
  packageManagerOptions:
    { [key: string]: PackageManagerConfiguration } | null | undefined
): PackageManagerFormValues {
  const result: PackageManagerFormValues = {
    config: {},
    mustRunAfter: {},
  };

  Object.entries(packageManagerOptions ?? {}).forEach(
    ([packageManagerId, packageManagerConfig]) => {
      result.config[packageManagerId] = {
        options: packageManagerConfig.options ?? {},
        secrets: {},
      };

      if (packageManagerConfig.mustRunAfter?.length) {
        result.mustRunAfter[packageManagerId] =
          packageManagerConfig.mustRunAfter;
      }
    }
  );

  return result;
}

/**
 * Convert the configuration of the enabled package managers from form values to the payload format expected by the
 * back-end. Package managers without any options and without a `mustRunAfter` entry are omitted, as are package
 * managers which are not enabled.
 */
export function createPackageManagerPayload(
  config: Record<string, unknown> | undefined,
  mustRunAfter: Record<string, string[] | undefined> | undefined,
  enabledPackageManagers: string[],
  plugins: PreconfiguredPluginDescriptor[]
): { [key: string]: PackageManagerConfiguration } | undefined {
  const pluginPayload = createPluginPayload(
    config,
    enabledPackageManagers,
    plugins,
    'PACKAGE_MANAGER'
  );

  const result = enabledPackageManagers.reduce<{
    [key: string]: PackageManagerConfiguration;
  }>((acc, packageManagerId) => {
    const options = pluginPayload?.[packageManagerId]?.options;
    const packageManagerMustRunAfter = mustRunAfter?.[packageManagerId];

    const packageManagerConfig: PackageManagerConfiguration = {
      ...(packageManagerMustRunAfter?.length
        ? { mustRunAfter: packageManagerMustRunAfter }
        : {}),
      ...(options && Object.keys(options).length > 0 ? { options } : {}),
    };

    if (Object.keys(packageManagerConfig).length > 0) {
      acc[packageManagerId] = packageManagerConfig;
    }

    return acc;
  }, {});

  return Object.keys(result).length > 0 ? result : undefined;
}
