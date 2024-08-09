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

// TypeScript gets confused when accessing nested properties, so we ensure type safety
// of the input of updateBaseDefaults() while letting 'any' types for internal processing.

/* eslint-disable  @typescript-eslint/no-explicit-any */

import { PagedResponse_Secret_, Secret } from '@/api/requests';
import { CreateRunFormValues } from '@/routes/_layout/organizations/$orgId/products/$productId/repositories/$repoId/create-run';
import { decodePropertyPath } from './defaults-helpers';

/**
 * Get the default values from the secrets.
 *
 * @param orgDefaults The organization defaults.
 * @param prodDefaults The product defaults.
 * @param repoDefaults The repository defaults.
 * @returns The default values as a map.
 */
function getDefaultValues(
  orgDefaults: Secret[],
  prodDefaults: Secret[],
  repoDefaults: Secret[]
): { [key: string]: string } {
  const defaultValuesMap: { [key: string]: string } = {};

  // Helper function to add secrets to the map
  const addDefaults = (defaults: Secret[]) => {
    defaults.forEach((def) => {
      const decoded = decodePropertyPath(def.name).property;
      if (def.description !== undefined) {
        defaultValuesMap[decoded] = def.description;
      }
    });
  };

  // Add defaults with precedence: repo > prod > org
  addDefaults(orgDefaults);
  addDefaults(prodDefaults);
  addDefaults(repoDefaults);

  return defaultValuesMap;
}

/**
 * Set a nested value in an object.
 *
 * @param obj The object to set the value in.
 * @param path The path to the value.
 * @param value The value to set.
 */
function setNestedValue(
  obj: CreateRunFormValues,
  path: string,
  value: string
): void {
  const keys = path.split('.');
  // TypeScript's type inference doesn't handle dynamic property access well,
  // so current is typed as any during the traversal.
  let current: any = obj;

  for (let i = 0; i < keys.length - 1; i++) {
    const key = keys[i] as keyof typeof current;
    if (!current[key]) {
      current[key] = {};
    }
    current = current[key];
  }

  const finalKey = keys[keys.length - 1] as keyof typeof current;
  current[finalKey] = value;
}

/**
 * Update the base defaults with the default values from the secrets.
 *
 * @param baseDefaults The base defaults to update.
 * @param orgDefaults The organization defaults.
 * @param prodDefaults The product defaults.
 * @param repoDefaults The repository defaults.
 * @returns The updated defaults.
 */
export function updateBaseDefaults(
  baseDefaults: CreateRunFormValues,
  orgDefaults: Secret[],
  prodDefaults: Secret[],
  repoDefaults: Secret[]
): any {
  const defaultValues = getDefaultValues(
    orgDefaults,
    prodDefaults,
    repoDefaults
  );
  const updatedDefaults = { ...baseDefaults };

  Object.entries(defaultValues).forEach(([key, value]) => {
    setNestedValue(updatedDefaults, key, value);
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
