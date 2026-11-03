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
  IncludeExcludeKind,
  IncludeExcludeType,
} from './includes-excludes';

/** A common include or exclude that can be picked instead of typing its pattern. */
export type IncludeExcludePreset = {
  label: string;
  pattern: string;
  type: IncludeExcludeType;
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
        type: 'exclude',
        reason: 'TEST_DEPENDENCY_OF',
      },
      {
        label: 'Debug builds',
        pattern: '.*[Dd]ebug.*',
        type: 'exclude',
        reason: 'BUILD_DEPENDENCY_OF',
      },
      {
        label: 'Only release runtime',
        pattern: '.*[Rr]eleaseRuntimeClasspath',
        type: 'include',
        reason: 'SOURCE_OF',
      },
    ],
    paths: [
      {
        label: 'Instrumented test sources',
        pattern: '**/src/androidTest/**',
        type: 'exclude',
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
        type: 'exclude',
        reason: 'TEST_DEPENDENCY_OF',
      },
      {
        label: 'Build tooling',
        pattern:
          'kotlinCompiler.*|kotlinKlib.*|kapt.*|ksp.*|detekt.*|ktlint.*|jacoco.*|checkstyle.*|lint.*',
        type: 'exclude',
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
        type: 'exclude',
        reason: 'TEST_DEPENDENCY_OF',
      },
      {
        label: 'Provided scope',
        pattern: 'provided',
        type: 'exclude',
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
        type: 'exclude',
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
        type: 'exclude',
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
        type: 'exclude',
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
        type: 'exclude',
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
        type: 'exclude',
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
        type: 'exclude',
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
        type: 'exclude',
        reason: 'BUILD_DEPENDENCY_OF',
      },
      {
        label: 'Test requirements',
        pattern: 'test_requires',
        type: 'exclude',
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
        type: 'exclude',
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
        type: 'exclude',
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
        type: 'exclude',
        reason: 'TEST_OF',
      },
      {
        label: 'Test directories',
        pattern: '**/tests/**',
        type: 'exclude',
        reason: 'TEST_OF',
      },
      {
        label: 'Test sources',
        pattern: '**/src/test/**',
        type: 'exclude',
        reason: 'TEST_OF',
      },
      {
        label: 'Documentation',
        pattern: 'docs/**',
        type: 'exclude',
        reason: 'DOCUMENTATION_OF',
      },
      {
        label: 'Examples',
        pattern: 'examples/**',
        type: 'exclude',
        reason: 'EXAMPLE_OF',
      },
      {
        label: 'Samples',
        pattern: 'samples/**',
        type: 'exclude',
        reason: 'EXAMPLE_OF',
      },
    ],
  },
];

/** Get the preset groups that have presets of the given kind. */
export const getPresetGroups = (kind: IncludeExcludeKind) =>
  includeExcludePresetGroups.filter((group) => group[kind].length > 0);
