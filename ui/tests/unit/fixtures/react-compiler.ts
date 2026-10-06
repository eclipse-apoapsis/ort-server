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

/**
 * Returns whether React Compiler compiled the given component. Compiled
 * components allocate a memo cache when they start rendering; unlike the memo
 * cache sentinel, this allocation is present in every compiled component.
 *
 * Components are only compiled in test files that run in the jsdom
 * environment; in the node environment, the compiler does not run at all.
 */
export const isCompiledByReactCompiler = (component: unknown): boolean => {
  const render =
    typeof component === 'object' && component !== null && 'type' in component
      ? component.type
      : component;

  return /const \$ = .*\.c\)\(\d+\);/.test(String(render));
};
