/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import {
  Issue,
  Package,
  RuleViolation,
  Severity,
  VulnerabilityWithIdentifier,
} from '@/api/requests';
import { VulnerabilityWithRepositoryCount } from '@/hooks/use-vulnerabilities-by-product-suspense';
import { calcOverallVulnerability } from './calc-overall-vulnerability';
import { VulnerabilityRating } from './get-status-class';

/**
 * Calculate the counts of vulnerabilities by their overall rating.
 *
 * @param vulnerabilities
 * @returns Vulnerability counts sorted in decreasing order of rating.
 */
export const calcVulnerabilityRatingCounts = (
  vulnerabilities:
    | VulnerabilityWithRepositoryCount[]
    | VulnerabilityWithIdentifier[]
): { rating: VulnerabilityRating; count: number }[] => {
  let criticalCount = 0;
  let highCount = 0;
  let mediumCount = 0;
  let lowCount = 0;
  let noneCount = 0;
  for (const vulnerability of vulnerabilities) {
    const ratings = vulnerability.vulnerability.references.map(
      (reference) => reference.severity
    );
    const overallRating = calcOverallVulnerability(ratings);
    switch (overallRating) {
      case 'CRITICAL':
        criticalCount++;
        break;
      case 'HIGH':
        highCount++;
        break;
      case 'MEDIUM':
        mediumCount++;
        break;
      case 'LOW':
        lowCount++;
        break;
      case 'NONE':
        noneCount++;
        break;
    }
  }
  return [
    { rating: 'CRITICAL', count: criticalCount },
    { rating: 'HIGH', count: highCount },
    { rating: 'MEDIUM', count: mediumCount },
    { rating: 'LOW', count: lowCount },
    { rating: 'NONE', count: noneCount },
  ];
};

/**
 * Calculate the counts of issues by their severity.
 *
 * @param issues
 * @returns Issue counts sorted in decreasing order of severity.
 */
export const calcIssueSeverityCounts = (
  issues: Issue[]
): { severity: Severity; count: number }[] => {
  let errorCount = 0;
  let warningCount = 0;
  let hintCount = 0;
  for (const issue of issues) {
    switch (issue.severity) {
      case 'ERROR':
        errorCount++;
        break;
      case 'WARNING':
        warningCount++;
        break;
      case 'HINT':
        hintCount++;
        break;
    }
  }
  return [
    { severity: 'ERROR', count: errorCount },
    { severity: 'WARNING', count: warningCount },
    { severity: 'HINT', count: hintCount },
  ];
};

/**
 * Calculate the counts of rule violations by their severity.
 *
 * @param violations
 * @returns Rule violation counts sorted in decreasing order of severity.
 */
export const calcRuleViolationSeverityCounts = (
  violations: RuleViolation[]
): { severity: Severity; count: number }[] => {
  let errorCount = 0;
  let warningCount = 0;
  let hintCount = 0;
  for (const violation of violations) {
    switch (violation.severity) {
      case 'ERROR':
        errorCount++;
        break;
      case 'WARNING':
        warningCount++;
        break;
      case 'HINT':
        hintCount++;
        break;
    }
  }
  return [
    { severity: 'ERROR', count: errorCount },
    { severity: 'WARNING', count: warningCount },
    { severity: 'HINT', count: hintCount },
  ];
};

/**
 * Calculate the counts of packages by their ecosystem.
 *
 * @param packages
 * @returns Package counts sorted by ecosystem.
 */
export const calcPackageEcosystemCounts = (
  packages: Package[]
): { ecosystem: string; count: number }[] => {
  const ecosystemCounts = new Map<string, number>();
  for (const pkg of packages) {
    const ecosystem = pkg.identifier.type;
    ecosystemCounts.set(ecosystem, (ecosystemCounts.get(ecosystem) || 0) + 1);
  }
  return Array.from(ecosystemCounts.entries())
    .map(([ecosystem, count]) => ({
      ecosystem: ecosystem,
      count,
    }))
    .sort((a, b) => a.ecosystem.localeCompare(b.ecosystem));
};
