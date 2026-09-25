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

import type {
  PluginOptionType,
  PreconfiguredPluginDescriptor,
  PreconfiguredPluginOption,
} from '@/api';
// Import from the module rather than the index, which would make the plugin field and this
// module import each other once the field uses these checks.
import { ADMIN_SECRET_VALUE } from '@/components/form/plugin-multi-select-field/utils';

/** The form values of one plugin's configuration. */
export type PluginConfigValues = {
  options?: Record<string, unknown>;
  secrets?: Record<string, unknown>;
};

/** Convert a serialized plugin option value from the API to its form representation. */
export function pluginOptionValueToFormValue(
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

/** Check whether a form value counts as not set: `undefined`, `null`, `''` or an empty list. */
export function isOptionValueMissing(value: unknown): boolean {
  return (
    value === undefined ||
    value === null ||
    value === '' ||
    (Array.isArray(value) && value.length === 0)
  );
}

/**
 * Check whether a value differs from the option's default value. Both are compared in their
 * form representation. Returns `false` if the option has no default value.
 */
export function optionValueDiffersFromDefault(
  option: Pick<PreconfiguredPluginOption, 'defaultValue' | 'type'>,
  value: unknown
): boolean {
  if (option.defaultValue == null) return false;

  const formValue = pluginOptionValueToFormValue(value, option.type);
  const defaultValue = pluginOptionValueToFormValue(
    option.defaultValue,
    option.type
  );

  return Array.isArray(formValue) && Array.isArray(defaultValue)
    ? formValue.length !== defaultValue.length ||
        formValue.some((entry, index) => entry !== defaultValue[index])
    : formValue !== defaultValue;
}

/** Get the value of an option from a plugin's configuration. */
function getOptionValue(
  option: Pick<PreconfiguredPluginOption, 'name' | 'type'>,
  pluginConfig: PluginConfigValues | undefined
): unknown {
  const section = option.type === 'SECRET' ? 'secrets' : 'options';

  return pluginConfig?.[section]?.[option.name];
}

/** Get the required options of a plugin which have no value in the given configuration. */
export function getMissingRequiredOptions(
  plugin: PreconfiguredPluginDescriptor,
  pluginConfig: PluginConfigValues | undefined
): PreconfiguredPluginOption[] {
  return plugin.options.filter(
    (option) =>
      option.isRequired &&
      isOptionValueMissing(getOptionValue(option, pluginConfig))
  );
}

/**
 * Check whether any option of the plugin which is not fixed has a value other than its default.
 * Only an option without a value, `undefined` or `null`, counts as unchanged; an empty string or
 * list is sent as a value, so it differs from a default that is not empty. A secret option
 * differs if it names a secret, and not the placeholder for a secret provided by an
 * administrator.
 */
export function differsFromDefaults(
  plugin: PreconfiguredPluginDescriptor,
  pluginConfig: PluginConfigValues | undefined
): boolean {
  return plugin.options.some((option) => {
    if (option.isFixed) return false;

    const value = getOptionValue(option, pluginConfig);

    if (option.type === 'SECRET') {
      return (
        typeof value === 'string' &&
        value.trim() !== '' &&
        value !== ADMIN_SECRET_VALUE
      );
    }

    if (value == null) return false;

    return (
      option.defaultValue == null ||
      optionValueDiffersFromDefault(option, value)
    );
  });
}
