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

import { MarkdownRenderer } from '@/components/markdown-renderer';
import { remarkHeadingIds } from '@/lib/remark-heading-ids';
import { remarkSections } from '@/lib/remark-sections';

describe('MarkdownRenderer', () => {
  it('gives headings the explicit IDs written at their end', () => {
    render(
      <MarkdownRenderer
        markdown={[
          '# Title',
          '## URL Prefix {#url}',
          '### Username Secret {#username-secret-ref}',
          '## No ID',
        ].join('\n\n')}
        remarkPlugins={[[remarkHeadingIds, { prefix: 'help-' }]]}
      />
    );

    expect(screen.getByRole('heading', { name: 'URL Prefix' })).toHaveAttribute(
      'id',
      'help-url'
    );
    expect(
      screen.getByRole('heading', { name: 'Username Secret' })
    ).toHaveAttribute('id', 'help-username-secret-ref');
    expect(screen.getByRole('heading', { name: 'No ID' })).not.toHaveAttribute(
      'id'
    );
  });

  it('wraps each section with an ID in a section element', () => {
    const { container } = render(
      <MarkdownRenderer
        markdown={[
          '# Title',
          'The overview.',
          '## URL Prefix {#url}',
          'The URL text.',
          '### Example {#example}',
          'The example text.',
          '## Username Secret {#username}',
          'The username text.',
          '## No ID',
          'Text without a section.',
        ].join('\n\n')}
        remarkPlugins={[remarkHeadingIds, remarkSections]}
      />
    );

    const url = container.querySelector('section[data-section="url"]');
    const example = container.querySelector('section[data-section="example"]');
    const username = container.querySelector(
      'section[data-section="username"]'
    );

    expect(url).toHaveTextContent('URL Prefix');
    expect(url).toHaveTextContent('The URL text.');
    expect(url).toContainElement(example as HTMLElement);
    expect(url).not.toHaveTextContent('The username text.');
    expect(example).toHaveTextContent('The example text.');
    expect(username).toHaveTextContent('The username text.');
    expect(username).not.toHaveTextContent('Text without a section.');
    expect(container.querySelectorAll('section')).toHaveLength(3);
    expect(screen.getByText('The overview.').closest('section')).toBeNull();
  });
});
