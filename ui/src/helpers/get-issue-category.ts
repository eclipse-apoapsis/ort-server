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
// Upon finding a new issue that is not categorized properly, please extend this map.
const issueCategoryMap: Record<IssueCategory, RegExp[]> = {
  Deprecation: [/^deprecated\b.*/],
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
  'Build System': [
    /.* failed to resolve dependencies for .*/,
    /^Multiple projects with the same id .*/,
  ],
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
