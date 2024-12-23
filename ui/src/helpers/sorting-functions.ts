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

import { Severity, VulnerabilityRating } from '@/api/requests';
import { vulnerabilityRatings } from '@/helpers/get-status-class';

/**
 * Compare two severities by their severity level. The severity levels are defined as follows:
 * HINT < WARNING < ERROR.
 *
 * As we cannot rely on the order of the enum severities to retain this order, when creating
 * the query client from the OpenApi specification, we need to manually sort them.
 *
 * @param a The first severity to compare.
 * @param b The second severity to compare.
 * @returns A negative value if `a` is less severe than `b`, a positive value if `a` is more severe than `b`, or zero if
 *          they are equally severe.
 */
export function compareSeverity(a: Severity, b: Severity): number {
  if (a === b) {
    return 0;
  }
  if (a === 'HINT') {
    return -1;
  }
  if (b === 'HINT') {
    return 1;
  }
  if (a === 'WARNING') {
    return b === 'ERROR' ? -1 : 1;
  }
  return 1;
}

/**
 * Compare two overall vulnerability ratings. The ratings are defined as follows:
 * NONE < LOW < MEDIUM < HIGH < CRITICAL.
 *
 * @param a The first rating to compare.
 * @param b The second rating to compare.
 * @returns A negative value if `a` is less severe than `b`, a positive value if `a` is more severe than `b`, or zero if
 *          they are equally severe.
 */
export function compareVulnerabilityRating(
  a: VulnerabilityRating,
  b: VulnerabilityRating
): number {
  return vulnerabilityRatings[a] - vulnerabilityRatings[b];
}
