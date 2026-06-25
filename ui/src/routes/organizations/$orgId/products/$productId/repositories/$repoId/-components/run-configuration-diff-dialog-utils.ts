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

import type { RunConfigurationDiff } from '@/helpers/config-diff';

export const maxValueLength = 280;

export function formatRunConfigurationDiffValue(value: unknown): string {
  const formattedValue = formatValue(value);

  if (formattedValue.length <= maxValueLength) {
    return formattedValue;
  }

  return `${formattedValue.slice(0, maxValueLength - 1)}…`;
}

export function formatRunConfigurationDiffSummary(
  diff: RunConfigurationDiff
): string {
  return `${diff.counts.added} added, ${diff.counts.removed} removed, ${diff.counts.modified} modified`;
}

const formatValue = (value: unknown): string => {
  if (value === undefined) {
    return 'undefined';
  }

  if (typeof value === 'string') {
    return JSON.stringify(value);
  }

  if (
    value === null ||
    typeof value === 'boolean' ||
    typeof value === 'number'
  ) {
    return String(value);
  }

  const jsonValue = JSON.stringify(value);

  return jsonValue ?? String(value);
};
