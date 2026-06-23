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

import type { RunConfigurationDiffPathSegment } from './types';

/**
 * Convert a diff path from
 * array form to the string shown in the UI.
 *
 * Examples:
 * - `['analyzer', 'enabled']` becomes `analyzer.enabled`.
 * - `['scanner', 'scanners', 0]` becomes `scanner.scanners[0]`.
 */
export function formatRunConfigurationDiffPath(
  pathSegments: RunConfigurationDiffPathSegment[]
): string {
  const formattedPath = pathSegments.reduce<string>((path, segment) => {
    if (typeof segment === 'number') {
      return `${path}[${segment}]`;
    }

    return path === '' ? String(segment) : `${path}.${String(segment)}`;
  }, '');

  return formattedPath === '' ? '<root>' : formattedPath;
}

/**
 * Read a nested value from a JSON-like object using the same path segment
 * format that `microdiff` uses.
 */
export function getValueAtPath(
  value: unknown,
  pathSegments: RunConfigurationDiffPathSegment[]
): unknown {
  let currentValue = value;

  for (const segment of pathSegments) {
    if (Array.isArray(currentValue) && typeof segment === 'number') {
      currentValue = currentValue[segment];
    } else if (
      typeof currentValue === 'object' &&
      currentValue !== null &&
      typeof segment === 'string'
    ) {
      currentValue = (currentValue as Record<string, unknown>)[segment];
    } else {
      return undefined;
    }
  }

  return currentValue;
}

/**
 * Check whether a path points at or below another path.
 */
export function pathStartsWith(
  path: RunConfigurationDiffPathSegment[],
  prefix: RunConfigurationDiffPathSegment[]
): boolean {
  return (
    path.length >= prefix.length &&
    prefix.every((segment, index) => path[index] === segment)
  );
}
