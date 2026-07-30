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
import { describe, expect, it, vi } from 'vitest';

import { QueryBoundary, QueryErrorFallback } from '@/components/query-boundary';

const SuspendedChild = () => {
  throw new Promise(() => undefined);
};

describe('QueryBoundary', () => {
  it('renders its children when they do not suspend', () => {
    const markup = renderToStaticMarkup(
      <QueryBoundary fallback={<div>Loading fallback</div>}>
        <div>Loaded content</div>
      </QueryBoundary>
    );

    expect(markup).toContain('Loaded content');
    expect(markup).not.toContain('Loading fallback');
    expect(markup).not.toContain('Loading data...');
  });

  it('renders its fallback and accessible label when a child suspends', () => {
    const markup = renderToStaticMarkup(
      <QueryBoundary
        fallback={<div>Loading fallback</div>}
        loadingLabel='Loading statistics...'
      >
        <div>
          <SuspendedChild />
          Suspended content
        </div>
      </QueryBoundary>
    );

    expect(markup).toContain('Loading fallback');
    expect(markup).toContain('Loading statistics...');
    expect(markup).toContain('role="status"');
    expect(markup).not.toContain('Suspended content');
  });
});

describe('QueryErrorFallback', () => {
  it('renders an accessible retry button with the error message', () => {
    const markup = renderToStaticMarkup(
      <QueryErrorFallback error={new Error('Request failed')} reset={vi.fn()} />
    );

    expect(markup).toContain('lucide-triangle-alert');
    expect(markup).toContain('Retry');
    expect(markup).toContain(
      'aria-label="Could not load data: Request failed. Click to retry."'
    );
  });
});
