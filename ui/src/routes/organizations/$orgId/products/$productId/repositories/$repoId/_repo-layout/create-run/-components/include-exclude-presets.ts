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

import type {
  IncludeExcludeDirection,
  IncludeExcludeKind,
} from './includes-excludes';

/** A common include or exclude that can be picked instead of typing its pattern. */
export type IncludeExcludePreset = {
  label: string;
  pattern: string;
  direction: IncludeExcludeDirection;
  reason: string;
};

/** The presets for the projects of one or more package managers. */
export type IncludeExcludePresetGroup = {
  /** The name of the group, usually the package manager. */
  label: string;
  /**
   * The ORT project types the group applies to, as in the type of a project identifier. Empty for
   * presets that apply to projects of any type.
   */
  projectTypes: string[];
  /** Whether the group applies only to Android projects built with Gradle. */
  android?: boolean;
  scopes: IncludeExcludePreset[];
  paths: IncludeExcludePreset[];
};

/**
 * Common includes and excludes by package manager. The scope names are those that the package
 * managers of ORT report; Android projects report a scope per build variant, such as
 * `fullDebugUnitTestRuntimeClasspath` or `fullReleaseRuntimeClasspath`.
 */
export const includeExcludePresetGroups: IncludeExcludePresetGroup[] = [
  {
    label: 'Gradle (Android)',
    projectTypes: ['Gradle'],
    android: true,
    scopes: [
      {
        label: 'Unit and instrumented tests',
        pattern: '.*(UnitTest|AndroidTest).*',
        direction: 'exclude',
        reason: 'TEST_DEPENDENCY_OF',
      },
      {
        label: 'Debug builds',
        pattern: '.*[Dd]ebug.*',
        direction: 'exclude',
        reason: 'BUILD_DEPENDENCY_OF',
      },
      {
        label: 'Only release runtime',
        pattern: '.*[Rr]eleaseRuntimeClasspath',
        direction: 'include',
        reason: 'SOURCE_OF',
      },
    ],
    paths: [
      {
        label: 'Instrumented test sources',
        pattern: '**/src/androidTest/**',
        direction: 'exclude',
        reason: 'TEST_OF',
      },
    ],
  },
  {
    label: 'Gradle',
    projectTypes: ['Gradle'],
    scopes: [
      {
        label: 'Test classpaths',
        pattern: 'test.*|.*Test.*',
        direction: 'exclude',
        reason: 'TEST_DEPENDENCY_OF',
      },
      {
        label: 'Build tooling',
        pattern:
          'kotlinCompiler.*|kotlinKlib.*|kapt.*|ksp.*|detekt.*|ktlint.*|jacoco.*|checkstyle.*|lint.*',
        direction: 'exclude',
        reason: 'BUILD_DEPENDENCY_OF',
      },
    ],
    paths: [],
  },
  {
    label: 'Maven',
    projectTypes: ['Maven'],
    scopes: [
      {
        label: 'Test scope',
        pattern: 'test',
        direction: 'exclude',
        reason: 'TEST_DEPENDENCY_OF',
      },
      {
        label: 'Provided scope',
        pattern: 'provided',
        direction: 'exclude',
        reason: 'PROVIDED_DEPENDENCY_OF',
      },
    ],
    paths: [],
  },
  {
    label: 'SBT',
    projectTypes: ['SBT'],
    scopes: [
      {
        label: 'Test scope',
        pattern: 'test',
        direction: 'exclude',
        reason: 'TEST_DEPENDENCY_OF',
      },
    ],
    paths: [],
  },
  {
    label: 'NPM, Yarn and PNPM',
    projectTypes: ['NPM', 'Yarn', 'Yarn2', 'PNPM', 'Bun'],
    scopes: [
      {
        label: 'Development dependencies',
        pattern: 'devDependencies',
        direction: 'exclude',
        reason: 'DEV_DEPENDENCY_OF',
      },
    ],
    paths: [],
  },
  {
    label: 'Cargo',
    projectTypes: ['Cargo'],
    scopes: [
      {
        label: 'Development and build dependencies',
        pattern: 'dev-dependencies|build-dependencies',
        direction: 'exclude',
        reason: 'DEV_DEPENDENCY_OF',
      },
    ],
    paths: [],
  },
  {
    label: 'Composer',
    projectTypes: ['Composer'],
    scopes: [
      {
        label: 'Development requirements',
        pattern: 'require-dev',
        direction: 'exclude',
        reason: 'DEV_DEPENDENCY_OF',
      },
    ],
    paths: [],
  },
  {
    label: 'Pub',
    projectTypes: ['Pub'],
    scopes: [
      {
        label: 'Development dependencies',
        pattern: 'dev_dependencies',
        direction: 'exclude',
        reason: 'DEV_DEPENDENCY_OF',
      },
    ],
    paths: [],
  },
  {
    label: 'Bundler',
    projectTypes: ['Bundler'],
    scopes: [
      {
        label: 'Development and test groups',
        pattern: 'development|test',
        direction: 'exclude',
        reason: 'DEV_DEPENDENCY_OF',
      },
    ],
    paths: [],
  },
  {
    label: 'Conan',
    projectTypes: ['Conan'],
    scopes: [
      {
        label: 'Build requirements',
        pattern: 'build_requires',
        direction: 'exclude',
        reason: 'BUILD_DEPENDENCY_OF',
      },
      {
        label: 'Test requirements',
        pattern: 'test_requires',
        direction: 'exclude',
        reason: 'TEST_DEPENDENCY_OF',
      },
    ],
    paths: [],
  },
  {
    label: 'Bazel',
    projectTypes: ['Bazel'],
    scopes: [
      {
        label: 'Development dependencies',
        pattern: 'dev',
        direction: 'exclude',
        reason: 'DEV_DEPENDENCY_OF',
      },
    ],
    paths: [],
  },
  {
    label: 'Stack',
    projectTypes: ['Stack'],
    scopes: [
      {
        label: 'Tests and benchmarks',
        pattern: 'test|bench',
        direction: 'exclude',
        reason: 'TEST_DEPENDENCY_OF',
      },
    ],
    paths: [],
  },
  {
    label: 'All projects',
    projectTypes: [],
    scopes: [],
    paths: [
      {
        label: 'Test directories',
        pattern: '**/test/**',
        direction: 'exclude',
        reason: 'TEST_OF',
      },
      {
        label: 'Test directories',
        pattern: '**/tests/**',
        direction: 'exclude',
        reason: 'TEST_OF',
      },
      {
        label: 'Test sources',
        pattern: '**/src/test/**',
        direction: 'exclude',
        reason: 'TEST_OF',
      },
      {
        label: 'Documentation',
        pattern: 'docs/**',
        direction: 'exclude',
        reason: 'DOCUMENTATION_OF',
      },
      {
        label: 'Examples',
        pattern: 'examples/**',
        direction: 'exclude',
        reason: 'EXAMPLE_OF',
      },
      {
        label: 'Samples',
        pattern: 'samples/**',
        direction: 'exclude',
        reason: 'EXAMPLE_OF',
      },
    ],
  },
];

/** Get the preset groups that have presets of the given kind. */
export const getPresetGroups = (kind: IncludeExcludeKind) =>
  includeExcludePresetGroups.filter((group) => group[kind].length > 0);
