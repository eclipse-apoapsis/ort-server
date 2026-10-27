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

import { describe, expect, it } from 'vitest';

import {
  getProjectSuggestions,
  getSuggestionGroups,
} from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/create-run/-components/project-suggestions';
import { createProject } from '../fixtures/create-run';

const suggestionsFor = (...projects: ReturnType<typeof createProject>[]) =>
  getProjectSuggestions({ runIndex: 12, projects, capped: false });

describe('getProjectSuggestions', () => {
  it('detects Gradle projects with build variant scopes as Android projects', () => {
    expect(
      suggestionsFor(
        createProject({
          type: 'Gradle',
          scopeNames: [
            'releaseRuntimeClasspath',
            'debugAndroidTestCompileClasspath',
          ],
        })
      ).android
    ).toBe(true);
    expect(
      suggestionsFor(
        createProject({ type: 'Gradle', scopeNames: ['testRuntimeClasspath'] })
      ).android
    ).toBe(false);
  });

  it('merges the scope names of all projects', () => {
    const { scopeNames } = suggestionsFor(
      createProject({ scopeNames: ['test', 'compile'] }),
      createProject({ scopeNames: ['compile', 'provided'] })
    );

    expect(scopeNames).toEqual(['compile', 'provided', 'test']);
  });

  it('suggests excluding the directories of example and test projects', () => {
    const groups = getSuggestionGroups(
      'paths',
      suggestionsFor(
        createProject({ definitionFilePath: 'samples/app/build.gradle' }),
        createProject({ definitionFilePath: 'samples/lib/build.gradle' }),
        createProject({ definitionFilePath: 'app/build.gradle' })
      )
    );

    expect(
      groups.find((group) => group.heading === 'Directories in run #12')
        ?.presets
    ).toEqual([
      {
        label: 'Example projects (2 projects)',
        pattern: 'samples/**',
        direction: 'exclude',
        reason: 'EXAMPLE_OF',
      },
    ]);
  });
});

describe('getSuggestionGroups', () => {
  it('puts the suggestions for the package managers found first', () => {
    const groups = getSuggestionGroups(
      'scopes',
      suggestionsFor(
        createProject({ type: 'NPM', scopeNames: ['devDependencies'] })
      )
    );

    expect(groups.map((group) => group.heading).slice(0, 3)).toEqual([
      'Suggested for NPM',
      'Scopes in run #12',
      'Gradle (Android)',
    ]);
  });

  it('lists a preset shared by the package managers found once', () => {
    const groups = getSuggestionGroups(
      'scopes',
      suggestionsFor(
        createProject({ type: 'Maven', scopeNames: ['test'] }),
        createProject({ type: 'SBT', scopeNames: ['test'] })
      )
    );

    expect(groups.at(0)?.presets.map((preset) => preset.pattern)).toEqual([
      'test',
      'provided',
    ]);
  });

  it('groups all suggestions by package manager without an analyzed run', () => {
    const groups = getSuggestionGroups('scopes', null);

    expect(groups.at(0)?.heading).toBe('Gradle (Android)');
  });
});
