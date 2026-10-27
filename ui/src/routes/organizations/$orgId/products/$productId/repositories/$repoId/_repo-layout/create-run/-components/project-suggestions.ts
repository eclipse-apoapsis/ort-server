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

import { escapeRegex } from '@/lib/regex';
import type { AnalyzedRunProjects } from './analyzed-run-projects';
import {
  getPresetGroups,
  type IncludeExcludePreset,
  type IncludeExcludePresetGroup,
} from './include-exclude-presets';
import { guessReason, type IncludeExcludeKind } from './includes-excludes';

/** What the projects of an analyzed run suggest as includes and excludes. */
export type ProjectSuggestions = {
  runIndex: number;
  /** Whether the run has more projects than the suggestions are based on. */
  capped: boolean;
  /** The ORT project types found, sorted. */
  projectTypes: string[];
  /** Whether a Gradle project builds an Android app or library. */
  android: boolean;
  /** The scope names found, sorted. */
  scopeNames: string[];
  /** The definition files of the projects found. */
  definitionFilePaths: string[];
};

/** A group of suggestions as shown in the list for adding includes and excludes. */
export type SuggestionGroup = {
  heading: string;
  presets: IncludeExcludePreset[];
};

// Android projects have a scope per build variant, such as "releaseRuntimeClasspath" or, with
// product flavors, "fullReleaseRuntimeClasspath".
const ANDROID_SCOPE = /([Dd]ebug|[Rr]elease).*Classpath/;

const directoryKinds: { segment: RegExp; label: string; reason: string }[] = [
  { segment: /^tests?$/i, label: 'Test projects', reason: 'TEST_OF' },
  {
    segment: /^(examples?|samples?|demos?)$/i,
    label: 'Example projects',
    reason: 'EXAMPLE_OF',
  },
  {
    segment: /^docs?$/i,
    label: 'Documentation projects',
    reason: 'DOCUMENTATION_OF',
  },
  { segment: /^benchmarks?$/i, label: 'Benchmark projects', reason: 'OTHER' },
];

/**
 * Find the directory suggestions for the given definition files. A project whose definition file
 * is below a directory named like "test", "examples" or "docs" suggests excluding that directory;
 * the topmost such directory is used, so that nested projects lead to one suggestion.
 */
const getDirectorySuggestions = (definitionFilePaths: string[]) => {
  const projectCounts = new Map<
    string,
    { label: string; reason: string; count: number }
  >();

  definitionFilePaths.forEach((path) => {
    const directories = path.split('/').slice(0, -1);

    for (const [index, directory] of directories.entries()) {
      const kind = directoryKinds.find(({ segment }) =>
        segment.test(directory)
      );
      if (!kind) continue;

      const pattern = `${directories.slice(0, index + 1).join('/')}/**`;
      const entry = projectCounts.get(pattern);
      if (entry) {
        entry.count++;
      } else {
        projectCounts.set(pattern, { ...kind, count: 1 });
      }
      break;
    }
  });

  return Array.from(projectCounts.entries())
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([pattern, { label, reason, count }]): IncludeExcludePreset => ({
      label: `${label} (${count} ${count === 1 ? 'project' : 'projects'})`,
      pattern,
      direction: 'exclude',
      reason,
    }));
};

/** Derive the suggestions for includes and excludes from the projects of an analyzed run. */
export const getProjectSuggestions = ({
  runIndex,
  projects,
  capped,
}: AnalyzedRunProjects): ProjectSuggestions => {
  const scopeNames = Array.from(
    new Set(projects.flatMap((project) => project.scopeNames))
  ).sort((a, b) => a.localeCompare(b));

  return {
    runIndex,
    capped,
    projectTypes: Array.from(
      new Set(projects.map((project) => project.identifier.type))
    ).sort((a, b) => a.localeCompare(b)),
    android: projects.some(
      (project) =>
        project.identifier.type === 'Gradle' &&
        project.scopeNames.some((scope) => ANDROID_SCOPE.test(scope))
    ),
    scopeNames,
    definitionFilePaths: projects.map((project) => project.definitionFilePath),
  };
};

const appliesTo = (
  group: IncludeExcludePresetGroup,
  suggestions: ProjectSuggestions
) =>
  group.android
    ? suggestions.android
    : group.projectTypes.length === 0 ||
      group.projectTypes.some((type) =>
        suggestions.projectTypes.includes(type)
      );

const listFormat = new Intl.ListFormat('en', { type: 'conjunction' });

const toScopePreset = (scopeName: string): IncludeExcludePreset => ({
  label: scopeName,
  pattern: escapeRegex(scopeName),
  direction: 'exclude',
  reason: guessReason('scopes', 'exclude', scopeName),
});

/**
 * Get the groups of suggestions for the list for adding includes and excludes. Without
 * suggestions from an analyzed run, there is one group per package manager. With them, the
 * presets for the package managers found come first, then the scopes or directories found, and
 * then the presets for the other package managers.
 */
export const getSuggestionGroups = (
  kind: IncludeExcludeKind,
  suggestions: ProjectSuggestions | null | undefined
): SuggestionGroup[] => {
  const presetGroups = getPresetGroups(kind);
  const toGroup = (group: IncludeExcludePresetGroup): SuggestionGroup => ({
    heading: group.label,
    presets: group[kind],
  });

  if (!suggestions) return presetGroups.map(toGroup);

  const found = presetGroups.filter((group) => appliesTo(group, suggestions));
  const others = presetGroups.filter((group) => !found.includes(group));
  const typeNames = suggestions.projectTypes.map((type) =>
    type === 'Gradle' && suggestions.android ? 'Gradle (Android)' : type
  );
  const inRun = suggestions.capped
    ? `in the first projects of run #${suggestions.runIndex}`
    : `in run #${suggestions.runIndex}`;

  return [
    {
      heading: `Suggested for ${listFormat.format(typeNames)}`,
      // Package managers can share a preset, such as excluding the test scope of Maven and SBT.
      presets: found
        .flatMap((group) => group[kind])
        .filter(
          (preset, index, presets) =>
            presets.findIndex(
              (other) =>
                other.direction === preset.direction &&
                other.pattern === preset.pattern
            ) === index
        ),
    },
    kind === 'scopes'
      ? {
          heading: `Scopes ${inRun}`,
          presets: suggestions.scopeNames.map(toScopePreset),
        }
      : {
          heading: `Directories ${inRun}`,
          presets: getDirectorySuggestions(suggestions.definitionFilePaths),
        },
    ...others.map(toGroup),
  ].filter((group) => group.presets.length > 0);
};
