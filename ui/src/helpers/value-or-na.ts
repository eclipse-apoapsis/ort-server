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

type ValueOrNaOptions = {
  arrayFormat?: 'string' | 'array';
};

/**
 * Check if the input value is defined, and if it is an array, return a string
 * representation (comma-separated list) of the array elements. If the value is
 * null or undefined, return 'N/A'.
 *
 * @param value Input value of type T, T[], null, or undefined.
 * @param options Optional configuration object to specify how to format arrays.
 *               - 'string': Returns a comma-separated string of array elements.
 *               - 'array': Returns the array as is.
 *               Default is 'string'.
 * @returns The input value if defined, or 'N/A' if null or undefined.
 */
export function valueOrNa<T>(
  value: T | T[] | null | undefined,
  options?: ValueOrNaOptions
): string | T | T[] {
  if (value === null || value === undefined) {
    return 'N/A';
  }

  if (Array.isArray(value)) {
    if (value.length === 0) {
      return 'N/A';
    }

    return options?.arrayFormat === 'array' ? value : value.join(', ');
  }

  if (typeof value === 'string' && value.trim().length === 0) {
    return 'N/A';
  }

  return value;
}
