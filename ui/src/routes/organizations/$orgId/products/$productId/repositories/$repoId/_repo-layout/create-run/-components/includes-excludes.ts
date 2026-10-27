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

import { z } from 'zod';

import type { PathIncludesExcludes, ScopeIncludesExcludes } from '@/api';
import {
  zPathExcludeReason,
  zPathIncludeReason,
  zScopeExcludeReason,
  zScopeIncludeReason,
} from '@/api/zod.gen';
import { capitalize } from '@/helpers/capitalize';

/** Whether an entry applies to the scopes or to the paths of a run. */
export type IncludeExcludeKind = 'scopes' | 'paths';

export type IncludeExcludeDirection = 'exclude' | 'include';

/**
 * An include or exclude as edited in the create run form. The reason is empty until one is chosen,
 * and the comment is empty when there is none.
 */
export type IncludeExcludeEntry = {
  direction: IncludeExcludeDirection;
  pattern: string;
  reason: string;
  comment: string;
};

const reasonOptions: Record<
  IncludeExcludeKind,
  Record<IncludeExcludeDirection, readonly string[]>
> = {
  scopes: {
    exclude: zScopeExcludeReason.options,
    include: zScopeIncludeReason.options,
  },
  paths: {
    exclude: zPathExcludeReason.options,
    include: zPathIncludeReason.options,
  },
};

/** Get the reasons the API accepts for an include or exclude of the given kind. */
export const getReasonOptions = (
  kind: IncludeExcludeKind,
  direction: IncludeExcludeDirection
): readonly string[] => reasonOptions[kind][direction];

/** Format a reason such as `TEST_DEPENDENCY_OF` as "Test dependency of". */
export const formatReason = (reason: string): string =>
  capitalize(reason.replaceAll('_', ' '));

const scopeExcludeReasonClues: [RegExp, string][] = [
  [/test/i, 'TEST_DEPENDENCY_OF'],
  [/dev/i, 'DEV_DEPENDENCY_OF'],
  [/doc/i, 'DOCUMENTATION_DEPENDENCY_OF'],
  [/provided/i, 'PROVIDED_DEPENDENCY_OF'],
  [
    /build|lint|kapt|ksp|detekt|jacoco|checkstyle|compiler/i,
    'BUILD_DEPENDENCY_OF',
  ],
];

const pathExcludeReasonClues: [RegExp, string][] = [
  [/test/i, 'TEST_OF'],
  [/doc/i, 'DOCUMENTATION_OF'],
  [/example|sample|demo/i, 'EXAMPLE_OF'],
  [/build|gradle/i, 'BUILD_TOOL_OF'],
];

/**
 * Guess the reason for a new include or exclude from its pattern. Includes are taken to be the
 * source of the project. Excludes get the reason their pattern hints at; without a hint, path
 * excludes get "Other", and scope excludes get no reason, because the API offers no "Other" for
 * them.
 */
export const guessReason = (
  kind: IncludeExcludeKind,
  direction: IncludeExcludeDirection,
  pattern: string
): string => {
  if (direction === 'include') return 'SOURCE_OF';

  const clues =
    kind === 'scopes' ? scopeExcludeReasonClues : pathExcludeReasonClues;
  const clue = clues.find(([regex]) => regex.test(pattern));

  if (clue) return clue[1];
  return kind === 'paths' ? 'OTHER' : '';
};

// The flags that Java accepts at the start of a regular expression and JavaScript supports.
const leadingJavaFlags = /^\(\?([ims]+)\)/;

/**
 * Compile a Java regular expression as a JavaScript one, or return `undefined` if it does not
 * compile. Flags at the start of the expression, such as `(?i)`, become JavaScript flags, as
 * JavaScript does not accept them inside the expression.
 */
export const toJavaScriptRegex = (pattern: string) => {
  const flags = leadingJavaFlags.exec(pattern)?.[1] ?? '';
  const source = flags ? pattern.slice(flags.length + 3) : pattern;

  try {
    return new RegExp(source, [...new Set(flags)].join(''));
  } catch {
    return undefined;
  }
};

/** Whether a pattern compiles as a Java regular expression, as far as JavaScript can tell. */
export const isValidRegex = (pattern: string) =>
  toJavaScriptRegex(pattern) !== undefined;

/**
 * Create the schema for the includes and excludes of one kind. Scope patterns are Java regular
 * expressions, which are checked as JavaScript ones. This rejects a few rarely used Java constructs,
 * such as possessive quantifiers, but nothing else checks the patterns before the analyzer uses them.
 */
export const createIncludesExcludesSchema = (kind: IncludeExcludeKind) =>
  z
    .array(
      z.object({
        direction: z.enum(['exclude', 'include']),
        pattern: z.string(),
        reason: z.string(),
        comment: z.string(),
      })
    )
    .superRefine((entries, ctx) => {
      const seen = new Set<string>();

      entries.forEach((entry, index) => {
        const pattern = entry.pattern.trim();

        if (pattern === '') {
          ctx.addIssue({
            code: 'custom',
            path: [index, 'pattern'],
            message: 'Enter a pattern.',
          });
        } else if (kind === 'scopes' && !isValidRegex(pattern)) {
          ctx.addIssue({
            code: 'custom',
            path: [index, 'pattern'],
            message: 'Not a valid regular expression.',
          });
        } else if (seen.has(`${entry.direction}:${pattern}`)) {
          ctx.addIssue({
            code: 'custom',
            path: [index, 'pattern'],
            message: 'This pattern is already in the list.',
          });
        }
        seen.add(`${entry.direction}:${pattern}`);

        if (!getReasonOptions(kind, entry.direction).includes(entry.reason)) {
          ctx.addIssue({
            code: 'custom',
            path: [index, 'reason'],
            message: 'Choose a reason.',
          });
        }
      });
    });

type IncludesExcludes = PathIncludesExcludes | ScopeIncludesExcludes;

/** Convert the includes and excludes of a run to form entries, excludes first. */
export const includesExcludesToFormEntries = (
  includesExcludes: IncludesExcludes | null | undefined
): IncludeExcludeEntry[] => {
  const toEntries = (
    direction: IncludeExcludeDirection,
    list: { pattern: string; reason: string; comment?: string | null }[] = []
  ) =>
    list.map((entry) => ({
      direction,
      pattern: entry.pattern,
      reason: entry.reason,
      comment: entry.comment ?? '',
    }));

  return [
    ...toEntries('exclude', includesExcludes?.excludes),
    ...toEntries('include', includesExcludes?.includes),
  ];
};

/**
 * Convert form entries to the includes and excludes of the request for creating a run. Returns
 * undefined when there are no entries, so that the request does not change for runs without them.
 * The entries must have been validated, so that their reasons are accepted by the API.
 */
export function formEntriesToIncludesExcludes<T extends IncludesExcludes>(
  entries: IncludeExcludeEntry[]
): T | undefined {
  if (entries.length === 0) return undefined;

  const toApiEntries = (direction: IncludeExcludeDirection) =>
    entries
      .filter((entry) => entry.direction === direction)
      .map((entry) => ({
        pattern: entry.pattern.trim(),
        reason: entry.reason,
        comment: entry.comment.trim() || undefined,
      }));

  return {
    excludes: toApiEntries('exclude'),
    includes: toApiEntries('include'),
  } as T;
}
