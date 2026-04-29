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

import {
  AnalyzerJobConfiguration,
  InfrastructureService,
  PostRepositoryRun,
  ReporterJobConfiguration,
} from '@/api';
import { convertArrayToMap } from './form-primitives';
import { createPluginPayload } from './plugin-utils';
import type { CreateRunFormValues } from './run-schema';

/**
 * Due to API schema and requirements for the form schema, the form values can't be directly passed
 * to the API. This function converts form values to correct payload to create an ORT run.
 */
export function formValuesToPayload(
  values: CreateRunFormValues
): PostRepositoryRun {
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
  const environmentDefinitions =
    values.jobConfigs.analyzer.environmentDefinitionsEnabled &&
    values.jobConfigs.analyzer.environmentDefinitions &&
    Object.values(values.jobConfigs.analyzer.environmentDefinitions).some(
      (entries) => entries.length > 0
    )
      ? values.jobConfigs.analyzer.environmentDefinitions
      : undefined;

  const environmentVariables =
    values.jobConfigs.analyzer.environmentVariables &&
    values.jobConfigs.analyzer.environmentVariables.length > 0
      ? values.jobConfigs.analyzer.environmentVariables
      : undefined;

  const infrastructureServices: InfrastructureService[] =
    values.jobConfigs.analyzer.infrastructureServices?.map((service) => ({
      credentialsTypes: service.credentialsTypes,
      description: service.description,
      name: service.name,
      passwordSecretRef: service.passwordSecretRef,
      url: service.url,
      usernameSecretRef: service.usernameSecretRef,
    })) || [];

  const environmentConfig = {
    infrastructureServices,
    ...(environmentDefinitions ? { environmentDefinitions } : {}),
    ...(environmentVariables ? { environmentVariables } : {}),
  };

  const analyzerConfig: AnalyzerJobConfiguration = {
    allowDynamicVersions: values.jobConfigs.analyzer.allowDynamicVersions,
    repositoryConfigPath:
      values.jobConfigs.analyzer.repositoryConfigPath || undefined,
    skipExcluded: values.jobConfigs.analyzer.skipExcluded,
    environmentConfig,
    // Determine the enabled package managers by filtering the packageManagers object
    // and finding those for which 'enabled' is true.
    enabledPackageManagers: [
      ...getEnabledPackageManagers(values.jobConfigs.analyzer.packageManagers),
      'Unmanaged',
    ],
    // Construct packageManagerOptions by including options for enabled package managers
    // that have options set in the form.
    packageManagerOptions: getPackageManagerOptions(
      values.jobConfigs.analyzer.packageManagers
    ),
    keepAliveWorker: values.jobConfigs.analyzer.keepAliveWorker || undefined,
  };

  //
  // Advisor configuration
  //

  const advisorConfig = values.jobConfigs.advisor.enabled
    ? {
        skipExcluded: values.jobConfigs.advisor.skipExcluded,
        advisors: values.jobConfigs.advisor.advisors,
        config: createPluginPayload(
          values.jobConfigs.advisor.config,
          values.jobConfigs.advisor.advisors
        ),
        keepAliveWorker: values.jobConfigs.advisor.keepAliveWorker || undefined,
      }
    : undefined;

  //
  // Scanner configuration
  //

  const scannerConfig = values.jobConfigs.scanner.enabled
    ? (() => {
        const allScanners = values.jobConfigs.scanner.scanners;
        const scopes = values.jobConfigs.scanner.scannerScopes;
        // When any scanner is set to 'packages' or 'projects', both lists must be
        // populated explicitly (because an empty projectScanners means "all scanners
        // scan everything"). Scanners set to 'both' (or with no explicit scope) must
        // therefore appear in *both* lists whenever the override is active.
        const hasProjectScannerOverride = allScanners.some(
          (s) => scopes[s] === 'projects' || scopes[s] === 'packages'
        );
        // 'both' / undefined / 'packages' → goes into scanners (package scanning)
        const packageScanners = allScanners.filter(
          (s) => !scopes[s] || scopes[s] === 'both' || scopes[s] === 'packages'
        );
        // 'both' / undefined / 'projects' → goes into projectScanners (project scanning)
        // Only populated when the override is active; otherwise omitted so that the
        // backend treats every scanner in `scanners` as scanning both.
        const projectScanners = hasProjectScannerOverride
          ? allScanners.filter(
              (s) =>
                !scopes[s] || scopes[s] === 'both' || scopes[s] === 'projects'
            )
          : undefined;
        return {
          createMissingArchives: true,
          skipConcluded: values.jobConfigs.scanner.skipConcluded,
          skipExcluded: values.jobConfigs.scanner.skipExcluded,
          keepAliveWorker:
            values.jobConfigs.scanner.keepAliveWorker || undefined,
          // When the projectScanners override is active but no scanners scan packages,
          // send an explicit empty array so the backend does not fall back to its
          // default ["ScanCode"] (which it applies when the field is absent/null).
          scanners: hasProjectScannerOverride
            ? packageScanners
            : packageScanners.length > 0
              ? packageScanners
              : undefined,
          projectScanners,
          config: createPluginPayload(
            values.jobConfigs.scanner.config,
            allScanners
          ),
        };
      })()
    : undefined;

  //
  // Evaluator configuration
  //

  const evaluatorConfig = values.jobConfigs.evaluator.enabled
    ? {
        keepAliveWorker:
          values.jobConfigs.evaluator.keepAliveWorker || undefined,
      }
    : undefined;

  //
  // Reporter configuration
  //

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

  // If WebApp and the deduplicateDependencyTree option are enabled, add the configuration.
  const webAppEnabled = values.jobConfigs.reporter.formats.includes('WebApp');
  if (webAppEnabled && values.jobConfigs.reporter.deduplicateDependencyTree) {
    config.WebApp = {
      options: {
        deduplicateDependencyTree: 'true',
      },
      secrets: {},
    };
  }

  const reporterConfig = values.jobConfigs.reporter.enabled
    ? {
        formats: values.jobConfigs.reporter.formats,
        config: Object.keys(config).length > 0 ? config : undefined,
        keepAliveWorker:
          values.jobConfigs.reporter.keepAliveWorker || undefined,
      }
    : undefined;

  //
  // Notifier configuration
  //

  // Convert the recipient addresses back to an array of strings, as expected by the back-end.
  const addresses = values.jobConfigs.notifier.recipientAddresses
    ? values.jobConfigs.notifier.recipientAddresses.map(
        (recipient) => recipient.email
      )
    : undefined;
  const notifierConfig = values.jobConfigs.notifier.enabled
    ? {
        recipientAddresses: addresses || undefined,
        keepAliveWorker:
          values.jobConfigs.notifier.keepAliveWorker || undefined,
      }
    : undefined;

  //
  // Create the payload from worker configurations
  //

  const parameters = values.jobConfigs.parameters
    ? convertArrayToMap(values.jobConfigs.parameters)
    : undefined;
  const labels = values.labels ? convertArrayToMap(values.labels) : undefined;

  return {
    revision: values.revision,
    path: values.path,
    jobConfigs: {
      analyzer: analyzerConfig,
      advisor: advisorConfig,
      scanner: scannerConfig,
      evaluator: evaluatorConfig,
      reporter: reporterConfig,
      notifier: notifierConfig,
      parameters,
    },
    labels,
    jobConfigContext: values.jobConfigContext,
  };
}
