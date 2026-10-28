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

import type {
  DefaultPlugins,
  DefaultPluginsCategory,
  OrtRun,
  PreconfiguredPluginDescriptor,
} from '@/api';
import type { ScannerScope } from '@/components/form/plugin-multi-select-field';
import { environmentDefinitionsSchema } from '@/lib/types';
import { convertMapToArray } from './form-primitives';
import { includesExcludesToFormEntries } from './includes-excludes';
import {
  getPluginDefaultValues,
  mergePluginConfigs,
  packageManagerConfigsToFormValues,
  providerPluginConfigsToFormValues,
  reconstructScannerSelection,
  UNMANAGED_PACKAGE_MANAGER_ID,
} from './plugin-utils';
import type { CreateRunFormValues } from './run-schema';

/**
 * Get the selected scanners and their scopes from the default package and project scanners.
 */
function getDefaultScannerSelection(
  packageScanners: readonly string[],
  projectScanners: readonly string[]
): {
  scanners: string[];
  scannerScopes: Record<string, ScannerScope>;
} {
  const packageScannerSet = new Set(packageScanners);
  const projectScannerSet = new Set(projectScanners);
  const scanners = Array.from(
    new Set([...packageScanners, ...projectScanners])
  );

  const getScope = (scanner: string) => {
    if (!projectScannerSet.has(scanner)) return 'packages' as const;
    if (!packageScannerSet.has(scanner)) return 'projects' as const;
    return 'both' as const;
  };

  return {
    scanners,
    scannerScopes: Object.fromEntries(
      scanners.map((scanner) => [scanner, getScope(scanner)])
    ),
  };
}

/** The plugins available in the create run form, grouped by plugin type. */
export type PluginGroups = {
  advisorPlugins: PreconfiguredPluginDescriptor[];
  scannerPlugins: PreconfiguredPluginDescriptor[];
  reporterPlugins: PreconfiguredPluginDescriptor[];
  packageCurationProviderPlugins: PreconfiguredPluginDescriptor[];
  packageConfigurationProviderPlugins: PreconfiguredPluginDescriptor[];
  packageManagerPlugins: PreconfiguredPluginDescriptor[];
};

/** The plugins that are selected by default in the plugin lists of the create run form. */
export type DefaultPluginSelections = {
  packageManagers: string[];
  packageCurationProviders: string[];
  advisors: string[];
  scanners: string[];
  scannerScopes: Record<string, ScannerScope>;
  packageConfigurationProviders: string[];
  reporters: string[];
};

/** The selections used for the form values until the default plugins are loaded. */
const NO_DEFAULT_PLUGIN_SELECTIONS: DefaultPluginSelections = {
  packageManagers: [],
  packageCurationProviders: [],
  advisors: [],
  scanners: [],
  scannerScopes: {},
  packageConfigurationProviders: [],
  reporters: [],
};

/**
 * Get the plugins that are selected by default in the create run form from the given default
 * plugins, ignoring those that are not available.
 */
export function getDefaultPluginSelections(
  defaultPlugins: readonly DefaultPlugins[],
  pluginGroups: PluginGroups
): DefaultPluginSelections {
  const getDefaultPluginIds = (
    category: DefaultPluginsCategory,
    plugins: readonly PreconfiguredPluginDescriptor[]
  ) => {
    const availablePluginIds = new Set(plugins.map((plugin) => plugin.id));

    return (
      defaultPlugins.find((defaults) => defaults.category === category)
        ?.pluginIds ?? []
    ).filter((pluginId) => availablePluginIds.has(pluginId));
  };

  const scannerSelection = getDefaultScannerSelection(
    getDefaultPluginIds('SCANNER', pluginGroups.scannerPlugins),
    getDefaultPluginIds('PROJECT_SCANNER', pluginGroups.scannerPlugins)
  );

  return {
    packageManagers: getDefaultPluginIds(
      'PACKAGE_MANAGER',
      pluginGroups.packageManagerPlugins
    ),
    packageCurationProviders: getDefaultPluginIds(
      'PACKAGE_CURATION_PROVIDER',
      pluginGroups.packageCurationProviderPlugins
    ),
    advisors: getDefaultPluginIds('ADVISOR', pluginGroups.advisorPlugins),
    scanners: scannerSelection.scanners,
    scannerScopes: scannerSelection.scannerScopes,
    packageConfigurationProviders: getDefaultPluginIds(
      'PACKAGE_CONFIGURATION_PROVIDER',
      pluginGroups.packageConfigurationProviderPlugins
    ),
    reporters: getDefaultPluginIds('REPORTER', pluginGroups.reporterPlugins),
  };
}

/**
 * Get the default values for the create run form. The form can be provided with a previously run
 * ORT run, in which case the values from it are used as defaults. Otherwise uses base defaults,
 * which select the given default plugins, or no plugins if they are not loaded yet.
 */
export function defaultValues(
  ortRun: OrtRun | null,
  {
    advisorPlugins,
    scannerPlugins,
    reporterPlugins,
    packageCurationProviderPlugins,
    packageConfigurationProviderPlugins,
    packageManagerPlugins,
  }: PluginGroups,
  isSuperuser: boolean,
  defaultSelections: DefaultPluginSelections = NO_DEFAULT_PLUGIN_SELECTIONS
): CreateRunFormValues {
  const advisorPluginDefaultValues = getPluginDefaultValues(advisorPlugins);
  const scannerPluginDefaultValues = getPluginDefaultValues(scannerPlugins);
  const reporterPluginDefaultValues = getPluginDefaultValues(reporterPlugins);
  const packageCurationProviderPluginDefaultValues = getPluginDefaultValues(
    packageCurationProviderPlugins
  );
  const packageCurationProviderFormValues = providerPluginConfigsToFormValues(
    ortRun?.jobConfigs.analyzer?.packageCurationProviders
  );
  const packageConfigurationProviderPluginDefaultValues =
    getPluginDefaultValues(packageConfigurationProviderPlugins);
  const evaluatorPackageConfigurationProviderFormValues =
    providerPluginConfigsToFormValues(
      ortRun?.jobConfigs.evaluator?.packageConfigurationProviders
    );
  const reporterPackageConfigurationProviderFormValues =
    providerPluginConfigsToFormValues(
      ortRun?.jobConfigs.reporter?.packageConfigurationProviders
    );
  const packageManagerPluginDefaultValues = getPluginDefaultValues(
    packageManagerPlugins
  );
  const packageManagerFormValues = packageManagerConfigsToFormValues(
    ortRun?.jobConfigs.analyzer?.packageManagerOptions
  );

  // Default values for the form: edit only these, not the defaultValues object.
  const baseDefaults: CreateRunFormValues = {
    revision: '',
    path: '',
    scopes: [],
    paths: [],
    jobConfigs: {
      analyzer: {
        enabled: true,
        repositoryConfigPath: '',
        allowDynamicVersions: true,
        skipExcluded: true,
        keepAliveWorker: false,
        keepAlivePhases: [],
        packageCurationProviders: defaultSelections.packageCurationProviders,
        packageCurationProviderConfig:
          packageCurationProviderPluginDefaultValues,
        packageManagers: defaultSelections.packageManagers,
        packageManagerConfig: packageManagerPluginDefaultValues,
        packageManagerMustRunAfter: {},
      },
      advisor: {
        enabled: true,
        skipExcluded: true,
        advisors: defaultSelections.advisors,
        config: advisorPluginDefaultValues,
        keepAliveWorker: false,
      },
      scanner: {
        enabled: true,
        skipConcluded: true,
        skipExcluded: true,
        scanners: defaultSelections.scanners,
        scannerScopes: defaultSelections.scannerScopes,
        config: scannerPluginDefaultValues,
        keepAliveWorker: false,
      },
      evaluator: {
        enabled: true,
        ruleSet: '',
        licenseClassificationsFile: '',
        copyrightGarbageFile: '',
        resolutionsFile: '',
        keepAliveWorker: false,
        packageConfigurationProviders:
          defaultSelections.packageConfigurationProviders,
        packageConfigurationProviderConfig:
          packageConfigurationProviderPluginDefaultValues,
      },
      reporter: {
        enabled: true,
        formats: defaultSelections.reporters,
        config: reporterPluginDefaultValues,
        keepAliveWorker: false,
        packageConfigurationProviders:
          defaultSelections.packageConfigurationProviders,
        packageConfigurationProviderConfig:
          packageConfigurationProviderPluginDefaultValues,
      },
      notifier: {
        enabled: false,
        recipientAddresses: [],
        keepAliveWorker: false,
      },
    },
    labels: undefined,
    jobConfigContext: '',
    environmentConfigPath: '',
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
      (entries) => entries.length > 0
    );

  return ortRun
    ? {
        revision: ortRun.revision || baseDefaults.revision,
        path: ortRun.path || baseDefaults.path,
        scopes: includesExcludesToFormEntries(ortRun.scopes),
        paths: includesExcludesToFormEntries(ortRun.paths),
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
            // The enabled package managers and their configuration are taken from the
            // previous run, falling back to the defaults if they are not set.
            packageManagers: ortRun.jobConfigs.analyzer?.enabledPackageManagers
              ? ortRun.jobConfigs.analyzer.enabledPackageManagers.filter(
                  (packageManagerId) =>
                    packageManagerId !== UNMANAGED_PACKAGE_MANAGER_ID &&
                    packageManagerPlugins.some(
                      (plugin) => plugin.id === packageManagerId
                    )
                )
              : baseDefaults.jobConfigs.analyzer.packageManagers,
            packageManagerConfig: mergePluginConfigs(
              packageManagerFormValues.config,
              packageManagerPluginDefaultValues,
              packageManagerPlugins
            ),
            packageManagerMustRunAfter: packageManagerFormValues.mustRunAfter,
            environmentDefinitions: hasEnvironmentDefinitions
              ? parsedEnvironmentDefinitions
              : undefined,
            environmentVariables:
              ortRun.jobConfigs.analyzer?.environmentConfig
                ?.environmentVariables || undefined,
            // Provider lists that are not set in the previous run use the defaults.
            packageCurationProviders: ortRun.jobConfigs.analyzer
              ?.packageCurationProviders
              ? packageCurationProviderFormValues.selectedPluginIds
              : baseDefaults.jobConfigs.analyzer.packageCurationProviders,
            packageCurationProviderConfig: mergePluginConfigs(
              packageCurationProviderFormValues.config,
              packageCurationProviderPluginDefaultValues,
              packageCurationProviderPlugins
            ),
            keepAliveWorker:
              (ortRun.jobConfigs.analyzer?.keepAliveWorker && isSuperuser) ||
              baseDefaults.jobConfigs.analyzer.keepAliveWorker,
            keepAlivePhases:
              ortRun.jobConfigs.analyzer?.keepAliveWorker &&
              isSuperuser &&
              ortRun.jobConfigs.analyzer.keepAlivePhases?.length
                ? ortRun.jobConfigs.analyzer.keepAlivePhases
                : baseDefaults.jobConfigs.analyzer.keepAlivePhases,
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
              ortRun.jobConfigs.advisor?.config,
              advisorPluginDefaultValues,
              advisorPlugins
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
              defaultSelections
            ),
            config: mergePluginConfigs(
              ortRun.jobConfigs.scanner?.config,
              scannerPluginDefaultValues,
              scannerPlugins
            ),
          },
          evaluator: {
            enabled:
              ortRun.jobConfigs.evaluator !== undefined &&
              ortRun.jobConfigs.evaluator !== null,
            packageConfigurationProviders: ortRun.jobConfigs.evaluator
              ?.packageConfigurationProviders
              ? evaluatorPackageConfigurationProviderFormValues.selectedPluginIds
              : baseDefaults.jobConfigs.evaluator.packageConfigurationProviders,
            packageConfigurationProviderConfig: mergePluginConfigs(
              evaluatorPackageConfigurationProviderFormValues.config,
              packageConfigurationProviderPluginDefaultValues,
              packageConfigurationProviderPlugins
            ),
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
            config: mergePluginConfigs(
              ortRun.jobConfigs.reporter?.config,
              reporterPluginDefaultValues,
              reporterPlugins
            ),
            packageConfigurationProviders: ortRun.jobConfigs.reporter
              ?.packageConfigurationProviders
              ? reporterPackageConfigurationProviderFormValues.selectedPluginIds
              : baseDefaults.jobConfigs.reporter.packageConfigurationProviders,
            packageConfigurationProviderConfig: mergePluginConfigs(
              reporterPackageConfigurationProviderFormValues.config,
              packageConfigurationProviderPluginDefaultValues,
              packageConfigurationProviderPlugins
            ),
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
        labels: convertMapToArray(ortRun.labels),
        jobConfigContext:
          ortRun.jobConfigContext || baseDefaults.jobConfigContext,
        environmentConfigPath:
          ortRun.environmentConfigPath || baseDefaults.environmentConfigPath,
      }
    : baseDefaults;
}
