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

import { Issue, RuleViolation, VulnerabilityWithDetails } from '@/api';
import {
  getAppliedIssueResolutions,
  getResolutionAccordionDefaultValue,
  getResolutionAccordionLabel,
} from '@/helpers/resolutions';

it('getAppliedIssueResolutions returns applied server issue resolutions without re-filtering by message', () => {
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

it('getResolutionAccordionDefaultValue expands details by default when a rule violation has no resolution activity', () => {
  const ruleViolation = {
    rule: 'TEST_RULE',
    message: 'Rule violation message',
    resolutions: [],
    unappliedResolutions: [],
  } as unknown as RuleViolation;

  expect(getResolutionAccordionDefaultValue(ruleViolation)).toStrictEqual([
    'details',
  ]);
});

it('getResolutionAccordionDefaultValue expands resolutions by default when an issue has pending resolutions', () => {
  const issue = {
    source: 'Analyzer',
    message: 'Rendered issue message',
    resolutions: [],
    unappliedResolutions: [
      {
        message: 'Rendered issue message',
        reason: 'BUILD_TOOL_ISSUE',
        comment: 'Pending until rerun',
        source: 'SERVER',
      },
    ],
  } as unknown as Issue;

  expect(getResolutionAccordionDefaultValue(issue)).toStrictEqual([
    'resolutions',
  ]);
});

it('getResolutionAccordionLabel returns an item-specific label', () => {
  const issue = {
    source: 'Analyzer',
    message: 'Issue message',
  } as unknown as Issue;
  const ruleViolation = {
    rule: 'TEST_RULE',
    message: 'Rule violation message',
  } as unknown as RuleViolation;
  const vulnerability = {
    vulnerability: {
      externalId: 'CVE-2026-1234',
    },
  } as unknown as VulnerabilityWithDetails;

  expect(getResolutionAccordionLabel(issue)).toBe('Resolve issue');
  expect(getResolutionAccordionLabel(ruleViolation)).toBe(
    'Resolve rule violation'
  );
  expect(getResolutionAccordionLabel(vulnerability)).toBe(
    'Resolve vulnerability'
  );
});
