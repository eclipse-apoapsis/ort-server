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

import type { Issue } from '@/api';
import { IssueDetails } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/issues/-components/issue-details';

const issue: Issue = {
  message: 'Failed to resolve dependency.\nCheck the package configuration.',
  source: 'Analyzer',
  severity: 'ERROR',
  timestamp: '2026-01-01T00:00:00Z',
};

describe('IssueDetails', () => {
  it('renders the existing message and styling unchanged', () => {
    const markup = renderToStaticMarkup(<IssueDetails issue={issue} />);

    expect(markup).toContain(
      `<div class="text-muted-foreground break-all whitespace-pre-line italic">${issue.message}</div>`
    );
  });

  it('renders the fallback for an empty message with the same styling', () => {
    const markup = renderToStaticMarkup(
      <IssueDetails issue={{ ...issue, message: '' }} />
    );

    expect(markup).toContain(
      '<div class="text-muted-foreground break-all whitespace-pre-line italic">No details.</div>'
    );
  });

  it.each([undefined, null, '', ' \n\t '])(
    'omits how-to-fix content for %j',
    (howToFix) => {
      const markup = renderToStaticMarkup(
        <IssueDetails issue={{ ...issue, howToFix }} />
      );

      expect(markup).not.toContain('How to fix');
      expect(markup).not.toContain('prose');
      expect(markup).toContain(issue.message);
    }
  );

  it.each([issue.message, ''])(
    'renders Markdown guidance alongside the message %j',
    (message) => {
      const markup = renderToStaticMarkup(
        <IssueDetails
          issue={{
            ...issue,
            message,
            howToFix:
              '**Update** the dependency.\n\n[Documentation](https://example.com/fix)\n\n```sh\npnpm install\n```',
          }}
        />
      );

      expect(markup).toContain(message || 'No details.');
      expect(markup).toContain('<div class="font-semibold">How to fix</div>');
      expect(markup).toContain('<strong>Update</strong>');
      expect(markup).toContain('href="https://example.com/fix"');
      expect(markup).toContain('rel="noopener noreferrer"');
      expect(markup).toContain('<pre');
      expect(markup).toContain('<code class="p-1">pnpm install\n</code>');
    }
  );

  it('does not render raw HTML in guidance as HTML', () => {
    const markup = renderToStaticMarkup(
      <IssueDetails
        issue={{
          ...issue,
          howToFix: 'Use <strong>safe text</strong> instead.',
        }}
      />
    );

    expect(markup).toContain('How to fix');
    expect(markup).toContain('&lt;strong&gt;safe text&lt;/strong&gt;');
    expect(markup).not.toContain('<strong>safe text</strong>');
  });
});
