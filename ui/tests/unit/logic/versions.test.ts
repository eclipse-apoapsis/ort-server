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

import { getVersionsRelativeToReference } from '@/helpers/versions';

describe('getVersionsRelativeToReference', () => {
  it('groups valid semver versions relative to a reference version', () => {
    expect(
      getVersionsRelativeToReference(['2.18.8', '2.21.4', '3.1.4'], '2.21.3')
    ).toEqual({
      earlierVersions: ['2.18.8'],
      nextVersion: '2.21.4',
      laterVersions: ['3.1.4'],
    });
  });

  it('groups valid semver versions relative to a reference version with pre-release identifiers', () => {
    expect(
      getVersionsRelativeToReference(
        ['2.18.8', '2.21.4', '2.21.4-beta.10', '2.21.4-beta.2'],
        '2.21.3'
      )
    ).toEqual({
      earlierVersions: ['2.18.8'],
      nextVersion: '2.21.4-beta.2',
      laterVersions: ['2.21.4-beta.10', '2.21.4'],
    });
  });

  it('correctly handles comparison for semver-like version strings', () => {
    expect(
      getVersionsRelativeToReference(['1.84', '1.80.2', '1.81.1'], '1.81')
    ).toEqual({
      earlierVersions: ['1.80.2'],
      nextVersion: '1.81.1',
      laterVersions: ['1.84'],
    });
  });

  it('correctly handles comparison for dotted numeric versions with more than three segments', () => {
    expect(getVersionsRelativeToReference(['0.12.0'], '0.9.5.4')).toEqual({
      earlierVersions: [],
      nextVersion: '0.12.0',
      laterVersions: [],
    });
  });

  it('correctly handles comparison for versions with "Final" suffix', () => {
    expect(
      getVersionsRelativeToReference(
        ['4.1.133', '4.2.13', '4.1.133.Final', '4.2.13.Final'],
        '4.1.130.Final'
      )
    ).toEqual({
      earlierVersions: [],
      nextVersion: '4.1.133',
      laterVersions: ['4.1.133.Final', '4.2.13', '4.2.13.Final'],
    });
  });

  it('falls back to numeric string comparison for non-semver-like versions', () => {
    expect(
      getVersionsRelativeToReference(['release-10', 'release-2'], 'release-1')
    ).toEqual({
      earlierVersions: [],
      nextVersion: 'release-2',
      laterVersions: ['release-10'],
    });
  });
});
