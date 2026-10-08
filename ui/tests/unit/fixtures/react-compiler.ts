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

import { readFileSync } from 'node:fs';
import path from 'node:path';

import { transformSync } from '@babel/core';
import { reactCompilerPreset } from '@vitejs/plugin-react';

/**
 * Returns whether React Compiler compiled the given component. Compiled
 * components allocate a memo cache when they start rendering; unlike the memo
 * cache sentinel, this allocation is present in every compiled component.
 * Components wrapped in `memo()` or `forwardRef()` are checked by the function
 * they wrap.
 *
 * Components are only compiled in test files that run in the jsdom
 * environment; in the node environment, the compiler does not run at all.
 */
export const isCompiledByReactCompiler = (component: unknown): boolean => {
  const render =
    typeof component === 'object' && component !== null
      ? 'type' in component
        ? component.type
        : 'render' in component
          ? component.render
          : component
      : component;

  return /const \$ = .*\.c\)\(\d+\);/.test(String(render));
};

/**
 * Returns the names of the functions in a source file that React Compiler
 * compiles, with the compilation mode set in `vite.config.ts`. Unlike
 * `isCompiledByReactCompiler`, this also reaches functions that the file does
 * not export. The path is relative to the `ui` directory.
 *
 * In a file that opts out with a module-level `'use no memo'` directive, the
 * compiler still reports its functions as compiled but leaves them unchanged,
 * so no names are returned for a file whose output does not use the compiler
 * runtime.
 */
export const compiledFunctionNames = (sourcePath: string): string[] => {
  const file = path.resolve(import.meta.dirname, '../../..', sourcePath);
  const source = readFileSync(file, 'utf8');
  const lines = source.split('\n');
  const names: string[] = [];
  const { preset } = reactCompilerPreset({
    compilationMode: 'infer',
    logger: {
      logEvent: (_, event) => {
        if (event.kind !== 'CompileSuccess') return;

        // Arrow functions have no name of their own; take it from the
        // declaration they start on.
        const line = lines[(event.fnLoc?.start.line ?? 0) - 1] ?? '';
        const name =
          event.fnName ?? /(?:const|function)\s+(\w+)/.exec(line)?.[1];
        if (name) names.push(name);
      },
    },
  });

  const result = transformSync(source, {
    filename: file,
    babelrc: false,
    configFile: false,
    parserOpts: { plugins: ['typescript', 'jsx'] },
    presets: [preset],
  });

  return result?.code?.includes('react/compiler-runtime') ? names : [];
};
