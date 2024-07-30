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

// Search the secrets hierarchically for default values.

// TypeScript gets confused when accessing nested properties dynamically, so we ensure type
// safety of the input of updateBaseDefaults() while letting 'any' types for internal processing.

/* eslint-disable  @typescript-eslint/no-explicit-any */

import { PagedResponse_Secret_, Secret } from '@/api/requests';
import { CreateRunFormValues } from '@/routes/_layout/organizations/$orgId/products/$productId/repositories/$repoId/create-run';
import { decodePropertyPath } from './defaults-helpers';

/**
 * Get the default values from the secrets.
 *
 * @param defaults All defaults.
 * @returns The default values as a map.
 */
function getDefaultValues(defaults: Secret[]): { [key: string]: string } {
  const defaultValuesMap: { [key: string]: string } = {};

  defaults.forEach((def) => {
    const decoded = decodePropertyPath(def.name).property;
    if (def.description !== undefined) {
      defaultValuesMap[decoded] = def.description;
    }
  });

  return defaultValuesMap;
}

/**
 * A helper function to get the nested value from an object by path.
 *
 * @param obj The object to retrieve the value from.
 * @param path The path of the nested value.
 * @returns The value at the specified path.
 */
function getNestedValue(obj: any, path: string): any {
  return path.split('.').reduce((o, p) => (o ? o[p] : undefined), obj);
}

/**
 * A helper function to set a nested value in an object by path.
 *
 * @param obj The object to update.
 * @param path The path of the nested value.
 * @param value The value to set.
 */
function setNestedValue(obj: any, path: string, value: any): void {
  const keys = path.split('.');
  const lastKey = keys.pop();
  const lastObj = keys.reduce((o, p) => (o[p] = o[p] || {}), obj);
  if (lastKey) lastObj[lastKey] = value;
}

/**
 * Update the base defaults with the default values from the secrets.
 *
 * @param baseDefaults The form's base defaults to update.
 * @param defaults All defaults.
 * @returns The updated defaults.
 */
export function updateBaseDefaults(
  baseDefaults: CreateRunFormValues,
  defaults: Secret[]
): any {
  const defaultValues = getDefaultValues(defaults);
  const updatedDefaults = { ...baseDefaults };

  Object.entries(defaultValues).forEach(([key, value]) => {
    // Extract the expected type from baseDefaults
    const currentValue = getNestedValue(updatedDefaults, key);

    // Determine the type and convert the string value back to its appropriate type
    let typedValue: any;
    if (typeof currentValue === 'boolean') {
      typedValue = value.toLowerCase() === 'true';
    } else if (typeof currentValue === 'number') {
      typedValue = Number(value);
    } else {
      typedValue = value; // Already a string
    }

    // Update the value in updatedDefaults
    setNestedValue(updatedDefaults, key, typedValue);
  });

  return updatedDefaults;
}

/**
 * Get the default parameters from the secrets.
 *
 * @param orgSecrets The organization secrets.
 * @param prodSecrets The product secrets.
 * @param repoSecrets The repository secrets.
 * @returns The default parameters as a key-value array.
 */
// TODO: Change this when parameters are supported
export function getDefaultParameters(
  orgSecrets: PagedResponse_Secret_,
  prodSecrets: PagedResponse_Secret_,
  repoSecrets: PagedResponse_Secret_
): { key: string; value: string }[] {
  const defaultValuesMap: { [key: string]: string } = {};

  // Helper function to add secrets to the map
  const addSecrets = (secrets: PagedResponse_Secret_) => {
    secrets.data.forEach((secret) => {
      if (secret.name.endsWith('DEFAULT-PARAMETER') && secret.description) {
        const nameWithoutDefault = secret.name
          .slice(0, -'DEFAULT-PARAMETER'.length)
          .trim();
        // Add or update the default value in the map
        defaultValuesMap[nameWithoutDefault] = secret.description;
      }
    });
  };

  // Add secrets with precedence: repo > prod > org
  addSecrets(orgSecrets);
  addSecrets(prodSecrets);
  addSecrets(repoSecrets);

  // Convert the map to the required output format
  const defaultKeyValueArray: { key: string; value: string }[] = Object.entries(
    defaultValuesMap
  ).map(([key, value]) => ({ key, value }));

  return defaultKeyValueArray;
}
