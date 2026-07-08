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

import { z, ZodType } from 'zod';

import { PluginOption, PluginOptionTemplate, PluginOptionType } from '@/api';

function pluginOptionTypeToZodType(type: PluginOptionType): ZodType {
  switch (type) {
    case 'BOOLEAN':
      return z.boolean().default(false);
    case 'ENUM':
      return z.string();
    case 'ENUM_LIST':
      return z.array(z.string()).default([]);
    case 'INTEGER':
      return z.coerce.number();
    case 'LONG':
      return z.coerce.bigint();
    case 'SECRET':
      return z.string();
    case 'STRING':
      return z.string();
    case 'STRING_LIST':
      return z.string();
    default:
      throw new Error(`Unsupported option type: ${type}`);
  }
}

export function parseStoredPluginOptionValue(
  value: string | null | undefined,
  type: PluginOptionType
): unknown {
  if (value === null || value === undefined) {
    return type === 'ENUM_LIST' ? [] : '';
  }
  switch (type) {
    case 'BOOLEAN':
      return value === 'true';
    case 'INTEGER':
      return Number(value);
    case 'ENUM_LIST':
      return value
        .split(',')
        .map((entry) => entry.trim())
        .filter((entry) => entry !== '');
    default:
      return value;
  }
}

export function buildPluginOptionsFormShape(options: Array<PluginOption>) {
  const shape: Record<string, ZodType> = {};
  for (const opt of options) {
    let schema = pluginOptionTypeToZodType(opt.type);
    if (opt.isNullable) {
      schema = schema.nullable();
    }
    if (!opt.isRequired) {
      schema = schema.optional();
    }
    shape[opt.name] = schema;
    shape[`${opt.name}_isFinal`] = z.boolean().default(false);
    shape[`${opt.name}_isNotSet`] = z.boolean().default(false);
  }
  return shape;
}

export function buildPluginTemplateRequestBody(
  options: PluginOption[] | undefined,
  formValues: Record<string, unknown>
): PluginOptionTemplate[] {
  return (
    options
      ?.filter((option) => !formValues[`${option.name}_isNotSet`])
      ?.map((option) => {
        const value = formValues[option.name];
        const isFinal = Boolean(formValues[`${option.name}_isFinal`]);

        let stringValue: string | null;
        if (value === null || value === undefined) {
          stringValue = null;
        } else if (Array.isArray(value)) {
          stringValue = value.join(',');
        } else if (
          typeof value === 'bigint' ||
          typeof value === 'number' ||
          typeof value === 'boolean'
        ) {
          stringValue = value.toString();
        } else {
          stringValue = value as string;
        }

        return {
          option: option.name,
          type: option.type,
          value: stringValue,
          isFinal,
        };
      }) ?? []
  );
}
