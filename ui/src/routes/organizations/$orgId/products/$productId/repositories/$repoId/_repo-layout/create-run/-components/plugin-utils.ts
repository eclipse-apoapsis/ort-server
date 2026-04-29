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
  PluginConfig,
  PluginOptionType,
  PreconfiguredPluginDescriptor,
} from '@/api';

function optionTypeToZodType(type: PluginOptionType): ZodType {
  switch (type) {
    case 'BOOLEAN':
      // Preprocess to coerce string representations ("true"/"false") that may
      // come from default values or API re-run data into actual booleans.
      return z.preprocess((val) => {
        if (typeof val === 'string') return val === 'true';
        return val;
      }, z.boolean());
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
  ctx: z.RefinementCtx
): void {
  for (const plugin of plugins) {
    if (!selectedPluginIds.includes(plugin.id)) continue;

    const pluginConfig = config?.[plugin.id];

    for (const option of plugin.options ?? []) {
      if (!option.isRequired) continue;

      const section = option.type === 'SECRET' ? 'secrets' : 'options';
      const value = pluginConfig?.[section]?.[option.name];

      if (value === undefined || value === null || value === '') {
        ctx.addIssue({
          code: 'invalid_type',
          expected: 'string',
          received: 'undefined',
          path: ['config', plugin.id, section, option.name],
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

  plugin.options?.forEach((option) => {
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
 * Merge the plugin configs from the last run with the default plugin configs. The configs from the last run take
 * precedence.
 */
export function mergePluginConfigs(
  lastRunConfig: { [p: string]: PluginConfig } | null | undefined,
  defaultConfig: Record<string, PluginConfig>
): Record<string, PluginConfig> {
  const merged: Record<string, PluginConfig> = {};

  for (const pluginId of Object.keys(defaultConfig)) {
    const defaultPlugin = defaultConfig[pluginId];
    const ortPlugin = lastRunConfig?.[pluginId];

    merged[pluginId] = {
      options: {
        ...(defaultPlugin?.options ?? {}),
        ...(ortPlugin?.options ?? {}),
      },
      secrets: {
        ...(defaultPlugin?.secrets ?? {}),
        ...(ortPlugin?.secrets ?? {}),
      },
    };
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
    config: Record<string, PluginConfig>;
  },
  scannerPluginDefaultValues: Record<string, PluginConfig>
): {
  scanners: string[];
  scannerScopes: Record<string, 'both' | 'packages' | 'projects'>;
  config: Record<string, PluginConfig>;
} {
  const hasProjectScannerOverride =
    apiProjectScanners != null && apiProjectScanners.length > 0;

  if (!apiScanners && !hasProjectScannerOverride) {
    return {
      scanners: baseDefaults.scanners,
      scannerScopes: baseDefaults.scannerScopes,
      config: baseDefaults.config,
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
    config: mergePluginConfigs(undefined, scannerPluginDefaultValues),
  };
}

export function getPluginDefaultValues(
  plugins: PreconfiguredPluginDescriptor[]
) {
  return plugins.reduce(
    (acc, plugin) => {
      const options: Record<string, string | boolean | string[]> = {};
      const secrets: Record<string, string> = {};

      plugin.options?.forEach((option) => {
        if (option.defaultValue !== undefined) {
          if (option.type === 'SECRET') {
            secrets[option.name] = String(option.defaultValue);
          } else if (option.type === 'BOOLEAN') {
            options[option.name] = option.defaultValue === 'true';
          } else if (option.type === 'STRING_LIST') {
            options[option.name] =
              typeof option.defaultValue === 'string'
                ? option.defaultValue
                    .split(',')
                    .map((s) => s.trim())
                    .filter(Boolean)
                : [];
          } else {
            options[option.name] = String(option.defaultValue);
          }
        }
      });

      // Cast to PluginConfig: the API type uses string maps, but the form
      // schema and Zod preprocessing handle boolean/array values correctly.
      acc[plugin.id] = {
        options: options as { [key: string]: string },
        secrets: secrets,
      };
      return acc;
    },
    {} as Record<string, PluginConfig>
  );
}

/**
 * Convert the plugin config from form values to the payload format expected by the back-end. Configuration for plugins
 * which are not enabled is not included in the payload.
 */
export function createPluginPayload(
  config: Record<string, unknown> | undefined,
  enabledPlugins: string[]
): { [key: string]: PluginConfig } | undefined {
  if (!config) return undefined;

  const filtered = Object.fromEntries(
    Object.entries(config)
      .filter(([key]) => enabledPlugins.includes(key))
      .map(([key, value]) => {
        if (value && typeof value === 'object') {
          const pluginConfig = value as Record<string, unknown>;
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
                .filter(
                  ([, optValue]) => optValue !== undefined && optValue !== null
                )
                .map(([optKey, optValue]) => [optKey, String(optValue)])
            );
          }

          if (
            pluginConfig.secrets &&
            typeof pluginConfig.secrets === 'object'
          ) {
            convertedConfig.secrets = Object.fromEntries(
              Object.entries(pluginConfig.secrets as Record<string, unknown>)
                .filter(
                  ([, secValue]) => secValue !== undefined && secValue !== null
                )
                .map(([secKey, secValue]) => [secKey, String(secValue)])
            );
          }

          return [key, convertedConfig];
        }
        return [key, value];
      })
  );

  return Object.keys(filtered).length > 0
    ? (filtered as { [key: string]: PluginConfig })
    : undefined;
}
