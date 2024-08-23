/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

/**
 * Some input types for creating runs are strings in the API, but they actually take a limited set
 * of values. Define the possible values here to use in the UI.
 */

export const advisors = [
  {
    id: 'OssIndex',
    label: 'OSS Index',
  },
  {
    id: 'OSV',
    label: 'OSV',
  },
  {
    id: 'VulnerableCode',
    label: 'VulnerableCode',
  },
] as const;

export const packageManagers = [
  {
    id: 'Bundler',
    label: 'Bundler (Ruby)',
  },
  {
    id: 'Cargo',
    label: 'Cargo (Rust)',
  },
  {
    id: 'GoMod',
    label: 'GoMod (Go)',
  },
  {
    id: 'GradleInspector',
    label: 'Gradle (Java)',
  },
  {
    id: 'Maven',
    label: 'Maven (Java)',
  },
  {
    id: 'NPM',
    label: 'NPM (JavaScript / Node.js)',
  },
  {
    id: 'PIP',
    label: 'PIP (Python)',
  },
  {
    id: 'Pipenv',
    label: 'Pipenv (Python)',
  },
  {
    id: 'PNPM',
    label: 'PNPM (JavaScript / Node.js)',
  },
  {
    id: 'Poetry',
    label: 'Poetry (Python)',
  },
  {
    id: 'Yarn',
    label: 'Yarn 1 (JavaScript / Node.js)',
  },
  {
    id: 'Yarn2',
    label: 'Yarn 2+ (JavaScript / Node.js)',
  },
] as const;

export const reportFormats = [
  {
    id: 'CycloneDx',
    label: 'CycloneDX SBOM',
  },
  {
    id: 'SpdxDocument',
    label: 'SPDX Document',
  },
  {
    id: 'PlainTextTemplate',
    label: 'NOTICE file',
  },
  {
    id: 'WebApp',
    label: 'ORT Web App',
  },
  {
    id: 'PdfTemplate',
    label: 'ORT PDF Reports',
  },
  {
    id: 'OrtResult',
    label: 'ORT Result',
  },
  {
    id: 'RunStatistics',
    label: 'ORT Run Statistics',
  },
] as const;
