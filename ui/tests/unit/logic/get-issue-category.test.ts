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

import { expect, it } from 'vitest';

import { getIssueCategory } from '@/helpers/get-issue-category';

it('correctly categorizes build system issues', () => {
  const messages = [
    "GradleInspector failed to resolve dependencies for path 'android/build.gradle':",
    "Multiple projects with the same id 'NPM::project:1.0.0' found.",
  ];

  messages.forEach((message) => {
    expect(getIssueCategory(message)).toBe('Build System');
  });
});

it('correctly categorizes deprecation issues', () => {
  const messages = [
    'deprecated @npmcli/move-file@1.1.2: This functionality has been moved to @npmcli/fs',
    'deprecated glob@7.2.3: Glob versions prior to v9 are no longer supported',
    'deprecated uuid@3.4.0: Please upgrade  to version 7 or higher.',
    'deprecated',
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
  const messages = [
    "IOException: Could not resolve provenance for package 'Pod::RNVectorIcons:10.2.0' for source code origins [ARTIFACT, VCS].",
    "Could not resolve provenance for package 'NPM::lodash:4.17.21' for source code origins [VCS].",
    "Could not resolve provenance for package 'Maven:org.example:lib:2.0.0' for source code origins [ARTIFACT].",
  ];

  messages.forEach((message) => {
    expect(getIssueCategory(message)).toBe('Missing Data');
  });
});

it('correctly categorizes other issues', () => {
  const message = 'Some other issue occurred.';
  expect(getIssueCategory(message)).toBe('Other');
});
