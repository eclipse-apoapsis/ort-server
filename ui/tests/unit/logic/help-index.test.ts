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

import { describe, expect, it } from 'vitest';

import {
  checkHelpLinks,
  generateHelpIndex,
  parseHelpDocument,
  readHelpIndex,
} from '../../../scripts/generate-help-index';

describe('generateHelpIndex', () => {
  it('matches the committed index, else run `pnpm generate:help`', async () => {
    expect(readHelpIndex()).toBe(await generateHelpIndex());
  });
});

describe('parseHelpDocument', () => {
  it('reads the title and the sections with an explicit ID', () => {
    const markdown = [
      '# Infrastructure Services',
      'The overview.',
      '## URL Prefix {#url}',
      '## A Heading Without ID',
      '### Username Secret {#username-secret-ref}',
    ].join('\n\n');

    expect(parseHelpDocument('test.md', markdown)).toEqual({
      title: 'Infrastructure Services',
      sections: {
        url: 'URL Prefix',
        'username-secret-ref': 'Username Secret',
      },
    });
  });

  it('ignores headings in fenced code blocks', () => {
    const markdown = [
      '# Runs',
      '```markdown\n# Example\n## Rule Set {#rule-set}\n```',
      '~~~~\n## Labels {#labels}\n~~~\n## Still Code {#still-code}\n~~~~',
      '## Rule Set {#rule-set}',
    ].join('\n\n');

    expect(parseHelpDocument('test.md', markdown)).toEqual({
      title: 'Runs',
      sections: { 'rule-set': 'Rule Set' },
    });
  });

  it('reads the text of formatted headings', () => {
    const markdown = '# The *Run*\n\n## The `url` Field {#url}';

    expect(parseHelpDocument('test.md', markdown)).toEqual({
      title: 'The Run',
      sections: { url: 'The url Field' },
    });
  });

  it('rejects a document without a level one heading', () => {
    expect(() => parseHelpDocument('test.md', '## URL Prefix {#url}')).toThrow(
      "The help document 'test.md' has no level one heading."
    );
  });

  it('rejects a document with two sections of the same ID', () => {
    expect(() =>
      parseHelpDocument('test.md', '# Title\n\n## A {#a}\n\n## B {#a}')
    ).toThrow("The help document 'test.md' has more than one section 'a'.");
  });
});

describe('checkHelpLinks', () => {
  const index = {
    secrets: { title: 'Secrets', sections: { name: 'Name' } },
  };

  it('accepts links to help documents, their sections and absolute URLs', () => {
    const markdown = [
      'Keep it in [a secret](secrets.md).',
      'Look it up by [its name](secrets.md#name).',
      'Read [the user guide](https://example.org/guide#secrets).',
      '```markdown\n[an example](missing.md)\n```',
      'Keep it in [a secret](secrets.md "Secrets") or [one][secrets].',
      '[secrets]: secrets.md#name',
    ].join('\n\n');

    expect(() => checkHelpLinks('test.md', markdown, index)).not.toThrow();
  });

  it.each([
    ['a link', (target: string) => `See [this](${target}).`],
    [
      'a link with a title',
      (target: string) => `See [this](${target} "This").`,
    ],
    [
      'a reference link',
      (target: string) => `See [this][ref].\n\n[ref]: ${target}`,
    ],
  ])('checks the target of %s', (_, link) => {
    expect(() =>
      checkHelpLinks('test.md', link('secrets.md#missing'), index)
    ).toThrow("links to 'secrets.md#missing'");
  });

  it.each([
    'missing.md',
    'secrets.md#missing',
    'secrets#name',
    '../secrets.md',
  ])("rejects a link to '%s'", (target) => {
    expect(() =>
      checkHelpLinks('test.md', `See [this](${target}).`, index)
    ).toThrow(
      `The help document 'test.md' links to '${target}', which is not a help document or one of its sections.`
    );
  });
});
