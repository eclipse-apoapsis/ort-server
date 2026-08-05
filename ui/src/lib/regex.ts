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

/**
 * Escape the POSIX regex metacharacters in `value` so that it matches literally when it is passed
 * to a backend filter that interprets its argument as a regular expression. Typing a character like
 * `(` into a search field must narrow the results instead of producing an invalid pattern.
 */
export function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * Turn what a user typed into a search box into a `filter` query parameter, leaving it out entirely
 * while nothing has been typed.
 */
export function toSearchFilter(searchTerm: string): string | undefined {
  return searchTerm ? escapeRegex(searchTerm) : undefined;
}
