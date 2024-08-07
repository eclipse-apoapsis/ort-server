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

import {
  ZodArray,
  ZodBoolean,
  ZodEnum,
  ZodNullable,
  ZodNumber,
  ZodObject,
  ZodOptional,
  ZodString,
  ZodType,
} from 'zod';

type PropertyPathWithType = {
  path: string;
  type: string;
};

/**
 * Check if a path is excluded based on the provided excluded paths.
 *
 * @param path The path to check.
 * @param excludedPaths The array of paths to be excluded.
 * @returns True if the path is excluded, otherwise false.
 */
const isPathExcluded = (path: string, excludedPaths: string[]): boolean => {
  return excludedPaths.some(
    (excludedPath) =>
      path === excludedPath || path.startsWith(`${excludedPath}.`)
  );
};

/**
 * Get the type of a Zod schema
 *
 * @param schema The Zod schema.
 * @returns The type as a string.
 */
const getType = (schema: ZodType): string => {
  if (schema instanceof ZodString) {
    return 'string';
  } else if (schema instanceof ZodNumber) {
    return 'number';
  } else if (schema instanceof ZodBoolean) {
    return 'boolean';
  } else if (schema instanceof ZodEnum) {
    return 'enum';
  } else if (schema instanceof ZodArray) {
    return `array<${getType(schema.element)}>`;
  } else if (schema instanceof ZodObject) {
    return 'object';
  } else if (schema instanceof ZodNullable || schema instanceof ZodOptional) {
    return `${getType(schema.unwrap())} | null`;
  } else {
    return 'unknown';
  }
};

/**
 * Get the property paths and their types from a Zod schema, excluding specified paths.
 *
 * @param schema The Zod schema.
 * @param excludedPaths Paths to exclude from the result.
 * @returns The property paths and their types.
 */
export const getPropertyPaths = (
  schema: ZodType,
  excludedPaths: string[] = []
): PropertyPathWithType[] => {
  // Check if schema is nullable or optional
  if (schema instanceof ZodNullable || schema instanceof ZodOptional) {
    return getPropertyPaths(schema.unwrap(), excludedPaths);
  }

  // Check if schema is an array
  if (schema instanceof ZodArray) {
    return getPropertyPaths(schema.element, excludedPaths);
  }

  // Check if schema is an object
  if (schema instanceof ZodObject) {
    // Get key/value pairs from schema
    const entries = Object.entries<ZodType>(schema.shape);

    // Loop through key/value pairs
    const paths = entries.flatMap(([key, value]) => {
      // Get nested keys and types
      const nestedPaths = getPropertyPaths(value, excludedPaths).map(
        (subPath) => ({
          path: subPath.path ? `${key}.${subPath.path}` : key,
          type: subPath.type,
        })
      );
      // Construct the full paths and types
      const fullPaths = nestedPaths.length
        ? nestedPaths
        : [{ path: key, type: getType(value) }];

      // Filter out excluded paths
      return fullPaths.filter(
        (subPath) => !isPathExcluded(subPath.path, excludedPaths)
      );
    });

    // Sort paths by length and then alphabetically
    return paths.sort((a, b) => {
      const lenDiff = a.path.split('.').length - b.path.split('.').length;
      return lenDiff !== 0 ? lenDiff : a.path.localeCompare(b.path);
    });
  }

  // For primitive types and other unsupported types, return the path with its type
  return [{ path: '', type: getType(schema) }].filter(
    (subPath) => subPath.path !== ''
  );
};

/**
 * Encode the property path based on the locked status.
 *
 * @param property The property path to encode.
 * @param locked The locked status.
 * @returns The encoded string.
 */
export const encodePropertyPath = (
  property: string,
  locked: boolean
): string => {
  // Replace dots with hyphens
  const encodedProperty = property.replace(/\./g, '-');
  // Prefix based on locked status
  const prefix = locked ? '-DEFLOCK-' : '-DEF-';
  return `${prefix}${encodedProperty}`;
};

/**
 * Decode the encoded property path to the original path and locked status.
 *
 * @param encoded The encoded property path.
 * @returns An object containing the original property path and locked status.
 */
export const decodePropertyPath = (
  encoded: string
): { property: string; locked: boolean } => {
  // Determine the prefix and locked status
  let prefix: string;
  let locked: boolean;

  if (encoded.startsWith('-DEFLOCK-')) {
    prefix = '-DEFLOCK-';
    locked = true;
  } else if (encoded.startsWith('-DEF-')) {
    prefix = '-DEF-';
    locked = false;
  } else {
    throw new Error('Invalid encoded property path');
  }

  // Remove the prefix and replace hyphens with dots
  const property = encoded.substring(prefix.length).replace(/-/g, '.');

  return { property, locked };
};

/**
 * Check if the given secret name is a default.
 *
 * @param name The secret name.
 * @returns True if the name has a default prefix, otherwise false.
 */
export const isDefault = (name: string): boolean => {
  const defaultPrefixes = ['-DEF-', '-DEFLOCK-'];
  return defaultPrefixes.some((prefix) => name.startsWith(prefix));
};
