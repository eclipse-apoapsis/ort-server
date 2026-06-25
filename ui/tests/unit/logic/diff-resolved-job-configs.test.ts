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

import { describe, expect, it } from 'vitest';

import { diffResolvedJobConfigs } from '@/helpers/config-diff/diff-resolved-job-configs';
import { formatRunConfigurationDiffPath } from '@/helpers/config-diff/path';

describe('diffResolvedJobConfigs', () => {
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

describe('formatRunConfigurationDiffPath', () => {
  it('formats array indices', () => {
    expect(formatRunConfigurationDiffPath(['scanner', 'scanners', 0])).toBe(
      'scanner.scanners[0]'
    );
  });

  it('formats nested object paths', () => {
    expect(formatRunConfigurationDiffPath(['analyzer', 'enabled'])).toBe(
      'analyzer.enabled'
    );
  });
});
