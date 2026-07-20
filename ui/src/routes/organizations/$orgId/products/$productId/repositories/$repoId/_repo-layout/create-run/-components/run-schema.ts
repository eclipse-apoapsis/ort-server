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

import { PreconfiguredPluginDescriptor } from '@/api';
import { zAnalyzerPhase, zInfrastructureService } from '@/api/zod.gen';
import { environmentDefinitionsSchema } from '@/lib/types';
import {
  environmentVariableSchema,
  keyValueSchema,
  packageManagerOptionsSchema,
} from './form-primitives';
import {
  createPluginConfigSchema,
  validateRequiredPluginOptions,
} from './plugin-utils';

export const createRunFormSchema = (
  advisorPlugins: PreconfiguredPluginDescriptor[],
  scannerPlugins: PreconfiguredPluginDescriptor[],
  packageCurationProviderPlugins: PreconfiguredPluginDescriptor[],
  packageConfigurationProviderPlugins: PreconfiguredPluginDescriptor[]
) => {
  const advisorConfigSchema: Record<string, z.ZodTypeAny> = {};

  advisorPlugins.forEach((plugin) => {
    advisorConfigSchema[plugin.id] = createPluginConfigSchema(plugin);
  });

  const scannerConfigSchema: Record<string, z.ZodTypeAny> = {};
  scannerPlugins.forEach((plugin) => {
    scannerConfigSchema[plugin.id] = createPluginConfigSchema(plugin);
  });

  const packageCurationProviderConfigSchema: Record<string, z.ZodTypeAny> = {};
  packageCurationProviderPlugins.forEach((plugin) => {
    packageCurationProviderConfigSchema[plugin.id] =
      createPluginConfigSchema(plugin);
  });

  const packageConfigurationProviderConfigSchema: Record<string, z.ZodTypeAny> =
    {};
  packageConfigurationProviderPlugins.forEach((plugin) => {
    packageConfigurationProviderConfigSchema[plugin.id] =
      createPluginConfigSchema(plugin);
  });

  return z.object({
    revision: z.string(),
    path: z.string(),
    jobConfigs: z
      .object({
        analyzer: z
          .object({
            enabled: z.boolean(),
            repositoryConfigPath: z.string().optional(),
            allowDynamicVersions: z.boolean(),
            skipExcluded: z.boolean(),
            environmentDefinitions: environmentDefinitionsSchema.optional(),
            environmentVariables: z.array(environmentVariableSchema).optional(),
            infrastructureServices: z.array(zInfrastructureService).optional(),
            keepAliveWorker: z.boolean(),
            keepAlivePhases: z.array(zAnalyzerPhase).optional(),
            packageCurationProviders: z.array(z.string()),
            packageCurationProviderConfig: z
              .object(packageCurationProviderConfigSchema)
              .optional(),
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
                Gleam: packageManagerOptionsSchema,
                GoMod: packageManagerOptionsSchema,
                Gradle: packageManagerOptionsSchema,
                GradleInspector: packageManagerOptionsSchema,
                Maven: packageManagerOptionsSchema,
                NPM: packageManagerOptionsSchema,
                NuGet: packageManagerOptionsSchema,
                OrtProjectFile: packageManagerOptionsSchema,
                PIP: packageManagerOptionsSchema,
                Pipenv: packageManagerOptionsSchema,
                PNPM: packageManagerOptionsSchema,
                Poetry: packageManagerOptionsSchema,
                Pub: packageManagerOptionsSchema,
                SBT: packageManagerOptionsSchema,
                SPDX: packageManagerOptionsSchema,
                SpdxDocumentFile: packageManagerOptionsSchema,
                Stack: packageManagerOptionsSchema,
                SwiftPM: packageManagerOptionsSchema,
                Tycho: packageManagerOptionsSchema,
                Yarn: packageManagerOptionsSchema,
                Yarn2: packageManagerOptionsSchema,
              })
              .refine((schema) => {
                // Ensure that not both Gradle and GradleInspector are enabled at the same time.
                return !(
                  schema.Gradle.enabled && schema.GradleInspector.enabled
                );
              }, '"Gradle Legacy" and "Gradle" cannot be enabled at the same time.'),
          })
          .superRefine((data, ctx) => {
            validateRequiredPluginOptions(
              packageCurationProviderPlugins,
              data.packageCurationProviders,
              data.packageCurationProviderConfig as
                | Record<
                    string,
                    Record<string, Record<string, unknown>> | undefined
                  >
                | undefined,
              ctx,
              'packageCurationProviderConfig'
            );
          }),
        advisor: z
          .object({
            enabled: z.boolean(),
            skipExcluded: z.boolean(),
            keepAliveWorker: z.boolean(),
            advisors: z.array(z.string()),
            config: z.object(advisorConfigSchema).optional(),
          })
          .superRefine((data, ctx) => {
            validateRequiredPluginOptions(
              advisorPlugins,
              data.advisors,
              data.config as
                | Record<
                    string,
                    Record<string, Record<string, unknown>> | undefined
                  >
                | undefined,
              ctx
            );
          }),
        scanner: z
          .object({
            enabled: z.boolean(),
            skipConcluded: z.boolean(),
            skipExcluded: z.boolean(),
            keepAliveWorker: z.boolean(),
            scanners: z.array(z.string()),
            scannerScopes: z.record(
              z.string(),
              z.enum(['both', 'packages', 'projects']).optional()
            ),
            config: z.object(scannerConfigSchema).optional(),
          })
          .superRefine((data, ctx) => {
            validateRequiredPluginOptions(
              scannerPlugins,
              data.scanners,
              data.config as
                | Record<
                    string,
                    Record<string, Record<string, unknown>> | undefined
                  >
                | undefined,
              ctx
            );
          }),
        evaluator: z.object({
          enabled: z.boolean(),
          ruleSet: z.string().optional(),
          licenseClassificationsFile: z.string().optional(),
          copyrightGarbageFile: z.string().optional(),
          resolutionsFile: z.string().optional(),
          keepAliveWorker: z.boolean(),
          packageConfigurationProviders: z.array(z.string()),
          packageConfigurationProviderConfig: z
            .object(packageConfigurationProviderConfigSchema)
            .optional(),
        }),
        reporter: z.object({
          enabled: z.boolean(),
          formats: z.array(z.string()),
          deduplicateDependencyTree: z.boolean().optional(),
          keepAliveWorker: z.boolean(),
          packageConfigurationProviders: z.array(z.string()),
          packageConfigurationProviderConfig: z
            .object(packageConfigurationProviderConfigSchema)
            .optional(),
        }),
        notifier: z.object({
          enabled: z.boolean(),
          recipientAddresses: z
            .array(z.object({ email: z.string() }))
            .optional(),
          keepAliveWorker: z.boolean(),
        }),
        parameters: z.array(keyValueSchema).optional(),
        ruleSet: z.string().optional(),
      })
      .superRefine((data, ctx) => {
        if (data.evaluator.enabled) {
          validateRequiredPluginOptions(
            packageConfigurationProviderPlugins,
            data.evaluator.packageConfigurationProviders,
            data.evaluator.packageConfigurationProviderConfig as
              | Record<
                  string,
                  Record<string, Record<string, unknown>> | undefined
                >
              | undefined,
            ctx,
            ['evaluator', 'packageConfigurationProviderConfig']
          );
        }

        if (data.reporter.enabled && !data.evaluator.enabled) {
          validateRequiredPluginOptions(
            packageConfigurationProviderPlugins,
            data.reporter.packageConfigurationProviders,
            data.reporter.packageConfigurationProviderConfig as
              | Record<
                  string,
                  Record<string, Record<string, unknown>> | undefined
                >
              | undefined,
            ctx,
            ['reporter', 'packageConfigurationProviderConfig']
          );
        }
      }),
    labels: z.array(keyValueSchema).optional(),
    jobConfigContext: z.string().optional(),
    environmentConfigPath: z.string().optional(),
  });
};

export type CreateRunFormValues = z.infer<
  ReturnType<typeof createRunFormSchema>
>;

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
      result.push({
        path: path ? `${path}.${key}` : key,
        message: error.message as string,
      });
    } else if (typeof error === 'object') {
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
