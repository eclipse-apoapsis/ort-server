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

import type { DependencyGraph } from '@/api';

const packageNames = [
  'http-client',
  'logging-api',
  'logback-classic',
  'logback-core',
  'json-lib',
  'test-lib',
  'commons-text',
];

/** Returns the ORT ID label of the fixture package with the given name. */
export const packageLabel = (name: string) => `Maven:com.example:${name}:1.0`;

/** Returns the PURL label of the fixture package with the given name. */
export const packagePurl = (name: string) =>
  `pkg:maven/com.example/${name}@1.0`;

/**
 * A small dependency graph with the following structure, where `logging-api`
 * is shared by two parents and the two `logback` packages both match a search
 * for "logback":
 *
 * ```
 * Gradle::app:1.0
 * ├─ compileClasspath
 * │  ├─ http-client
 * │  │  ├─ logging-api
 * │  │  │  └─ logback-classic
 * │  │  │     └─ logback-core
 * │  │  └─ json-lib
 * │  └─ commons-text
 * └─ testRuntimeClasspath
 *    └─ test-lib
 *       └─ logging-api (same subtree as above)
 * Gradle::lib:1.0
 * └─ runtimeClasspath
 *    └─ json-lib
 * Gradle::docs:1.0 (no scopes)
 * ```
 */
export const createDependencyGraph = (): DependencyGraph => ({
  edges: [
    { from: 0, to: 1 },
    { from: 0, to: 4 },
    { from: 1, to: 2 },
    { from: 2, to: 3 },
    { from: 5, to: 1 },
  ],
  nodes: packageNames.map((_, index) => ({
    fragment: 0,
    linkage: 'DYNAMIC',
    packageCount: 0,
    pkg: index,
  })),
  packageCount: packageNames.length,
  packages: packageNames.map((name) => ({
    name,
    namespace: 'com.example',
    type: 'Maven',
    version: '1.0',
  })),
  purls: packageNames.map(packagePurl),
  projectGroups: [
    {
      packageCount: 7,
      projectLabel: 'Gradle::app:1.0',
      scopes: [
        {
          packageCount: 6,
          rootNodeIndexes: [0, 6],
          scopeLabel: 'compileClasspath',
          scopeName: 'compileClasspath',
        },
        {
          packageCount: 4,
          rootNodeIndexes: [5],
          scopeLabel: 'testRuntimeClasspath',
          scopeName: 'testRuntimeClasspath',
        },
      ],
    },
    {
      packageCount: 1,
      projectLabel: 'Gradle::lib:1.0',
      scopes: [
        {
          packageCount: 1,
          rootNodeIndexes: [4],
          scopeLabel: 'runtimeClasspath',
          scopeName: 'runtimeClasspath',
        },
      ],
    },
    {
      packageCount: 0,
      projectLabel: 'Gradle::docs:1.0',
      scopes: [],
    },
  ],
});

/**
 * Creates a graph of packages with the given names and edges between their
 * indexes. It has a single project with a single scope whose roots are the
 * given root nodes.
 */
export const createGraph = (
  names: string[],
  edges: [number, number][],
  rootNodeIndexes: number[] = [0]
): DependencyGraph => ({
  edges: edges.map(([from, to]) => ({ from, to })),
  nodes: names.map((_, index) => ({
    fragment: 0,
    linkage: 'DYNAMIC',
    packageCount: 0,
    pkg: index,
  })),
  packageCount: names.length,
  packages: names.map((name) => ({
    name,
    namespace: 'com.example',
    type: 'Maven',
    version: '1.0',
  })),
  purls: names.map(packagePurl),
  projectGroups: [
    {
      packageCount: names.length,
      projectLabel: 'Gradle::project:1.0',
      scopes: [
        {
          packageCount: names.length,
          rootNodeIndexes,
          scopeLabel: 'compileClasspath',
          scopeName: 'compileClasspath',
        },
      ],
    },
  ],
});

/**
 * Creates a graph of `layers` layers with `width` packages each, where every
 * package depends on every package of the next layer. The scope's roots are
 * the first layer, so there are `width ** layers` paths to the packages of the
 * last layer, whose names all contain "leaf".
 */
export const createSharedDependencyGraph = (
  layers: number,
  width: number
): DependencyGraph => {
  const names: string[] = [];
  const edges: [number, number][] = [];

  for (let layer = 0; layer < layers; layer++) {
    for (let position = 0; position < width; position++) {
      names.push(
        layer === layers - 1 ? `leaf-${position}` : `layer-${layer}-${position}`
      );

      if (layer > 0) {
        for (let parent = 0; parent < width; parent++) {
          edges.push([(layer - 1) * width + parent, layer * width + position]);
        }
      }
    }
  }

  return createGraph(
    names,
    edges,
    Array.from({ length: width }, (_, position) => position)
  );
};
