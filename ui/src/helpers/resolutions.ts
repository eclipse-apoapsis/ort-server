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

import { Issue, RuleViolation, VulnerabilityWithDetails } from '@/api';

export type ItemWithResolutions =
  | Issue
  | RuleViolation
  | VulnerabilityWithDetails;

export function isVulnerabilityItem(
  item: ItemWithResolutions
): item is VulnerabilityWithDetails {
  return 'vulnerability' in item;
}

export function isIssueItem(item: ItemWithResolutions): item is Issue {
  return 'source' in item;
}

export function getAppliedVulnerabilityResolutions(
  item: VulnerabilityWithDetails
) {
  return item.resolutions.filter(
    (resolution) => resolution.externalId === item.vulnerability.externalId
  );
}

export function getUnappliedVulnerabilityResolutions(
  item: VulnerabilityWithDetails
) {
  return item.unappliedResolutions.filter(
    (resolution) => resolution.externalId === item.vulnerability.externalId
  );
}

export function hasVulnerabilityResolutionActivity(
  item: VulnerabilityWithDetails
) {
  return (
    getAppliedVulnerabilityResolutions(item).length > 0 ||
    getUnappliedVulnerabilityResolutions(item).length > 0
  );
}

export function getAppliedIssueResolutions(item: Issue, repositoryId?: string) {
  void repositoryId;

  return item.resolutions ?? [];
}

export function getUnappliedIssueResolutions(item: Issue) {
  return (item.unappliedResolutions ?? []).filter(
    (resolution) => resolution.message === item.message
  );
}

export function hasIssueResolutionActivity(item: Issue) {
  return (
    getAppliedIssueResolutions(item).length > 0 ||
    getUnappliedIssueResolutions(item).length > 0
  );
}

export function getResolvedStatus(item: ItemWithResolutions) {
  if (isVulnerabilityItem(item)) {
    return getAppliedVulnerabilityResolutions(item).length > 0
      ? 'Resolved'
      : 'Unresolved';
  }

  if (isIssueItem(item)) {
    return hasIssueResolutionActivity(item) ? 'Resolved' : 'Unresolved';
  }

  return item.resolutions && item.resolutions.length > 0
    ? 'Resolved'
    : 'Unresolved';
}

// Unit tests.

if (import.meta.vitest) {
  const { describe, it, expect } = import.meta.vitest;

  describe('getAppliedIssueResolutions', () => {
    it('return applied server issue resolutions without re-filtering by message', () => {
      const issue = {
        source: 'Analyzer',
        message: 'Rendered issue message',
        resolutions: [
          {
            message:
              'Persisted server resolution message that does not equal the rendered message',
            messageHash: 'abc123',
            reason: 'BUILD_TOOL_ISSUE',
            comment: 'Persisted after rerun',
            source: 'SERVER',
            isDeleted: false,
          },
        ],
        unappliedResolutions: [],
      } as unknown as Issue;

      expect(getAppliedIssueResolutions(issue)).toStrictEqual(
        issue.resolutions ?? []
      );
    });
  });
}
