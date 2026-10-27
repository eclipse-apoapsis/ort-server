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
  countRemaining,
  describeEffect,
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

describe('describeEffect', () => {
  const suggestions = suggestionsFor(
    createProject({ scopeNames: ['test', 'testRuntimeClasspath', 'compile'] })
  );

  it('describes what an exclude or an include would do in the analyzed run', () => {
    expect(describeEffect('scopes', 'exclude', 'test.*', suggestions)).toEqual({
      text: 'Would exclude 2 of the 3 scopes found in run #12',
      leavesNothing: false,
    });
    expect(
      describeEffect('scopes', 'include', 'compile', suggestions)?.text
    ).toBe('Would keep only 1 of the 3 scopes found in run #12');
    expect(describeEffect('scopes', 'include', 'runtime', suggestions)).toEqual(
      {
        text: 'Would keep none of the 3 scopes found in run #12',
        leavesNothing: true,
      }
    );
  });

  it('matches a scope pattern against the whole scope name', () => {
    expect(describeEffect('scopes', 'exclude', 'test', suggestions)?.text).toBe(
      'Would exclude 1 of the 3 scopes found in run #12'
    );
  });

  it('applies Java flags at the start of a scope pattern', () => {
    expect(
      describeEffect('scopes', 'exclude', '(?i)TEST.*', suggestions)?.text
    ).toBe('Would exclude 2 of the 3 scopes found in run #12');
  });

  it('describes a path exclude only if it excludes projects', () => {
    const paths = suggestionsFor(
      createProject({ definitionFilePath: 'pom.xml' }),
      createProject({ definitionFilePath: 'samples/app/pom.xml' })
    );

    expect(describeEffect('paths', 'exclude', 'samples/**', paths)?.text).toBe(
      'Would exclude 1 of the 2 projects found in run #12'
    );
    // The path may still hold files that are not part of any project.
    expect(
      describeEffect('paths', 'exclude', 'src/tests/**', paths)
    ).toBeUndefined();
    expect(describeEffect('paths', 'include', 'src/**', paths)?.text).toBe(
      'Would keep none of the 2 projects found in run #12'
    );
  });
});

describe('countRemaining', () => {
  const suggestions = suggestionsFor(
    createProject({
      definitionFilePath: 'app/pom.xml',
      scopeNames: ['compile', 'test', 'provided'],
    }),
    createProject({ definitionFilePath: 'samples/pom.xml' })
  );

  it('counts what is left after all excludes together', () => {
    expect(
      countRemaining(
        'scopes',
        [
          { direction: 'exclude', pattern: 'test' },
          { direction: 'exclude', pattern: 'compile|provided' },
        ],
        suggestions
      )
    ).toEqual({ remaining: 0, total: 3 });
  });

  it('excludes what matches no include', () => {
    expect(
      countRemaining(
        'paths',
        [{ direction: 'include', pattern: 'src/**' }],
        suggestions
      )
    ).toEqual({ remaining: 0, total: 2 });
    expect(
      countRemaining(
        'paths',
        [
          { direction: 'include', pattern: '**' },
          { direction: 'exclude', pattern: 'samples/**' },
        ],
        suggestions
      )
    ).toEqual({ remaining: 1, total: 2 });
  });

  it('ignores entries with an empty or invalid pattern', () => {
    expect(
      countRemaining(
        'scopes',
        [
          { direction: 'exclude', pattern: '' },
          { direction: 'exclude', pattern: 'test(' },
        ],
        suggestions
      )
    ).toBeUndefined();
  });
});
