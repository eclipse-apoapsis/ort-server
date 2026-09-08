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

const LICENSE_FINDING_CURATION_REASONS = [
  'CODE',
  'DATA_OF',
  'DOCUMENTATION_OF',
  'INCORRECT',
  'NOT_DETECTED',
  'REFERENCE',
] as const;

const REASON_PLACEHOLDER = LICENSE_FINDING_CURATION_REASONS.join('|');

type LicenseFindingCurationTemplateOptions = {
  id: string;
  path: string;
  startLine: number;
  endLine: number;
  detectedLicense: string;
};

const quoteYamlString = (value: string) => JSON.stringify(value);

/** Create an editable package-configuration template for a license finding. */
export function createLicenseFindingCurationTemplate({
  id,
  path,
  startLine,
  endLine,
  detectedLicense,
}: LicenseFindingCurationTemplateOptions): string {
  const lines = [
    `id: ${quoteYamlString(id)}`,
    'license_finding_curations:',
    `- path: ${quoteYamlString(path)}`,
  ];

  if (startLine > 0 && endLine >= startLine) {
    lines.push(
      `  start_lines: ${quoteYamlString(startLine.toString())}`,
      `  line_count: ${endLine - startLine + 1}`
    );
  }

  lines.push(
    `  detected_license: ${quoteYamlString(detectedLicense)}`,
    `  reason: ${quoteYamlString(REASON_PLACEHOLDER)}`,
    '  comment: ""',
    '  concluded_license: "<SPDX expression>|NONE"'
  );

  return lines.join('\n');
}
