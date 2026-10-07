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

import { compiledFunctionNames } from '../fixtures/react-compiler';
import {
  AnnotatedFixture,
  DefaultPropFixture,
  UnannotatedFixture,
} from '../fixtures/react-compiler-fixtures';

it('compiles only components annotated with use memo', () => {
  expect(AnnotatedFixture.toString()).toContain('react.memo_cache_sentinel');
  expect(UnannotatedFixture.toString()).not.toContain(
    'react.memo_cache_sentinel'
  );
});

it('renders annotated and unannotated components identically', () => {
  const { rerender } = render(<AnnotatedFixture />);
  expect(screen.getByText('Fixture')).toBeInTheDocument();

  rerender(<UnannotatedFixture />);
  expect(screen.getByText('Fixture')).toBeInTheDocument();
});

// React Compiler 1.0 skips such components when it runs with Babel 8. The fixture has no output
// that is independent of its props, so it has no sentinel check; test for the memo cache instead.
it('compiles annotated components with a default prop value', () => {
  expect(DefaultPropFixture.toString()).toMatch(/const \$ = .*\.c\)\(\d+\);/);
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
  ).toEqual(['AnnotatedFixture', 'DefaultPropFixture']);
});
