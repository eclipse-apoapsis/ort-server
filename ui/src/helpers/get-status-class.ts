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

import { JobStatus, OrtRunStatus, Severity } from '@/api/requests';
import { PackageManager } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/-types';

// Combine statuses reported either by ORT Runs or the individual jobs within them.
export type Status = JobStatus | OrtRunStatus | undefined;

// There is no vulnerability rating in the OpenApi spec, so define it here.
export const vulnerabilityRatings = {
  CRITICAL: 4,
  HIGH: 3,
  MEDIUM: 2,
  LOW: 1,
  NONE: 0,
} as const;

export type VulnerabilityRating = keyof typeof vulnerabilityRatings;

// TailwindCSS classes matching to statuses
//
// Note: all color classes need to be defined as they are here, as they
// are formed in compilation time and cannot be interpolated in runtimet.

const STATUS_BACKGROUND_COLOR: {
  [K in Exclude<Status, undefined>]: string;
} = {
  CREATED: 'bg-gray-500',
  SCHEDULED: 'bg-blue-300',
  RUNNING: 'bg-blue-500',
  ACTIVE: 'bg-blue-500',
  FAILED: 'bg-red-500',
  FINISHED: 'bg-green-500',
  FINISHED_WITH_ISSUES: 'bg-yellow-500',
} as const;

const STATUS_FONT_COLOR: { [K in Exclude<Status, undefined>]: string } = {
  CREATED: 'text-gray-500',
  SCHEDULED: 'text-blue-300',
  RUNNING: 'text-blue-500',
  ACTIVE: 'text-blue-500',
  FAILED: 'text-red-500',
  FINISHED: 'text-green-500',
  FINISHED_WITH_ISSUES: 'text-yellow-500',
} as const;

const STATUS_CLASS: {
  [K in Exclude<Status, undefined>]: string;
} = {
  CREATED: 'w-3 h-3 rounded-full',
  SCHEDULED: 'w-3 h-3 rounded-full',
  RUNNING: 'w-4 h-4 rounded-full animate-pulse border border-black',
  ACTIVE: 'w-4 h-4 rounded-full animate-pulse border border-black',
  FAILED: 'w-3 h-3 rounded-full',
  FINISHED: 'w-3 h-3 rounded-full',
  FINISHED_WITH_ISSUES: 'w-3 h-3 rounded-full',
} as const;

const RULE_VIOLATION_SEVERITY_BG_COLOR: {
  [K in Severity]: string;
} = {
  ERROR: 'bg-red-600',
  WARNING: 'bg-amber-500',
  HINT: 'bg-neutral-300',
} as const;

const ISSUE_SEVERITY_BG_COLOR: {
  [K in Severity]: string;
} = {
  ERROR: 'bg-red-600',
  WARNING: 'bg-amber-500',
  HINT: 'bg-neutral-300',
} as const;

// TailwindCSS class accessor functions
//
// As some TailwindCSS classes need to be returned with an
// accessor function which handles an undefined status specifically,
// access all classes through functions for the sake of consistency
// in code which uses these classes.

// Get the color class for coloring the background of elements
export function getStatusBackgroundColor(status: Status): string {
  if (status === undefined) {
    return 'bg-gray-300';
  }
  return STATUS_BACKGROUND_COLOR[status];
}

// Get the color class for font coloring
export function getStatusFontColor(status: Status): string {
  if (status === undefined) {
    return 'text-gray-300';
  }
  return STATUS_FONT_COLOR[status];
}

// Get the general class for the elements
export function getStatusClass(status: Status): string {
  if (status === undefined) {
    return 'w-3 h-3 rounded-full';
  }
  return STATUS_CLASS[status];
}

// Get the color class for coloring the background of vulnerability ratings
export function getVulnerabilityRatingBackgroundColor(
  rating: VulnerabilityRating
): string {
  switch (rating) {
    case 'CRITICAL':
      return 'bg-red-600';
    case 'HIGH':
      return 'bg-orange-600';
    case 'MEDIUM':
      return 'bg-amber-500';
    case 'LOW':
      return 'bg-yellow-400';
    case 'NONE':
    default:
      return 'bg-neutral-300';
  }
}

// Get the color class for coloring the background of rule violation severities
export function getRuleViolationSeverityBackgroundColor(
  severity: Severity
): string {
  return RULE_VIOLATION_SEVERITY_BG_COLOR[severity];
}

// Get the color class for coloring the background of issue severities
export function getIssueSeverityBackgroundColor(severity: Severity): string {
  return ISSUE_SEVERITY_BG_COLOR[severity];
}

// Get the color class for coloring the background of ecosystems.
// These color classes are defined as follows:
// 1. To avoid clashing with "status indicator colors" (eg. red for "FAILED")
//    elsewhere in the UI, those TailwindCSS color palettes which are already
//    used for status indicators are excluded, which leaves 15 different color
//    palettes to choose from.
// 2. The package managers are grouped according to the programming languages,
//    with a total of 14 different groups.
// 3. Package managers which belong to the same group are chosen from inside
//    these 14 color palettes in a way that the colors are visually distinct.
export function getEcosystemBackgroundColor(
  ecosystem: PackageManager | string
): string {
  switch (ecosystem) {
    case 'Bazel':
      return 'bg-stone-500';
    case 'Bower':
      return 'bg-teal-300';
    case 'Bundler':
      return 'bg-pink-400';
    case 'Cargo':
      return 'bg-purple-400';
    case 'Carthage':
      return 'bg-emerald-500';
    case 'CocoaPods':
      return 'bg-rose-400';
    case 'Composer':
      return 'bg-neutral-400';
    case 'Conan':
      return 'bg-emerald-400';
    case 'GoMod':
      return 'bg-cyan-500';
    case 'Gradle':
      return 'bg-indigo-400';
    case 'GradleInspector':
      return 'bg-indigo-500';
    case 'Maven':
      return 'bg-indigo-600';
    case 'NPM':
      return 'bg-teal-400';
    case 'NuGet':
      return 'bg-violet-500';
    case 'PIP':
      return 'bg-lime-400';
    case 'Pipenv':
      return 'bg-lime-500';
    case 'PNPM':
      return 'bg-teal-500';
    case 'Poetry':
      return 'bg-lime-600';
    case 'Pub':
      return 'bg-fuchsia-400';
    case 'SBT':
      return 'bg-sky-400';
    case 'SpdxDocumentFile':
      return 'bg-zinc-400';
    case 'Stack':
      return 'bg-slate-400';
    case 'SwiftPM':
      return 'bg-rose-500';
    case 'Yarn':
      return 'bg-teal-600';
    case 'Yarn2':
      return 'bg-teal-700';
    default:
      return 'bg-neutral-300'; // Fallback for undefined ecosystems
  }
}
