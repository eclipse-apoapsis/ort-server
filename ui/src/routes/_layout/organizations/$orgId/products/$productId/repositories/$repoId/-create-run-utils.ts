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

import { z } from 'zod';

import { CreateOrtRun, OrtRun } from '@/api/requests';
import { packageManagers } from './-types';

const keyValueSchema = z.object({
  key: z.string().min(1),
  value: z.string(), // Allow empty values for now
});

export const createRunFormSchema = z.object({
  revision: z.string(),
  path: z.string(),
  jobConfigs: z.object({
    analyzer: z.object({
      enabled: z.boolean(),
      repositoryConfigPath: z.string().optional(),
      allowDynamicVersions: z.boolean(),
      skipExcluded: z.boolean(),
      enabledPackageManagers: z.array(z.string()),
    }),
    advisor: z.object({
      enabled: z.boolean(),
      skipExcluded: z.boolean(),
      advisors: z.array(z.string()),
    }),
    scanner: z.object({
      enabled: z.boolean(),
      skipConcluded: z.boolean(),
      skipExcluded: z.boolean(),
    }),
    evaluator: z.object({
      enabled: z.boolean(),
      ruleSet: z.string().optional(),
      licenseClassificationsFile: z.string().optional(),
      copyrightGarbageFile: z.string().optional(),
      resolutionsFile: z.string().optional(),
    }),
    reporter: z.object({
      enabled: z.boolean(),
      formats: z.array(z.string()),
    }),
    notifier: z.object({
      enabled: z.boolean(),
      notifierRules: z.string().optional(),
      resolutionsFile: z.string().optional(),
      mail: z.object({
        recipientAddresses: z.array(z.object({ email: z.string() })).optional(),
        mailServerConfiguration: z.object({
          hostName: z.string(),
          port: z.coerce.number().int(),
          username: z.string(),
          password: z.string(),
          useSsl: z.boolean(),
          fromAddress: z.string(),
        }),
      }),
      jira: z.object({
        jiraRestClientConfiguration: z.object({
          serverUrl: z.string(),
          username: z.string(),
          password: z.string(),
        }),
      }),
    }),
    parameters: z.array(keyValueSchema).optional(),
  }),
  labels: z.array(keyValueSchema).optional(),
  jobConfigContext: z.string().optional(),
});

export type CreateRunFormValues = z.infer<typeof createRunFormSchema>;

/**
 * Get the default values for the create run form. The form can be provided with a previously run
 * ORT run, in which case the values from it are used as defaults. Otherwise uses base defaults.
 */
export function defaultValues(
  ortRun: OrtRun | null
): z.infer<typeof createRunFormSchema> {
  // Default values for the form: edit only these, not the defaultValues object.
  const baseDefaults = {
    revision: 'main',
    path: '',
    jobConfigs: {
      analyzer: {
        enabled: true,
        repositoryConfigPath: '',
        allowDynamicVersions: true,
        skipExcluded: true,
        enabledPackageManagers: [
          ...packageManagers.map((pm) => pm.id),
          'Unmanaged',
        ],
      },
      advisor: {
        enabled: true,
        skipExcluded: true,
        advisors: ['OSV', 'VulnerableCode'],
      },
      scanner: {
        enabled: true,
        skipConcluded: true,
        skipExcluded: true,
      },
      evaluator: {
        enabled: true,
        ruleSet: '',
        licenseClassificationsFile: '',
        copyrightGarbageFile: '',
        resolutionsFile: '',
      },
      reporter: {
        enabled: true,
        formats: ['CycloneDx', 'SpdxDocument', 'WebApp'],
      },
      notifier: {
        enabled: false,
        notifierRules: '',
        resolutionsFile: '',
        mail: {
          recipientAddresses: [],
          mailServerConfiguration: {
            hostName: 'localhost',
            port: 587,
            username: '',
            password: '',
            useSsl: true,
            fromAddress: '',
          },
        },
        jira: {
          jiraRestClientConfiguration: {
            serverUrl: '',
            username: '',
            password: '',
          },
        },
      },
    },
    jobConfigContext: '',
  };

  // Default values for the form are either taken from "baseDefaults" or,
  // when a rerun action has been taken, fetched from the ORT Run that is
  // being rerun. Whenever a rerun job config parameter is missing, use the
  // default value.
  const defaultValues = ortRun
    ? {
        revision: ortRun.revision || baseDefaults.revision,
        path: ortRun.path || baseDefaults.path,
        jobConfigs: {
          analyzer: {
            enabled: baseDefaults.jobConfigs.analyzer.enabled,
            repositoryConfigPath:
              ortRun.jobConfigs.analyzer?.repositoryConfigPath ||
              baseDefaults.jobConfigs.analyzer.repositoryConfigPath,
            allowDynamicVersions:
              ortRun.jobConfigs.analyzer?.allowDynamicVersions ||
              baseDefaults.jobConfigs.analyzer.allowDynamicVersions,
            skipExcluded:
              ortRun.jobConfigs.analyzer?.skipExcluded ||
              baseDefaults.jobConfigs.analyzer.skipExcluded,
            enabledPackageManagers:
              ortRun.jobConfigs.analyzer?.enabledPackageManagers ||
              baseDefaults.jobConfigs.analyzer.enabledPackageManagers,
          },
          advisor: {
            enabled:
              ortRun.jobConfigs.advisor !== undefined &&
              ortRun.jobConfigs.advisor !== null,
            skipExcluded:
              ortRun.jobConfigs.advisor?.skipExcluded ||
              baseDefaults.jobConfigs.advisor.skipExcluded,
            advisors:
              ortRun.jobConfigs.advisor?.advisors ||
              baseDefaults.jobConfigs.advisor.advisors,
          },
          scanner: {
            enabled:
              ortRun.jobConfigs.scanner !== undefined &&
              ortRun.jobConfigs.scanner !== null,
            skipConcluded:
              ortRun.jobConfigs.scanner?.skipConcluded ||
              baseDefaults.jobConfigs.scanner.skipConcluded,
            skipExcluded:
              ortRun.jobConfigs.scanner?.skipExcluded ||
              baseDefaults.jobConfigs.scanner.skipExcluded,
          },
          evaluator: {
            enabled:
              ortRun.jobConfigs.evaluator !== undefined &&
              ortRun.jobConfigs.evaluator !== null,
            ruleSet:
              ortRun.jobConfigs.evaluator?.ruleSet ||
              baseDefaults.jobConfigs.evaluator.ruleSet,
            licenseClassificationsFile:
              ortRun.jobConfigs.evaluator?.licenseClassificationsFile ||
              baseDefaults.jobConfigs.evaluator.licenseClassificationsFile,
            copyrightGarbageFile:
              ortRun.jobConfigs.evaluator?.copyrightGarbageFile ||
              baseDefaults.jobConfigs.evaluator.copyrightGarbageFile,
            resolutionsFile:
              ortRun.jobConfigs.evaluator?.resolutionsFile ||
              baseDefaults.jobConfigs.evaluator.resolutionsFile,
          },
          reporter: {
            enabled:
              ortRun.jobConfigs.reporter !== undefined &&
              ortRun.jobConfigs.reporter !== null,
            formats:
              ortRun.jobConfigs.reporter?.formats ||
              baseDefaults.jobConfigs.reporter.formats,
          },
          notifier: {
            enabled:
              ortRun.jobConfigs.notifier !== undefined &&
              ortRun.jobConfigs.notifier !== null,
            notifierRules:
              ortRun.jobConfigs.notifier?.notifierRules ||
              baseDefaults.jobConfigs.notifier.notifierRules,
            resolutionsFile:
              ortRun.jobConfigs.notifier?.resolutionsFile ||
              baseDefaults.jobConfigs.notifier.resolutionsFile,
            mail: {
              // Convert the recipient addresses string array coming from the back-end to an array of objects.
              // This needs to be done because the useFieldArray hook requires an array of objects.
              recipientAddresses:
                ortRun.jobConfigs.notifier?.mail?.recipientAddresses?.map(
                  (email) => ({ email })
                ) || baseDefaults.jobConfigs.notifier.mail.recipientAddresses,
              mailServerConfiguration: {
                hostName:
                  ortRun.jobConfigs.notifier?.mail?.mailServerConfiguration
                    ?.hostName ||
                  baseDefaults.jobConfigs.notifier.mail.mailServerConfiguration
                    .hostName,
                port:
                  ortRun.jobConfigs.notifier?.mail?.mailServerConfiguration
                    ?.port ||
                  baseDefaults.jobConfigs.notifier.mail.mailServerConfiguration
                    .port,
                username:
                  ortRun.jobConfigs.notifier?.mail?.mailServerConfiguration
                    ?.username ||
                  baseDefaults.jobConfigs.notifier.mail.mailServerConfiguration
                    .username,
                password:
                  ortRun.jobConfigs.notifier?.mail?.mailServerConfiguration
                    ?.password ||
                  baseDefaults.jobConfigs.notifier.mail.mailServerConfiguration
                    .password,
                useSsl:
                  ortRun.jobConfigs.notifier?.mail?.mailServerConfiguration
                    ?.useSsl ||
                  baseDefaults.jobConfigs.notifier.mail.mailServerConfiguration
                    .useSsl,
                fromAddress:
                  ortRun.jobConfigs.notifier?.mail?.mailServerConfiguration
                    ?.fromAddress ||
                  baseDefaults.jobConfigs.notifier.mail.mailServerConfiguration
                    .fromAddress,
              },
            },
            jira: {
              jiraRestClientConfiguration: {
                serverUrl:
                  ortRun.jobConfigs.notifier?.jira?.jiraRestClientConfiguration
                    ?.serverUrl ||
                  baseDefaults.jobConfigs.notifier.jira
                    .jiraRestClientConfiguration.serverUrl,
                username:
                  ortRun.jobConfigs.notifier?.jira?.jiraRestClientConfiguration
                    ?.username ||
                  baseDefaults.jobConfigs.notifier.jira
                    .jiraRestClientConfiguration.username,
                password:
                  ortRun.jobConfigs.notifier?.jira?.jiraRestClientConfiguration
                    ?.password ||
                  baseDefaults.jobConfigs.notifier.jira
                    .jiraRestClientConfiguration.password,
              },
            },
          },
          // Convert the parameters object map coming from the back-end to an array of key-value pairs.
          // This needs to be done because the useFieldArray hook requires an array of objects.
          parameters: ortRun.jobConfigs.parameters
            ? Object.entries(ortRun.jobConfigs.parameters).map(([k, v]) => ({
                key: k,
                value: v,
              }))
            : [],
        },
        // Convert the labels object map coming from the back-end to an array of key-value pairs.
        labels: ortRun.labels
          ? Object.entries(ortRun.labels).map(([k, v]) => ({
              key: k,
              value: v,
            }))
          : [],
        jobConfigContext:
          ortRun.jobConfigContext || baseDefaults.jobConfigContext,
      }
    : baseDefaults;

  return defaultValues;
}

/**
 * Due to API schema and requirements for the form schema, the form values can't be directly passed
 * to the API. This function converts form values to correct payload to create an ORT run.
 */
export function formValuesToPayload(
  values: z.infer<typeof createRunFormSchema>
): CreateOrtRun {
  // In ORT Server, running or not running a job for and ORT Run is decided
  // based on the presence or absence of the corresponding job configuration
  // in the request body. If a job is disabled in the UI, we pass "undefined"
  // as the configuration for that job in the request body, in effect leaving
  // it empty, and thus disabling the job.
  const analyzerConfig = {
    allowDynamicVersions: values.jobConfigs.analyzer.allowDynamicVersions,
    repositoryConfigPath: values.jobConfigs.analyzer.repositoryConfigPath,
    skipExcluded: values.jobConfigs.analyzer.skipExcluded,
    enabledPackageManagers: values.jobConfigs.analyzer.enabledPackageManagers,
  };

  const advisorConfig = values.jobConfigs.advisor.enabled
    ? {
        skipExcluded: values.jobConfigs.advisor.skipExcluded,
        advisors: values.jobConfigs.advisor.advisors,
      }
    : undefined;

  const scannerConfig = values.jobConfigs.scanner.enabled
    ? {
        createMissingArchives: true,
        skipConcluded: values.jobConfigs.scanner.skipConcluded,
        skipExcluded: values.jobConfigs.scanner.skipExcluded,
      }
    : undefined;

  const evaluatorConfig = values.jobConfigs.evaluator.enabled
    ? {
        // Only include the config parameter structures if the corresponding form fields are not empty.
        // In case they are empty, the default path from the config file provider will be used to
        // resolve the corresponding files.
        ruleSet: values.jobConfigs.evaluator.ruleSet || undefined,
        licenseClassificationsFile:
          values.jobConfigs.evaluator.licenseClassificationsFile || undefined,
        copyrightGarbageFile:
          values.jobConfigs.evaluator.copyrightGarbageFile || undefined,
        resolutionsFile:
          values.jobConfigs.evaluator.resolutionsFile || undefined,
      }
    : undefined;

  const reporterConfig = values.jobConfigs.reporter.enabled
    ? {
        formats: values.jobConfigs.reporter.formats,
      }
    : undefined;

  // Convert the recipient addresses back to an array of strings, as expected by the back-end.
  const addresses = values.jobConfigs.notifier.mail.recipientAddresses
    ? values.jobConfigs.notifier.mail.recipientAddresses.map(
        (recipient) => recipient.email
      )
    : undefined;
  const notifierConfig = values.jobConfigs.notifier.enabled
    ? {
        notifierRules: values.jobConfigs.notifier.notifierRules || undefined,
        resolutionsFile:
          values.jobConfigs.notifier.resolutionsFile || undefined,
        mail: {
          recipientAddresses: addresses || undefined,
          mailServerConfiguration: {
            hostName:
              values.jobConfigs.notifier.mail.mailServerConfiguration.hostName,
            port: values.jobConfigs.notifier.mail.mailServerConfiguration.port,
            username:
              values.jobConfigs.notifier.mail.mailServerConfiguration.username,
            password:
              values.jobConfigs.notifier.mail.mailServerConfiguration.password,
            useSsl:
              values.jobConfigs.notifier.mail.mailServerConfiguration.useSsl,
            fromAddress:
              values.jobConfigs.notifier.mail.mailServerConfiguration
                .fromAddress,
          },
        },
        jira: {
          jiraRestClientConfiguration: {
            serverUrl:
              values.jobConfigs.notifier.jira.jiraRestClientConfiguration
                .serverUrl,
            username:
              values.jobConfigs.notifier.jira.jiraRestClientConfiguration
                .username,
            password:
              values.jobConfigs.notifier.jira.jiraRestClientConfiguration
                .password,
          },
        },
      }
    : undefined;

  // Convert the parameters and labels arrays back to objects, as expected by the back-end.
  const parameters = values.jobConfigs.parameters
    ? values.jobConfigs.parameters.reduce(
        (acc, param) => {
          acc[param.key] = param.value;
          return acc;
        },
        {} as { [key: string]: string }
      )
    : undefined;
  const labels = values.labels
    ? values.labels.reduce(
        (acc, label) => {
          acc[label.key] = label.value;
          return acc;
        },
        {} as { [key: string]: string }
      )
    : undefined;

  const requestBody = {
    revision: values.revision,
    path: values.path,
    jobConfigs: {
      analyzer: analyzerConfig,
      advisor: advisorConfig,
      scanner: scannerConfig,
      evaluator: evaluatorConfig,
      reporter: reporterConfig,
      notifier: notifierConfig,
      parameters: parameters,
    },
    labels: labels,
    jobConfigContext: values.jobConfigContext,
  };

  return requestBody;
}
