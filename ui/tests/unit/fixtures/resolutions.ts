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

import type { Issue, RuleViolation, VulnerabilityWithDetails } from '@/api';
import type { DeepPartial } from './deep-partial';

/**
 * Builds an {@link Issue} for resolution tests. The defaults make it an issue
 * (it has a `source`); tests override only the fields they assert on.
 */
export const createIssue = (overrides: DeepPartial<Issue> = {}): Issue =>
  ({
    source: 'Analyzer',
    message: 'Issue message',
    ...overrides,
  }) as unknown as Issue;

/**
 * Builds a {@link RuleViolation} for resolution tests. The defaults make it a
 * rule violation (it has a `rule`).
 */
export const createRuleViolation = (
  overrides: DeepPartial<RuleViolation> = {}
): RuleViolation =>
  ({
    rule: 'TEST_RULE',
    message: 'Rule violation message',
    ...overrides,
  }) as unknown as RuleViolation;

/**
 * Builds a {@link VulnerabilityWithDetails} for resolution tests. The defaults
 * make it a vulnerability (it has a `vulnerability`).
 */
export const createVulnerability = (
  overrides: DeepPartial<VulnerabilityWithDetails> = {}
): VulnerabilityWithDetails =>
  ({
    vulnerability: { externalId: 'CVE-0000-0000' },
    ...overrides,
  }) as unknown as VulnerabilityWithDetails;
