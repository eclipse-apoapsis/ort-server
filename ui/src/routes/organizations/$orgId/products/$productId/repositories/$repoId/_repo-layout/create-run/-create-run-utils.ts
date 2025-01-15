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

import { FieldErrors } from 'react-hook-form';
import { z } from 'zod';

import {
  AnalyzerJobConfiguration,
  CreateOrtRun,
  OrtRun,
  ReporterJobConfiguration,
} from '@/api/requests';
import { packageManagers } from '../../-types';

const keyValueSchema = z.object({
  key: z.string(),
  value: z.string(), // Allow empty values for now
});

const packageManagerOptionsSchema = z.object({
  enabled: z.boolean(),
  mustRunAfter: z
    .array(
      z
        .string()
        // Only accept valid package manager IDs to the string array in the form.
        .refine((pkgMgr): pkgMgr is PackageManagerId =>
          packageManagers.some((pm) => pm.id === pkgMgr)
        )
    )
    .optional(),
  options: z.array(keyValueSchema).optional(),
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
      packageManagers: z
        .object({
          Bazel: packageManagerOptionsSchema,
          Bower: packageManagerOptionsSchema,
          Bundler: packageManagerOptionsSchema,
          Cargo: packageManagerOptionsSchema,
          Carthage: packageManagerOptionsSchema,
          CocoaPods: packageManagerOptionsSchema,
          Composer: packageManagerOptionsSchema,
          Conan: packageManagerOptionsSchema,
          GoMod: packageManagerOptionsSchema,
          Gradle: packageManagerOptionsSchema,
          GradleInspector: packageManagerOptionsSchema,
          Maven: packageManagerOptionsSchema,
          NPM: packageManagerOptionsSchema,
          NuGet: packageManagerOptionsSchema,
          PIP: packageManagerOptionsSchema,
          Pipenv: packageManagerOptionsSchema,
          PNPM: packageManagerOptionsSchema,
          Poetry: packageManagerOptionsSchema,
          Pub: packageManagerOptionsSchema,
          SBT: packageManagerOptionsSchema,
          SpdxDocumentFile: packageManagerOptionsSchema,
          Stack: packageManagerOptionsSchema,
          SwiftPM: packageManagerOptionsSchema,
          Yarn: packageManagerOptionsSchema,
          Yarn2: packageManagerOptionsSchema,
        })
        .refine((schema) => {
          // Ensure that not both Gradle and GradleInspector are enabled at the same time.
          return !(schema.Gradle.enabled && schema.GradleInspector.enabled);
        }, '"Gradle Legacy" and "Gradle" cannot be enabled at the same time.'),
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
 * Converts an object map coming from the back-end to an array of key-value pairs.
 * This is useful for form handling where an array of objects is required.
 *
 * @param objectMap - The object map from the back-end.
 * @returns An array of key-value pairs.
 */
const convertMapToArray = (objectMap: {
  [key: string]: string;
}): { key: string; value: string }[] => {
  return Object.entries(objectMap).map(([key, value]) => ({
    key,
    value,
  }));
};

/**
 * Converts an array of key-value pairs to an object map.
 * This is useful for converting form data back to the format expected by the back-end.
 *
 * @param keyValueArray - An array of key-value pairs.
 * @returns The object map.
 */
const convertArrayToMap = (
  keyValueArray: { key: string; value: string }[]
): { [key: string]: string } => {
  return keyValueArray.reduce(
    (acc, { key, value }) => {
      acc[key] = value;
      return acc;
    },
    {} as { [key: string]: string }
  );
};

// Define the type for the returned error messages with full paths
type FlattenedError = {
  path: string;
  message: string;
};

/**
 * Flatten the error object returned by react-hook-form to an array of error messages with full paths.
 * The function traverses recursively the object paths until it finds the error messages, and stores
 * the messages and paths into the messages to the return array.
 *
 * @param errors - An array of error objects to be processed.
 * @param path - The path to the current object in the object tree. It is used for recursion.
 * @returns An array of error messages with full paths.
 */
export const flattenErrors = (
  errors: FieldErrors<CreateRunFormValues>,
  path = ''
): FlattenedError[] => {
  let result: FlattenedError[] = [];

  for (const key in errors) {
    const errorKey = key as keyof FieldErrors<CreateRunFormValues>;
    const error = errors[errorKey];

    if (error?.message) {
      // Base case: if this entry has a message, add it to the result
      result.push({
        path: path ? `${path}.${key}` : key,
        message: error.message as string, // Cast to string since message is usually typed as string | undefined
      });
    } else if (typeof error === 'object') {
      // Recursive case: traverse nested objects
      result = result.concat(
        flattenErrors(
          error as FieldErrors<CreateRunFormValues>,
          path ? `${path}.${key}` : key
        )
      );
    }
  }

  return result;
};

// Derive the type of packageManagerId from the ids of packageManagers
export type PackageManagerId = (typeof packageManagers)[number]['id'];

/**
 * Get the default values for the create run form. The form can be provided with a previously run
 * ORT run, in which case the values from it are used as defaults. Otherwise uses base defaults.
 */
export function defaultValues(
  ortRun: OrtRun | null
): z.infer<typeof createRunFormSchema> {
  /**
   * Constructs the default options for a package manager, either as a blank set of options
   * or from an earlier ORT run if rerun functionality is used.
   *
   * @param packageManagerId The ID of the package manager.
   * @param enabledByDefault Whether the package manager should be enabled by default.
   * @returns The default options.
   */
  const defaultPackageManagerOptions = (
    packageManagerId: PackageManagerId,
    enabledByDefault: boolean = true
  ) => {
    if (ortRun) {
      return {
        enabled:
          ortRun.jobConfigs.analyzer?.enabledPackageManagers?.includes(
            packageManagerId
          ) || false,
        mustRunAfter:
          (ortRun.jobConfigs.analyzer?.packageManagerOptions?.[packageManagerId]
            ?.mustRunAfter as PackageManagerId[]) || [],
        options: convertMapToArray(
          ortRun.jobConfigs.analyzer?.packageManagerOptions?.[packageManagerId]
            ?.options || {}
        ),
      };
    }
    return {
      enabled: enabledByDefault,
      mustRunAfter: [],
      options: [],
    };
  };

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
        packageManagers: {
          Bazel: defaultPackageManagerOptions('Bazel'),
          Bower: defaultPackageManagerOptions('Bower'),
          Bundler: defaultPackageManagerOptions('Bundler'),
          Cargo: defaultPackageManagerOptions('Cargo'),
          Carthage: defaultPackageManagerOptions('Carthage'),
          CocoaPods: defaultPackageManagerOptions('CocoaPods'),
          Composer: defaultPackageManagerOptions('Composer'),
          Conan: defaultPackageManagerOptions('Conan'),
          GoMod: defaultPackageManagerOptions('GoMod'),
          Gradle: defaultPackageManagerOptions('Gradle', false),
          GradleInspector: defaultPackageManagerOptions('GradleInspector'),
          Maven: defaultPackageManagerOptions('Maven'),
          NPM: defaultPackageManagerOptions('NPM'),
          NuGet: defaultPackageManagerOptions('NuGet'),
          PIP: defaultPackageManagerOptions('PIP'),
          Pipenv: defaultPackageManagerOptions('Pipenv'),
          PNPM: defaultPackageManagerOptions('PNPM'),
          Poetry: defaultPackageManagerOptions('Poetry'),
          Pub: defaultPackageManagerOptions('Pub'),
          SBT: defaultPackageManagerOptions('SBT'),
          SpdxDocumentFile: defaultPackageManagerOptions('SpdxDocumentFile'),
          Stack: defaultPackageManagerOptions('Stack'),
          SwiftPM: defaultPackageManagerOptions('SwiftPM'),
          Yarn: defaultPackageManagerOptions('Yarn'),
          Yarn2: defaultPackageManagerOptions('Yarn2'),
        },
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
        formats: ['CycloneDX', 'SpdxDocument', 'WebApp'],
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
            // defaultPackageManagerOptions gets the options from the previous run already in the
            // baseDefaults object, so those values can be used here.
            packageManagers: baseDefaults.jobConfigs.analyzer.packageManagers,
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
              // To fix retriggering (with "Reuse" button) of old runs with wrong data
              // ("CycloneDx" instead of "CycloneDX"), convert this to correct plugin
              // configuration.
              ortRun.jobConfigs.reporter?.formats?.map((format) =>
                format === 'CycloneDx' ? 'CycloneDX' : format
              ) || baseDefaults.jobConfigs.reporter.formats,
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
          parameters: convertMapToArray(ortRun.jobConfigs.parameters || {}),
        },
        // Convert the labels object map coming from the back-end to an array of key-value pairs.
        labels: convertMapToArray(ortRun.labels || {}),
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
  /**
   * A helper function to get the enabled package managers from the form values.
   *
   * @param packageManagers Package managers object from the form values.
   * @returns Array of enabled package manager IDs.
   */
  const getEnabledPackageManagers = (
    packageManagers: typeof values.jobConfigs.analyzer.packageManagers
  ) => {
    return Object.keys(packageManagers).filter(
      (pm) =>
        packageManagers[
          // Ensure that TypeScript infers the correct type for the key.
          // This is safe because the key is always a valid package manager ID.
          pm as keyof typeof values.jobConfigs.analyzer.packageManagers
        ].enabled
    );
  };

  /**
   * A helper function to get the package manager options for the enabled package managers.
   * This is done by converting the packageManagers object into an array of key-value pairs,
   * filtering out the disabled package managers, mapping the filtered array to an array of
   * objects with the package manager ID as the key and the options as the value, and then
   * reducing this array to a single object.
   *
   * @param packageManagers Package managers object from the form values.
   * @returns Single object with package manager IDs as keys and options as values.
   */
  const getPackageManagerOptions = (
    packageManagers: typeof values.jobConfigs.analyzer.packageManagers
  ) => {
    const options = Object.entries(packageManagers)
      .filter(
        // Skip package managers that are not enabled or have no extra options set.
        ([, pm]) =>
          pm.enabled &&
          ((pm.options && pm.options.length > 0) ||
            (pm.mustRunAfter && pm.mustRunAfter.length > 0))
      )
      .map(([pmId, pm]) => {
        // Build the filtered options object, including only non-empty properties.
        const filteredOptions = {
          ...(pm.mustRunAfter?.length ? { mustRunAfter: pm.mustRunAfter } : {}),
          ...(pm.options?.length
            ? { options: convertArrayToMap(pm.options) }
            : {}),
        };
        // Return the object only if it has valid options.
        return Object.keys(filteredOptions).length > 0
          ? { [pmId]: filteredOptions }
          : {};
      })
      // Combine all package manager objects into a single result.
      .reduce((acc, pm) => ({ ...acc, ...pm }), {});
    // If no options are set, return undefined.
    return Object.keys(options).length > 0 ? options : undefined;
  };

  //
  // Analyzer configuration
  //

  // In ORT Server, running or not running a job for and ORT Run is decided
  // based on the presence or absence of the corresponding job configuration
  // in the request body. If a job is disabled in the UI, we pass "undefined"
  // as the configuration for that job in the request body, in effect leaving
  // it empty, and thus disabling the job.
  const analyzerConfig: AnalyzerJobConfiguration = {
    allowDynamicVersions: values.jobConfigs.analyzer.allowDynamicVersions,
    repositoryConfigPath:
      values.jobConfigs.analyzer.repositoryConfigPath || undefined,
    skipExcluded: values.jobConfigs.analyzer.skipExcluded,
    // Determine the enabled package managers by filtering the packageManagers object
    // and finding those for which 'enabled' is true.
    enabledPackageManagers: [
      ...getEnabledPackageManagers(values.jobConfigs.analyzer.packageManagers),
      'Unmanaged', // Add "Unmanaged" package manager to all runs
    ],
    // Construct packageManagerOptions by including options for enabled package managers
    // that have options set in the form.
    packageManagerOptions: getPackageManagerOptions(
      values.jobConfigs.analyzer.packageManagers
    ),
  };

  //
  // Advisor configuration
  //

  const advisorConfig = values.jobConfigs.advisor.enabled
    ? {
        skipExcluded: values.jobConfigs.advisor.skipExcluded,
        advisors: values.jobConfigs.advisor.advisors,
      }
    : undefined;

  //
  // Scanner configuration
  //

  const scannerConfig = values.jobConfigs.scanner.enabled
    ? {
        createMissingArchives: true,
        skipConcluded: values.jobConfigs.scanner.skipConcluded,
        skipExcluded: values.jobConfigs.scanner.skipExcluded,
      }
    : undefined;

  //
  // Evaluator configuration
  //

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

  //
  // Reporter configuration
  //

  // Check if CycloneDX, SPDX, and/or NOTICE file reports are enabled in the form,
  // and configure them to use all output formats, accordingly.

  const cycloneDxEnabled =
    values.jobConfigs.reporter.formats.includes('CycloneDX');
  const spdxDocumentEnabled =
    values.jobConfigs.reporter.formats.includes('SpdxDocument');
  const noticeFileEnabled =
    values.jobConfigs.reporter.formats.includes('PlainTextTemplate');

  const config: ReporterJobConfiguration['config'] = {};

  if (spdxDocumentEnabled) {
    config.SpdxDocument = {
      options: {
        outputFileFormats: 'YAML,JSON',
      },
      secrets: {},
    };
  }

  if (cycloneDxEnabled) {
    config.CycloneDX = {
      options: {
        outputFileFormats: 'XML,JSON',
      },
      secrets: {},
    };
  }

  if (noticeFileEnabled) {
    config.PlainTextTemplate = {
      options: {
        templateIds: 'NOTICE_DEFAULT,NOTICE_SUMMARY',
      },
      secrets: {},
    };
  }

  const reporterConfig = values.jobConfigs.reporter.enabled
    ? {
        formats: values.jobConfigs.reporter.formats,
        config: Object.keys(config).length > 0 ? config : undefined,
      }
    : undefined;

  //
  // Notifier configuration
  //

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

  //
  // Create the payload from worker configurations
  //

  // Convert the parameters and labels arrays back to objects, as expected by the back-end.
  const parameters = values.jobConfigs.parameters
    ? convertArrayToMap(values.jobConfigs.parameters)
    : undefined;
  const labels = values.labels ? convertArrayToMap(values.labels) : undefined;

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
