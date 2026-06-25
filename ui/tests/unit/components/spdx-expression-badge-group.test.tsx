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

import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import { SpdxExpressionBadgeGroup } from '@/components/licenses/spdx-expression-badge-group';

describe('SpdxExpressionBadgeGroup', () => {
  it('falls back to a single badge for invalid expressions', () => {
    const markup = renderToStaticMarkup(
      <SpdxExpressionBadgeGroup expression='MIT OR (' />
    );

    expect(markup).toContain('MIT OR (');
    expect(markup.match(/data-slot="badge"/g)?.length).toBe(1);
    expect(markup).not.toContain(
      'class="text-muted-foreground text-xs font-medium uppercase"'
    );
  });

  it('renders a single badge for an atomic SPDX expression', () => {
    const markup = renderToStaticMarkup(
      <SpdxExpressionBadgeGroup expression='MIT' />
    );

    expect(markup).toContain('MIT');
    expect(markup.match(/data-slot="badge"/g)?.length).toBe(1);
    expect(markup).not.toContain('AND');
    expect(markup).not.toContain('OR');
  });

  it('renders conjunctions between multiple badges', () => {
    const markup = renderToStaticMarkup(
      <SpdxExpressionBadgeGroup expression='MIT AND Apache-2.0' />
    );

    expect(markup).toContain('MIT');
    expect(markup).toContain('Apache-2.0');
    expect(markup).toContain('and');
    expect(markup.match(/data-slot="badge"/g)?.length).toBe(2);
  });

  it('renders deprecated GPL plus syntax as its normalized atomic badge', () => {
    const markup = renderToStaticMarkup(
      <SpdxExpressionBadgeGroup expression='GPL-2.0+' />
    );

    expect(markup).toContain('GPL-2.0-or-later');
    expect(markup.match(/data-slot="badge"/g)?.length).toBe(1);
  });

  it('renders parentheses when an OR group is nested inside an AND group', () => {
    const markup = renderToStaticMarkup(
      <SpdxExpressionBadgeGroup expression='MIT AND (Apache-2.0 OR BSD-3-Clause)' />
    );

    expect(markup).toContain('(');
    expect(markup).toContain(')');
    expect(markup).toContain('MIT');
    expect(markup).toContain('Apache-2.0');
    expect(markup).toContain('BSD-3-Clause');
    expect(markup.match(/data-slot="badge"/g)?.length).toBe(3);
  });

  it('renders WITH expressions as a single badge', () => {
    const markup = renderToStaticMarkup(
      <SpdxExpressionBadgeGroup expression='GPL-2.0-only WITH Classpath-exception-2.0' />
    );

    expect(markup).toContain('GPL-2.0-only WITH Classpath-exception-2.0');
    expect(markup.match(/data-slot="badge"/g)?.length).toBe(1);
  });
});
