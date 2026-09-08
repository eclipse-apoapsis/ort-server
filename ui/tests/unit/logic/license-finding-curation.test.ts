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

import { createLicenseFindingCurationTemplate } from '@/lib/license-finding-curation';

const createTemplate = (startLine = 18, endLine = 18) =>
  createLicenseFindingCurationTemplate({
    id: 'NPM::abs-svg-path:0.1.1',
    path: 'package/package.json',
    startLine,
    endLine,
    detectedLicense: 'MIT',
  });

describe('createLicenseFindingCurationTemplate', () => {
  it('creates a template for the displayed license finding', () => {
    expect(createTemplate()).toBe(`id: "NPM::abs-svg-path:0.1.1"
license_finding_curations:
- path: "package/package.json"
  start_lines: "18"
  line_count: 1
  detected_license: "MIT"
  reason: "CODE|DATA_OF|DOCUMENTATION_OF|INCORRECT|NOT_DETECTED|REFERENCE"
  comment: ""
  concluded_license: "<SPDX expression>|NONE"`);
  });

  it('calculates the inclusive line count for a multi-line finding', () => {
    expect(createTemplate(7, 11)).toContain(
      '  start_lines: "7"\n  line_count: 5'
    );
  });

  it('quotes special characters in string values', () => {
    const id = 'NPM::quoted"name:1\\2';
    const path = 'src/"quoted"\\file.ts';
    const detectedLicense = 'LicenseRef-"custom"\\value';
    const template = createLicenseFindingCurationTemplate({
      id,
      path,
      startLine: 1,
      endLine: 1,
      detectedLicense,
    });

    expect(template).toContain(`id: ${JSON.stringify(id)}`);
    expect(template).toContain(`- path: ${JSON.stringify(path)}`);
    expect(template).toContain(
      `  detected_license: ${JSON.stringify(detectedLicense)}`
    );
  });

  it.each([
    { name: 'unknown', startLine: -1, endLine: -1 },
    { name: 'reversed', startLine: 5, endLine: 4 },
  ])('omits line selectors for a $name range', ({ startLine, endLine }) => {
    const template = createTemplate(startLine, endLine);

    expect(template).not.toContain('start_lines:');
    expect(template).not.toContain('line_count:');
  });

  it('contains only the intended package configuration fields', () => {
    const template = createTemplate();

    expect(template).toContain(
      'reason: "CODE|DATA_OF|DOCUMENTATION_OF|INCORRECT|NOT_DETECTED|REFERENCE"'
    );
    expect(template).toContain('comment: ""');
    expect(template).toContain('concluded_license: "<SPDX expression>|NONE"');
    expect(template).not.toContain('source_artifact_url:');
    expect(template).not.toContain('vcs:');
    expect(template).not.toContain('path_excludes:');
  });
});
