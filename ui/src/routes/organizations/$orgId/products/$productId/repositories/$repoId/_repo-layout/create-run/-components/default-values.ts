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

import { OrtRun, PreconfiguredPluginDescriptor } from '@/api';
import { environmentDefinitionsSchema, PackageManagerId } from '@/lib/types';
import { convertMapToArray } from './form-primitives';
import {
  getPluginDefaultValues,
  mergePluginConfigs,
  reconstructScannerSelection,
} from './plugin-utils';
import type { CreateRunFormValues } from './run-schema';

/**
 * Get the default values for the create run form. The form can be provided with a previously run
 * ORT run, in which case the values from it are used as defaults. Otherwise uses base defaults.
 */
export function defaultValues(
  ortRun: OrtRun | null,
  advisorPlugins: PreconfiguredPluginDescriptor[],
  scannerPlugins: PreconfiguredPluginDescriptor[],
  isSuperuser: boolean
): CreateRunFormValues {
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
          ortRun.jobConfigs.analyzer?.enabledPackageManagers === undefined
            ? enabledByDefault
            : ortRun.jobConfigs.analyzer?.enabledPackageManagers?.includes(
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

  // Find out if any of the reporters had their options.deduplicateDependencyTree set to true in the previous run.
  // This is used to set the default value for the deduplicateDependencyTree toggle in the UI.
  const deduplicateDependencyTreeEnabled = ortRun
    ? ortRun.jobConfigs.reporter?.config &&
      Object.keys(ortRun.jobConfigs.reporter.config ?? {}).some((key) => {
        const config = ortRun.jobConfigs.reporter?.config?.[key];
        return config?.options?.deduplicateDependencyTree === 'true';
      })
    : false;

  const advisorPluginDefaultValues = getPluginDefaultValues(advisorPlugins);
  const scannerPluginDefaultValues = getPluginDefaultValues(scannerPlugins);
  const scannerDefaults = {
    scanners: ['ScanCode'],
    scannerScopes: {
      ScanCode: 'both' as const,
    } as Record<string, 'both' | 'packages' | 'projects'>,
    config: scannerPluginDefaultValues,
  };

  // Default values for the form: edit only these, not the defaultValues object.
  const baseDefaults: CreateRunFormValues = {
    revision: '',
    path: '',
    jobConfigs: {
      analyzer: {
        enabled: true,
        repositoryConfigPath: '',
        allowDynamicVersions: true,
        skipExcluded: true,
        keepAliveWorker: false,
        environmentDefinitionsEnabled: false,
        environmentDefinitions: undefined,
        infrastructureServices: [],
        packageManagers: {
          Bazel: defaultPackageManagerOptions('Bazel'),
          Bower: defaultPackageManagerOptions('Bower'),
          Bundler: defaultPackageManagerOptions('Bundler'),
          Cargo: defaultPackageManagerOptions('Cargo'),
          Carthage: defaultPackageManagerOptions('Carthage'),
          CocoaPods: defaultPackageManagerOptions('CocoaPods'),
          Composer: defaultPackageManagerOptions('Composer'),
          Conan: defaultPackageManagerOptions('Conan'),
          Gleam: defaultPackageManagerOptions('Gleam'),
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
          SPDX: defaultPackageManagerOptions('SPDX'),
          SpdxDocumentFile: defaultPackageManagerOptions('SpdxDocumentFile'),
          Stack: defaultPackageManagerOptions('Stack'),
          SwiftPM: defaultPackageManagerOptions('SwiftPM'),
          Tycho: defaultPackageManagerOptions('Tycho'),
          Yarn: defaultPackageManagerOptions('Yarn'),
          Yarn2: defaultPackageManagerOptions('Yarn2'),
        },
      },
      advisor: {
        enabled: true,
        skipExcluded: true,
        advisors: ['OSV', 'VulnerableCode'],
        config: advisorPluginDefaultValues,
        keepAliveWorker: false,
      },
      scanner: {
        enabled: true,
        skipConcluded: true,
        skipExcluded: true,
        keepAliveWorker: false,
        ...scannerDefaults,
      },
      evaluator: {
        enabled: true,
        ruleSet: '',
        licenseClassificationsFile: '',
        copyrightGarbageFile: '',
        resolutionsFile: '',
        keepAliveWorker: false,
      },
      reporter: {
        enabled: true,
        formats: ['CycloneDX', 'SpdxDocument', 'WebApp'],
        deduplicateDependencyTree: false,
        keepAliveWorker: false,
      },
      notifier: {
        enabled: false,
        recipientAddresses: [],
        keepAliveWorker: false,
      },
    },
    labels: undefined,
    jobConfigContext: '',
  };

  // Default values for the form are either taken from "baseDefaults" or,
  // when a rerun action has been taken, fetched from the ORT Run that is
  // being rerun. Whenever a rerun job config parameter is missing, use the
  // default value.
  const existingEnvironmentDefinitions =
    ortRun?.jobConfigs.analyzer?.environmentConfig?.environmentDefinitions;

  const parsedEnvironmentDefinitions = existingEnvironmentDefinitions
    ? environmentDefinitionsSchema.parse(existingEnvironmentDefinitions)
    : undefined;

  const hasEnvironmentDefinitions =
    parsedEnvironmentDefinitions !== undefined &&
    Object.values(parsedEnvironmentDefinitions).some(
      (entries) => entries && entries.length > 0
    );

  return ortRun
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
            environmentDefinitionsEnabled: hasEnvironmentDefinitions,
            environmentDefinitions: hasEnvironmentDefinitions
              ? parsedEnvironmentDefinitions
              : undefined,
            environmentVariables:
              ortRun.jobConfigs.analyzer?.environmentConfig
                ?.environmentVariables || undefined,
            infrastructureServices:
              ortRun.jobConfigs.analyzer?.environmentConfig
                ?.infrastructureServices ||
              baseDefaults.jobConfigs.analyzer.infrastructureServices,
            keepAliveWorker:
              (ortRun.jobConfigs.analyzer?.keepAliveWorker && isSuperuser) ||
              baseDefaults.jobConfigs.analyzer.keepAliveWorker,
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
            config: mergePluginConfigs(
              ortRun?.jobConfigs?.advisor?.config,
              advisorPluginDefaultValues
            ),
            keepAliveWorker:
              (ortRun.jobConfigs.advisor?.keepAliveWorker && isSuperuser) ||
              baseDefaults.jobConfigs.advisor.keepAliveWorker,
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
            keepAliveWorker:
              (ortRun.jobConfigs.scanner?.keepAliveWorker && isSuperuser) ||
              baseDefaults.jobConfigs.scanner.keepAliveWorker,
            ...reconstructScannerSelection(
              ortRun.jobConfigs.scanner?.scanners ?? null,
              ortRun.jobConfigs.scanner?.projectScanners ?? null,
              scannerDefaults,
              scannerPluginDefaultValues
            ),
          },
          evaluator: {
            enabled:
              ortRun.jobConfigs.evaluator !== undefined &&
              ortRun.jobConfigs.evaluator !== null,
            keepAliveWorker:
              (ortRun.jobConfigs.evaluator?.keepAliveWorker && isSuperuser) ||
              baseDefaults.jobConfigs.evaluator.keepAliveWorker,
          },
          reporter: {
            enabled:
              ortRun.jobConfigs.reporter !== undefined &&
              ortRun.jobConfigs.reporter !== null,
            formats:
              ortRun.jobConfigs.reporter?.formats?.map((format) =>
                format === 'CycloneDx' ? 'CycloneDX' : format
              ) || baseDefaults.jobConfigs.reporter.formats,
            deduplicateDependencyTree:
              deduplicateDependencyTreeEnabled || undefined,
            keepAliveWorker:
              (ortRun.jobConfigs.reporter?.keepAliveWorker && isSuperuser) ||
              baseDefaults.jobConfigs.reporter.keepAliveWorker,
          },
          notifier: {
            enabled:
              ortRun.jobConfigs.notifier !== undefined &&
              ortRun.jobConfigs.notifier !== null,
            recipientAddresses:
              ortRun.jobConfigs.notifier?.recipientAddresses?.map((email) => ({
                email,
              })) || baseDefaults.jobConfigs.notifier.recipientAddresses,
            keepAliveWorker:
              (ortRun.jobConfigs.notifier?.keepAliveWorker && isSuperuser) ||
              baseDefaults.jobConfigs.notifier.keepAliveWorker,
          },
          parameters: convertMapToArray(ortRun.jobConfigs.parameters || {}),
          ruleSet: baseDefaults.jobConfigs.ruleSet,
        },
        labels: convertMapToArray(ortRun.labels || {}),
        jobConfigContext:
          ortRun.jobConfigContext || baseDefaults.jobConfigContext,
      }
    : baseDefaults;
}
