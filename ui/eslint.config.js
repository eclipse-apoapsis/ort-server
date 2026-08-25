/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import js from '@eslint/js';
import tsEslint from '@typescript-eslint/eslint-plugin';
import tsParser from '@typescript-eslint/parser';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import { defineConfig } from 'eslint/config';
import globals from 'globals';

export default defineConfig([
  {
    ignores: ['dist/', '.eslintrc.cjs', 'src/api/', 'src/routeTree.gen.ts'],
  },
  js.configs.recommended,
  ...tsEslint.configs['flat/recommended'],
  reactHooks.configs.flat.recommended,
  {
    files: ['**/*.ts', '**/*.tsx'],
    languageOptions: {
      globals: {
        ...globals.browser,
      },
      parser: tsParser,
      parserOptions: {
        // Type information is needed by no-unnecessary-condition below.
        // openapi-ts.config.ts is not part of tsconfig.json, so it has to be
        // allowed separately for the project service to accept it.
        projectService: {
          allowDefaultProject: ['openapi-ts.config.ts'],
        },
        tsconfigRootDir: import.meta.dirname,
      },
    },
    plugins: {
      'react-refresh': reactRefresh,
    },
    rules: {
      'react-refresh/only-export-components': [
        'warn',
        {
          allowConstantExport: true,
          extraHOCs: ['createFileRoute', 'createRootRouteWithContext'],
        },
      ],
      'no-console': ['warn'],
      'no-restricted-syntax': [
        'error',
        {
          selector:
            'JSXElement[openingElement.name.name="Link"] JSXElement[openingElement.name.name="Button"]',
          message:
            'Do not nest a Button inside a Link, which renders an anchor. Use <Button asChild><Link …/></Button>.',
        },
      ],
      // Report conditions that can never change the outcome, such as `?.` on a
      // value that is never nullish, so that the checks that do matter stand
      // out.
      '@typescript-eslint/no-unnecessary-condition': 'error',
      // Silence the warnings about TanStack Table hooks, because React Compiler
      // is not used in this project.
      'react-hooks/incompatible-library': 'off',
    },
  },
]);
