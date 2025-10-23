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

import { IssueCategory } from '@/schemas';

// A map where the key is the issue category and the value is an array of regular expressions.
// Upon finding a new issue that is not categorized properly, please extend this map, and
// provide a unit test for it in the same file.
// Unit tests can be run from the /ui folder with:
//   pnpm test
const issueCategoryMap: Record<IssueCategory, RegExp[]> = {
  Deprecation: [/^deprecated .*/],
  Infrastructure: [
    /The .* worker failed due to an unexpected error.*/,
    /ERROR: Timeout after .* seconds while scanning file '.*'\./,
    /Missing scan results response body\./,
    /Unable to get an upload URL for '.*'\./,
    /Uploading '.*' to .* failed\./,
    /Failed to add scan job for the following packages:/,
    /Scan failed for job with ID '.*'/,
    /StreamResetException: stream was reset*/,
  ],
  'Missing Data': [/Could not resolve provenance for .*/],
  'Build System': [/.* failed to resolve dependencies for .*/],
  Other: [/.*/],
};

/**
 * Use regular expressions to categorize an issue based on its message.
 *
 * @param message The issue message
 * @returns The issue category
 */
export const getIssueCategory = (message: string): IssueCategory => {
  for (const category in issueCategoryMap) {
    if (
      issueCategoryMap[category as IssueCategory].some((regex) =>
        regex.test(message)
      )
    ) {
      return category as IssueCategory;
    }
  }
  return 'Other';
};

//
// Unit tests for the getIssueCategory() function.
//

if (import.meta.vitest) {
  const { it, expect } = import.meta.vitest;

  it('correctly categorizes deprecation issues', () => {
    const messages = [
      'deprecated @npmcli/move-file@1.1.2: This functionality has been moved to @npmcli/fs',
      'deprecated glob@7.2.3: Glob versions prior to v9 are no longer supported',
      'deprecated uuid@3.4.0: Please upgrade  to version 7 or higher.',
    ];

    messages.forEach((message) => {
      expect(getIssueCategory(message)).toBe('Deprecation');
    });
  });

  it('correctly categorizes infrastructure issues', () => {
    const messages = [
      'The analyzer worker failed due to an unexpected error.',
      "ERROR: Timeout after 30 seconds while scanning file 'example.txt'.",
      'Missing scan results response body.',
      "Unable to get an upload URL for 'foo.txt'.",
      "Uploading 'foo.txt' to http:\\foo.com failed.",
      'Failed to add scan job for the following packages:',
      "Scan failed for job with ID 'djeh4gh3g39372':",
      'StreamResetException: stream was reset: INTERNAL_ERROR',
    ];

    messages.forEach((message) => {
      expect(getIssueCategory(message)).toBe('Infrastructure');
    });
  });

  it('correctly categorizes missing data issues', () => {
    const message =
      "IOException: Could not resolve provenance for package 'Pod::RNVectorIcons:10.2.0' for source code origins [ARTIFACT, VCS].";
    expect(getIssueCategory(message)).toBe('Missing Data');
  });

  it('correctly categorizes build system issues', () => {
    const message =
      "GradleInspector failed to resolve dependencies for path 'android/build.gradle':";
    expect(getIssueCategory(message)).toBe('Build System');
  });

  it('correctly categorizes other issues', () => {
    const message = 'Some other issue occurred.';
    expect(getIssueCategory(message)).toBe('Other');
  });
}
