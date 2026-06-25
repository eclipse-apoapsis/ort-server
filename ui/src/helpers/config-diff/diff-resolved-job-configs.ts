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

import diff from 'microdiff';
import type { Difference } from 'microdiff';

import type { JobConfigurations } from '@/api';
import { formatRunConfigurationDiffPath } from './path';
import type {
  RunConfigurationDiff,
  RunConfigurationDiffAddedEntry,
  RunConfigurationDiffEntry,
  RunConfigurationDiffModifiedEntry,
  RunConfigurationDiffRemovedEntry,
  RunConfigurationDiffStatus,
} from './types';
import {
  createUnorderedStringListDiffEntries,
  isEntryForUnorderedStringList,
  unorderedStringListPaths,
} from './unordered-string-lists';

const statusSortOrder: Record<RunConfigurationDiffStatus, number> = {
  added: 0,
  removed: 1,
  modified: 2,
};

const compareDiffEntries = (
  first: RunConfigurationDiffEntry,
  second: RunConfigurationDiffEntry
) => {
  const pathComparison = first.path.localeCompare(second.path);

  if (pathComparison !== 0) {
    return pathComparison;
  }

  return statusSortOrder[first.status] - statusSortOrder[second.status];
};

const isAddedEntry = (
  entry: RunConfigurationDiffEntry
): entry is RunConfigurationDiffAddedEntry => entry.status === 'added';

const isRemovedEntry = (
  entry: RunConfigurationDiffEntry
): entry is RunConfigurationDiffRemovedEntry => entry.status === 'removed';

const isModifiedEntry = (
  entry: RunConfigurationDiffEntry
): entry is RunConfigurationDiffModifiedEntry => entry.status === 'modified';

/**
 * Convert a raw `microdiff` record into the diff shape needed.
 */
const mapDifference = (difference: Difference): RunConfigurationDiffEntry => {
  const pathSegments = [...difference.path];
  const path = formatRunConfigurationDiffPath(pathSegments);

  switch (difference.type) {
    case 'CREATE':
      return {
        status: 'added',
        path,
        pathSegments,
        newValue: difference.value,
      };
    case 'REMOVE':
      return {
        status: 'removed',
        path,
        pathSegments,
        oldValue: difference.oldValue,
      };
    case 'CHANGE':
      return {
        status: 'modified',
        path,
        pathSegments,
        oldValue: difference.oldValue,
        newValue: difference.value,
      };
  }
};

/**
 * Compare two resolved job configurations.
 *
 * The adapter hides `microdiff` details from components and also handles
 * selected string arrays as unordered lists. This avoids noisy changes when a
 * value is inserted into arrays such as enabled package managers or scanners.
 */
export function diffResolvedJobConfigs(
  baseConfig: JobConfigurations,
  comparedConfig: JobConfigurations
): RunConfigurationDiff {
  if (baseConfig === undefined || comparedConfig === undefined) {
    throw new TypeError('Resolved job configurations must be defined.');
  }

  const microdiffEntries = diff(
    baseConfig as Record<string, unknown>,
    comparedConfig as Record<string, unknown>
  )
    .map(mapDifference)
    .filter(
      (entry) =>
        !isEntryForUnorderedStringList(entry, baseConfig, comparedConfig)
    );

  const unorderedStringListEntries = unorderedStringListPaths.flatMap((path) =>
    createUnorderedStringListDiffEntries(path, baseConfig, comparedConfig)
  );

  const entries = [...microdiffEntries, ...unorderedStringListEntries].sort(
    compareDiffEntries
  );

  const added = entries.filter(isAddedEntry);
  const removed = entries.filter(isRemovedEntry);
  const modified = entries.filter(isModifiedEntry);

  return {
    added,
    removed,
    modified,
    counts: {
      added: added.length,
      removed: removed.length,
      modified: modified.length,
      total: entries.length,
    },
  };
}
