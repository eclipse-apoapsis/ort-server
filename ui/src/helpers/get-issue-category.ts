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

// These regular expressions are used to categorize the issues
// and they will be expanded as new cases are discovered.
const infrastructureRegEx = new RegExp(
  'The .* worker failed due to an unexpected error.*' +
    "|ERROR: Timeout after .* seconds while scanning file '.*'\\."
);
const missingDataRegEx = new RegExp(
  'IOException: Could not resolve provenance for .*'
);
const buildSystemRegEx = new RegExp('.* failed to resolve dependencies for .*');

/**
 * Use regular expressions to categorize an issue based on its message.
 *
 * @param message The issue message
 * @returns The issue category
 */
export const getIssueCategory = (message: string): IssueCategory => {
  if (infrastructureRegEx.test(message)) {
    return 'Infrastructure';
  }
  if (missingDataRegEx.test(message)) {
    return 'Missing Data';
  }
  if (buildSystemRegEx.test(message)) {
    return 'Build System';
  }
  return 'Other';
};

//
// Unit tests for the getIssueCategory() function.
//

if (import.meta.vitest) {
  const { it, expect } = import.meta.vitest;

  it('correctly categorizes an infrastructure issue', () => {
    const message1 = 'The analyzer worker failed due to an unexpected error.';
    expect(getIssueCategory(message1)).toBe('Infrastructure');

    const message2 =
      "ERROR: Timeout after 30 seconds while scanning file 'example.txt'.";
    expect(getIssueCategory(message2)).toBe('Infrastructure');
  });

  it('correctly categorizes a missing data issue', () => {
    const message =
      "IOException: Could not resolve provenance for package 'Pod::RNVectorIcons:10.2.0' for source code origins [ARTIFACT, VCS].";
    expect(getIssueCategory(message)).toBe('Missing Data');
  });

  it('correctly categorizes a build system issue', () => {
    const message =
      "GradleInspector failed to resolve dependencies for path 'android/build.gradle':";
    expect(getIssueCategory(message)).toBe('Build System');
  });

  it('correctly categorizes an other issue', () => {
    const message = 'Some other issue occurred.';
    expect(getIssueCategory(message)).toBe('Other');
  });
}
