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
import { describe, expect, it } from 'vitest';

import { RunIncludesExcludes } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/config/-components/run-includes-excludes';

describe('RunIncludesExcludes', () => {
  it('shows the includes and excludes of a run with their reasons and comments', () => {
    render(
      <RunIncludesExcludes
        scopes={{
          excludes: [
            {
              pattern: 'test.*',
              reason: 'TEST_DEPENDENCY_OF',
              comment: 'Only used in tests',
            },
          ],
        }}
        paths={{ includes: [{ pattern: 'src/**', reason: 'SOURCE_OF' }] }}
      />
    );

    expect(screen.getByText('test.*')).toBeInTheDocument();
    expect(screen.getByText('Test dependency of')).toBeInTheDocument();
    expect(screen.getByText('Only used in tests')).toBeInTheDocument();
    expect(screen.getByText('src/**')).toBeInTheDocument();
    expect(screen.getByText('Source of')).toBeInTheDocument();
  });

  it('shows nothing for a run without includes and excludes', () => {
    const { container } = render(
      <RunIncludesExcludes scopes={null} paths={{ excludes: [] }} />
    );

    expect(container).toBeEmptyDOMElement();
  });
});
