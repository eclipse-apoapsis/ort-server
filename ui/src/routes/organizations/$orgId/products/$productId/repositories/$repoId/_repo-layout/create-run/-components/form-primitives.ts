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

import { z } from 'zod';

import { packageManagers } from '@/lib/types';

export const keyValueSchema = z.object({
  key: z.string(),
  value: z.string(), // Allow empty values for now
});

export const packageManagerOptionsSchema = z.object({
  enabled: z.boolean(),
  // An optional array of package manager IDs (as enums) that must run after the current one.
  mustRunAfter: z
    .array(
      z.enum(Object.fromEntries(packageManagers.map((pm) => [pm.id, pm.id])))
    )
    .optional(),
  options: z.array(keyValueSchema).optional(),
});

// Ensure that when environment variables are used, the name and value
// are both non-empty strings, otherwise the Analyzer job will fail.
//
// The "value" field must be made both nullable and optional, to conform with
// the API's EnvironmentVariableDeclaration, which defines the value as
// string | null | undefined.
export const environmentVariableSchema = z.object({
  name: z.string().min(1),
  secretName: z.string().min(1).nullable().optional(),
  value: z.string().min(1).nullable().optional(),
});

/**
 * Converts an object map coming from the back-end to an array of key-value pairs.
 * This is useful for form handling where an array of objects is required.
 *
 * @param objectMap - The object map from the back-end.
 * @returns An array of key-value pairs.
 */
export const convertMapToArray = (objectMap: {
  [key: string]: string;
}): { key: string; value: string }[] => {
  return Object.entries(objectMap).map(([key, value]) => ({
    key,
    value,
  }));
};

/**
 * Converts an array of key-value pairs to an object map.
 * This is useful for converting form data back to the format expected by the back-end.
 *
 * @param keyValueArray - An array of key-value pairs.
 * @returns The object map.
 */
export const convertArrayToMap = (
  keyValueArray: { key: string; value: string }[]
): { [key: string]: string } => {
  return keyValueArray.reduce(
    (acc, { key, value }) => {
      acc[key] = value;
      return acc;
    },
    {} as { [key: string]: string }
  );
};
