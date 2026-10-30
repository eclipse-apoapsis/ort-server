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

import { ReactNode } from 'react';

import { InlineCode } from '@/components/typography.tsx';

export type JobConfigField = {
  label: string;
  description?: ReactNode;
};

const keepAliveWorker: JobConfigField = {
  label: 'Keep worker alive',
  description:
    'A flag to control whether the worker is kept alive for debugging purposes. This flag only has an effect if the ORT Server is deployed on Kubernetes.',
};

const packageConfigurationProviders: JobConfigField = {
  label: 'Package configuration providers',
  description:
    'Configure the package configuration providers to use. Providers higher in the list take precedence over lower providers. Change the order of providers via drag & drop.',
};

/**
 * The labels and help text of the job settings, keyed by the path of the setting in the job
 * configurations. A `*` segment stands for any key of a map or any element of a list.
 *
 * Labels of the environment definition fields of each package manager are kept in
 * `ENVIRONMENT_DEFINITION_SCHEMAS`.
 */
export const jobConfigFields = {
  ruleSet: {
    label: 'Rule set',
    description:
      'The rule set to use for the run. This selects a set of configuration files used by the Evaluator and the Reporter, such as rules for the Evaluator or license classifications. If left empty, the default rule set is used.',
  },
  parameters: {
    label: 'Configuration parameters',
    description:
      "A map of key-value pairs that serve as input parameters for the configuration's validation script. The script can use these parameters to alter specific job configurations.",
  },
  'analyzer.repositoryConfigPath': {
    label: 'Repository configuration path',
    description:
      'The optional path to a repository configuration file. If this is not defined, the repository configuration is read from ".ort.yml" in the root of the analyzed project repository.',
  },
  'analyzer.allowDynamicVersions': {
    label: 'Allow dynamic versions',
    description:
      'Enable the analysis of projects that use version ranges to declare their dependencies.',
  },
  'analyzer.skipExcluded': {
    label: 'Skip excluded',
    description:
      'A flag to control whether excluded scopes and paths should be skipped by the analyzer.',
  },
  'analyzer.environmentConfig': { label: 'Environment configuration' },
  'analyzer.environmentConfig.environmentVariables': {
    label: 'Environment variables',
    description: (
      <>
        A list of environment variable names and their values, either literal
        ones, or retrieved from named secrets. Use this to specify environment
        variables that are required by the build process. In case of Gradle,
        this can also be used to{' '}
        <a
          className='text-blue-400 hover:underline'
          href={
            'https://docs.gradle.org/current/userguide/build_environment.html#setting_a_project_property'
          }
          target='_blank'
        >
          set Gradle properties by prefixing them with{' '}
          <InlineCode>ORG_GRADLE_PROJECT_</InlineCode>
        </a>
        .
      </>
    ),
  },
  'analyzer.environmentConfig.environmentVariables.*.name': { label: 'Name' },
  'analyzer.environmentConfig.environmentVariables.*.value': {
    label: 'Value',
  },
  'analyzer.environmentConfig.environmentVariables.*.secretName': {
    label: 'Secret',
  },
  'analyzer.environmentConfig.environmentDefinitions': {
    label: 'Environment configuration',
    description:
      'Configure the credentials for different package managers to access private artifact repositories.',
  },
  'analyzer.environmentConfig.environmentDefinitions.*.*.service': {
    label: 'Service',
    description:
      'Select the infrastructure service from a chosen hierarchy level.',
  },
  'analyzer.enabledPackageManagers': {
    label: 'Enabled package managers',
    description:
      "Select the package managers enabled for this run. Note that the 'Unmanaged' package manager is always enabled.",
  },
  'analyzer.packageManagerOptions': {
    label: 'Package manager settings',
    description: 'The settings of each enabled package manager.',
  },
  'analyzer.packageCurationProviders': {
    label: 'Package curation providers',
    description:
      'Configure the package curation providers to use. Providers higher in the list take precedence over lower providers. Change the order of providers via drag & drop.',
  },
  'analyzer.keepAliveWorker': keepAliveWorker,
  'analyzer.keepAlivePhases': {
    label: 'Phases',
    description: (
      <>
        The phases in which the worker should be kept alive. If none are
        selected, the worker is kept alive in the <InlineCode>full</InlineCode>{' '}
        and <InlineCode>analysis</InlineCode> phases.
      </>
    ),
  },
  'advisor.skipExcluded': {
    label: 'Skip excluded',
    description:
      'A flag to control whether excluded scopes and paths should be skipped by the advisor.',
  },
  'advisor.advisors': {
    label: 'Enabled advisors',
    description: 'Select the advisors enabled for this run.',
  },
  'advisor.config': {
    label: 'Advisor settings',
    description: 'The settings of each enabled advisor.',
  },
  'advisor.keepAliveWorker': keepAliveWorker,
  'scanner.skipConcluded': {
    label: 'Skip concluded',
    description:
      'A flag to indicate whether packages that have a concluded license and authors set (to derive copyrights from) should be skipped by the scanner.',
  },
  'scanner.skipExcluded': {
    label: 'Skip excluded',
    description:
      'A flag to control whether excluded scopes and paths should by skipped by the scanner.',
  },
  'scanner.scanners': {
    label: 'Enabled scanners',
    description:
      'Select the scanners enabled for this run. Use the scope toggle to control whether a scanner runs on projects, packages, or both.',
  },
  'scanner.config': {
    label: 'Scanner settings',
    description: 'The settings of each enabled scanner.',
  },
  'scanner.keepAliveWorker': keepAliveWorker,
  'evaluator.packageConfigurationProviders': packageConfigurationProviders,
  'evaluator.keepAliveWorker': keepAliveWorker,
  'reporter.formats': {
    label: 'Report formats',
    description: 'Select the report formats to generate from the run.',
  },
  'reporter.config': {
    label: 'Report format settings',
    description: 'The settings of each report format.',
  },
  'reporter.packageConfigurationProviders': packageConfigurationProviders,
  'reporter.keepAliveWorker': keepAliveWorker,
  'notifier.recipientAddresses': { label: 'Recipient addresses' },
  'notifier.keepAliveWorker': keepAliveWorker,
} as const satisfies Record<string, JobConfigField>;

/**
 * The labels and help text of the settings of a run outside its job configurations, keyed by
 * the name of the setting in the request that creates the run.
 */
export const runConfigFields = {
  revision: {
    label: 'Revision',
    description:
      'The repository revision to use. Can be a branch, tag, or commit. Uses the default branch if left empty.',
  },
  path: {
    label: 'Path',
    description: (
      <>
        The path (relative to the repository root) to limit the analysis to, for
        example <InlineCode>path/to/source</InlineCode>.
      </>
    ),
  },
  jobConfigContext: {
    label: 'Configuration context',
    description: (
      <>
        The context to pass to the configuration provider in use. The
        configuration provider is responsible for obtaining configuration for
        this run and uses the context in an implementation-specific way. For
        example, if the <InlineCode>GitHubConfigFileProvider</InlineCode> is in
        use, the context defines the Git revision of the configuration to check
        out.
      </>
    ),
  },
  environmentConfigPath: {
    label: 'Environment configuration path',
    description: (
      <>
        The optional path to an environment configuration file. If this is not
        defined, the environment configuration is read from the default location{' '}
        <InlineCode>.ort.env.yml</InlineCode>.
      </>
    ),
  },
  labels: {
    label: 'ORT labels',
    description:
      'A map of key-value pairs to store as labels in ORT results. ORT does not interpret labels by itself, but leaves interpretation to custom configuration, like evaluator rules.',
  },
} as const satisfies Record<string, JobConfigField>;

/** The label and help text of the `scopes` and `paths` of a run, which are shown as one setting. */
export const includesExcludesField = {
  label: 'Includes and excludes',
  description: (
    <>
      Exclude scopes and paths from this run, or include only some of them.
      These apply in addition to the excludes and includes in the repository's{' '}
      <InlineCode>.ort.yml</InlineCode>.
    </>
  ),
} as const satisfies JobConfigField;
