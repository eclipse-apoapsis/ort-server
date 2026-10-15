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

// @vitest-environment jsdom

import { render, screen } from '@testing-library/react';
import { expect, it } from 'vitest';

import { isCompiledByReactCompiler } from '../fixtures/react-compiler';
import {
  DefaultPropFixture,
  InferredFixture,
} from '../fixtures/react-compiler-fixtures';

it('compiles components without a directive', () => {
  expect(isCompiledByReactCompiler(InferredFixture)).toBe(true);
});

// React Compiler 1.0 skips such components when it runs with Babel 8.
it('compiles components with a default prop value', () => {
  expect(isCompiledByReactCompiler(DefaultPropFixture)).toBe(true);
});

it('renders the default prop value unless another value is passed', () => {
  const { rerender } = render(<DefaultPropFixture />);
  expect(screen.getByText('Fixture')).toBeInTheDocument();

  rerender(<DefaultPropFixture label='Other' />);
  expect(screen.getByText('Other')).toBeInTheDocument();
});
