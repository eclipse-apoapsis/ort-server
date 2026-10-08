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

import {
  compiledFunctionNames,
  isCompiledByReactCompiler,
} from '../fixtures/react-compiler';
import {
  DefaultPropFixture,
  InferredFixture,
  OptedOutFixture,
  renderFixture,
} from '../fixtures/react-compiler-fixtures';

it('compiles components without a directive', () => {
  expect(InferredFixture.toString()).toContain('react.memo_cache_sentinel');
});

it('does not compile components that opt out with use no memo', () => {
  expect(isCompiledByReactCompiler(OptedOutFixture)).toBe(false);
});

it('does not compile functions that are neither components nor hooks', () => {
  expect(isCompiledByReactCompiler(renderFixture)).toBe(false);
});

it('renders compiled and opted-out components identically', () => {
  const { rerender } = render(<InferredFixture />);
  expect(screen.getByText('Fixture')).toBeInTheDocument();

  rerender(<OptedOutFixture />);
  expect(screen.getByText('Fixture')).toBeInTheDocument();
});

// React Compiler 1.0 skips such components when it runs with Babel 8. The fixture has no output
// that is independent of its props, so it has no sentinel check; test for the memo cache instead.
it('compiles components with a default prop value', () => {
  expect(isCompiledByReactCompiler(DefaultPropFixture)).toBe(true);
});

it('renders the default prop value unless another value is passed', () => {
  const { rerender } = render(<DefaultPropFixture />);
  expect(screen.getByText('Fixture')).toBeInTheDocument();

  rerender(<DefaultPropFixture label='Other' />);
  expect(screen.getByText('Other')).toBeInTheDocument();
});

it('names the functions of a source file that are compiled', () => {
  expect(
    compiledFunctionNames('tests/unit/fixtures/react-compiler-fixtures.tsx')
  ).toEqual(['InferredFixture', 'DefaultPropFixture']);
});

it('names no functions of a module that opts out with use no memo', () => {
  expect(
    compiledFunctionNames('src/providers/home-data/home-data-context.ts')
  ).toEqual([]);
});
