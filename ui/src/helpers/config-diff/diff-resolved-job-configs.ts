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

if (import.meta.vitest) {
  const { describe, expect, it } = import.meta.vitest;

  describe('formatRunConfigurationDiffPath', () => {
    it('formats nested object paths', () => {
      expect(formatRunConfigurationDiffPath(['analyzer', 'enabled'])).toBe(
        'analyzer.enabled'
      );
    });

    it('formats array indices', () => {
      expect(formatRunConfigurationDiffPath(['scanner', 'scanners', 0])).toBe(
        'scanner.scanners[0]'
      );
    });
  });

  describe('diffResolvedJobConfigs', () => {
    it('groups added fields', () => {
      const diff = diffResolvedJobConfigs(
        {},
        { analyzer: { allowDynamicVersions: true } }
      );

      expect(diff.added).toEqual([
        {
          status: 'added',
          path: 'analyzer',
          pathSegments: ['analyzer'],
          newValue: { allowDynamicVersions: true },
        },
      ]);
      expect(diff.counts).toEqual({
        added: 1,
        removed: 0,
        modified: 0,
        total: 1,
      });
    });

    it('groups removed fields', () => {
      const diff = diffResolvedJobConfigs(
        { analyzer: { allowDynamicVersions: true } },
        {}
      );

      expect(diff.removed).toEqual([
        {
          status: 'removed',
          path: 'analyzer',
          pathSegments: ['analyzer'],
          oldValue: { allowDynamicVersions: true },
        },
      ]);
      expect(diff.counts).toEqual({
        added: 0,
        removed: 1,
        modified: 0,
        total: 1,
      });
    });

    it('groups modified fields', () => {
      const diff = diffResolvedJobConfigs(
        { analyzer: { allowDynamicVersions: false } },
        { analyzer: { allowDynamicVersions: true } }
      );

      expect(diff.modified).toEqual([
        {
          status: 'modified',
          path: 'analyzer.allowDynamicVersions',
          pathSegments: ['analyzer', 'allowDynamicVersions'],
          oldValue: false,
          newValue: true,
        },
      ]);
      expect(diff.counts).toEqual({
        added: 0,
        removed: 0,
        modified: 1,
        total: 1,
      });
    });

    it('formats nested paths', () => {
      const diff = diffResolvedJobConfigs(
        { scanner: { scanners: ['ScanCode'] } },
        { scanner: { scanners: ['ScanCode', 'FossID'] } }
      );

      expect(diff.added).toEqual([
        {
          status: 'added',
          path: 'scanner.scanners[1]',
          pathSegments: ['scanner', 'scanners', 1],
          newValue: 'FossID',
        },
      ]);
    });

    it('does not report differences for different object key order', () => {
      const diff = diffResolvedJobConfigs(
        {
          analyzer: {
            allowDynamicVersions: false,
            enabledPackageManagers: ['NPM'],
          },
        },
        {
          analyzer: {
            enabledPackageManagers: ['NPM'],
            allowDynamicVersions: false,
          },
        }
      );

      expect(diff.counts.total).toBe(0);
      expect(diff.added).toEqual([]);
      expect(diff.removed).toEqual([]);
      expect(diff.modified).toEqual([]);
    });

    it('reports array order differences for ordered arrays', () => {
      const diff = diffResolvedJobConfigs(
        {
          analyzer: {
            packageCurationProviders: [
              { type: 'DefaultFile', id: 'DefaultFile' },
              { type: 'ClearlyDefined', id: 'ClearlyDefined' },
            ],
          },
        },
        {
          analyzer: {
            packageCurationProviders: [
              { type: 'ClearlyDefined', id: 'ClearlyDefined' },
              { type: 'DefaultFile', id: 'DefaultFile' },
            ],
          },
        }
      );

      expect(diff.modified.map((entry) => entry.path)).toEqual([
        'analyzer.packageCurationProviders[0].id',
        'analyzer.packageCurationProviders[0].type',
        'analyzer.packageCurationProviders[1].id',
        'analyzer.packageCurationProviders[1].type',
      ]);
    });

    it('does not report shifted modifications for an added package manager', () => {
      const diff = diffResolvedJobConfigs(
        { analyzer: { enabledPackageManagers: ['NPM', 'PIP', 'Pipenv'] } },
        {
          analyzer: {
            enabledPackageManagers: ['NPM', 'OrtProjectFile', 'PIP', 'Pipenv'],
          },
        }
      );

      expect(diff.added).toEqual([
        {
          status: 'added',
          path: 'analyzer.enabledPackageManagers[1]',
          pathSegments: ['analyzer', 'enabledPackageManagers', 1],
          newValue: 'OrtProjectFile',
        },
      ]);
      expect(diff.modified).toEqual([]);
      expect(diff.counts).toEqual({
        added: 1,
        removed: 0,
        modified: 0,
        total: 1,
      });
    });

    it('ignores order changes for unordered string lists', () => {
      const diff = diffResolvedJobConfigs(
        {
          advisor: { advisors: ['OSV', 'VulnerableCode'] },
          analyzer: { enabledPackageManagers: ['NPM', 'PIP'] },
          reporter: { formats: ['CycloneDX', 'WebApp'] },
          scanner: { scanners: ['ScanCode', 'FossID'] },
        },
        {
          advisor: { advisors: ['VulnerableCode', 'OSV'] },
          analyzer: { enabledPackageManagers: ['PIP', 'NPM'] },
          reporter: { formats: ['WebApp', 'CycloneDX'] },
          scanner: { scanners: ['FossID', 'ScanCode'] },
        }
      );

      expect(diff.counts.total).toBe(0);
    });

    it('sorts grouped entries by path deterministically', () => {
      const diff = diffResolvedJobConfigs(
        {
          analyzer: { allowDynamicVersions: false },
          scanner: { scanners: ['ScanCode'] },
        },
        {
          advisor: { advisors: ['OSV'] },
          analyzer: {
            allowDynamicVersions: true,
            enabledPackageManagers: ['NPM'],
          },
          scanner: { projectScanners: ['ScanCode'] },
        }
      );

      expect(diff.added.map((entry) => entry.path)).toEqual([
        'advisor',
        'analyzer.enabledPackageManagers',
        'scanner.projectScanners',
      ]);
      expect(diff.removed.map((entry) => entry.path)).toEqual([
        'scanner.scanners',
      ]);
      expect(diff.modified.map((entry) => entry.path)).toEqual([
        'analyzer.allowDynamicVersions',
      ]);
      expect(diff.counts).toEqual({
        added: 3,
        removed: 1,
        modified: 1,
        total: 5,
      });
    });
  });
}
