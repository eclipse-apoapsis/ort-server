/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

export type OrtRun = {
  id: number;
  index: number;
  repositoryId: number;
  revision: string;
  path?: Record<string, any>;
  createdAt: string;
  finishedAt?: Record<string, any>;
  jobConfigs: {
    analyzer?: {
      allowDynamicVersions?: boolean;
      disabledPackageManagers?: Record<string, any>;
      enabledPackageManagers?: Record<string, any>;
      environmentConfig?: {
        infrastructureServices: Array<{
          name: string;
          url: string;
          description?: Record<string, any>;
          usernameSecretRef: string;
          passwordSecretRef: string;
          excludeFromNetrc?: boolean;
        }>;
        environmentDefinitions?: Record<string, Array<Record<string, string>>>;
        environmentVariables?: Array<{
          name: string;
          secretName: string;
        }>;
        strict?: boolean;
      };
      packageCurationProviders?: Array<{
        type: string;
        id?: string;
        enabled?: boolean;
        options?: Record<string, string>;
        secrets?: Record<string, string>;
      }>;
      packageManagerOptions?: Record<
        string,
        {
          mustRunAfter?: Record<string, any>;
          options?: Record<string, string>;
        }
      >;
      repositoryConfigPath?: Record<string, any>;
      skipExcluded?: Record<string, any>;
    };
    advisor?: {
      advisors?: Array<string>;
      skipExcluded?: boolean;
      config?: Record<
        string,
        {
          options: Record<string, string>;
          secrets: Record<string, string>;
        }
      >;
    };
    scanner?: {
      createMissingArchives?: Record<string, any>;
      detectedLicenseMappings?: Record<string, string>;
      ignorePatterns?: Record<string, any>;
      projectScanners?: Record<string, any>;
      scanners?: Record<string, any>;
      skipConcluded?: Record<string, any>;
      skipExcluded?: boolean;
      config?: Record<
        string,
        {
          options: Record<string, string>;
          secrets: Record<string, string>;
        }
      >;
    };
    evaluator?: {
      copyrightGarbageFile?: Record<string, any>;
      licenseClassificationsFile?: Record<string, any>;
      packageConfigurationProviders?: Array<{
        type: string;
        id?: string;
        enabled?: boolean;
        options?: Record<string, string>;
        secrets?: Record<string, string>;
      }>;
      resolutionsFile?: Record<string, any>;
      ruleSet?: Record<string, any>;
    };
    reporter?: {
      copyrightGarbageFile?: Record<string, any>;
      formats?: Array<string>;
      licenseClassificationsFile?: Record<string, any>;
      packageConfigurationProviders?: Array<{
        type: string;
        id?: string;
        enabled?: boolean;
        options?: Record<string, string>;
        secrets?: Record<string, string>;
      }>;
      resolutionsFile?: Record<string, any>;
      assetFiles?: Array<{
        sourcePath: string;
        targetFolder?: Record<string, any>;
        targetName?: Record<string, any>;
      }>;
      assetDirectories?: Array<{
        sourcePath: string;
        targetFolder?: Record<string, any>;
        targetName?: Record<string, any>;
      }>;
      config?: Record<
        string,
        {
          options: Record<string, string>;
          secrets: Record<string, string>;
        }
      >;
      nameMappings?: Record<
        string,
        {
          namePrefix: string;
        }
      >;
    };
    notifier?: {
      notifierRules?: Record<string, any>;
      resolutionsFile?: Record<string, any>;
      mail?: {
        recipientAddresses?: Record<string, any>;
        mailServerConfiguration?: {
          hostName: string;
          port: number;
          username: string;
          password: string;
          useSsl: boolean;
          fromAddress: string;
        };
      };
    };
    parameters?: Record<string, string>;
  };
  resolvedJobConfigs?: {
    analyzer?: {
      allowDynamicVersions?: boolean;
      disabledPackageManagers?: Record<string, any>;
      enabledPackageManagers?: Record<string, any>;
      environmentConfig?: {
        infrastructureServices: Array<{
          name: string;
          url: string;
          description?: Record<string, any>;
          usernameSecretRef: string;
          passwordSecretRef: string;
          excludeFromNetrc?: boolean;
        }>;
        environmentDefinitions?: Record<string, Array<Record<string, string>>>;
        environmentVariables?: Array<{
          name: string;
          secretName: string;
        }>;
        strict?: boolean;
      };
      packageCurationProviders?: Array<{
        type: string;
        id?: string;
        enabled?: boolean;
        options?: Record<string, string>;
        secrets?: Record<string, string>;
      }>;
      packageManagerOptions?: Record<
        string,
        {
          mustRunAfter?: Record<string, any>;
          options?: Record<string, string>;
        }
      >;
      repositoryConfigPath?: Record<string, any>;
      skipExcluded?: Record<string, any>;
    };
    advisor?: {
      advisors?: Array<string>;
      skipExcluded?: boolean;
      config?: Record<
        string,
        {
          options: Record<string, string>;
          secrets: Record<string, string>;
        }
      >;
    };
    scanner?: {
      createMissingArchives?: Record<string, any>;
      detectedLicenseMappings?: Record<string, string>;
      ignorePatterns?: Record<string, any>;
      projectScanners?: Record<string, any>;
      scanners?: Record<string, any>;
      skipConcluded?: Record<string, any>;
      skipExcluded?: boolean;
      config?: Record<
        string,
        {
          options: Record<string, string>;
          secrets: Record<string, string>;
        }
      >;
    };
    evaluator?: {
      copyrightGarbageFile?: Record<string, any>;
      licenseClassificationsFile?: Record<string, any>;
      packageConfigurationProviders?: Array<{
        type: string;
        id?: string;
        enabled?: boolean;
        options?: Record<string, string>;
        secrets?: Record<string, string>;
      }>;
      resolutionsFile?: Record<string, any>;
      ruleSet?: Record<string, any>;
    };
    reporter?: {
      copyrightGarbageFile?: Record<string, any>;
      formats?: Array<string>;
      licenseClassificationsFile?: Record<string, any>;
      packageConfigurationProviders?: Array<{
        type: string;
        id?: string;
        enabled?: boolean;
        options?: Record<string, string>;
        secrets?: Record<string, string>;
      }>;
      resolutionsFile?: Record<string, any>;
      assetFiles?: Array<{
        sourcePath: string;
        targetFolder?: Record<string, any>;
        targetName?: Record<string, any>;
      }>;
      assetDirectories?: Array<{
        sourcePath: string;
        targetFolder?: Record<string, any>;
        targetName?: Record<string, any>;
      }>;
      config?: Record<
        string,
        {
          options: Record<string, string>;
          secrets: Record<string, string>;
        }
      >;
      nameMappings?: Record<
        string,
        {
          namePrefix: string;
        }
      >;
    };
    notifier?: {
      notifierRules?: Record<string, any>;
      resolutionsFile?: Record<string, any>;
      mail?: {
        recipientAddresses?: Record<string, any>;
        mailServerConfiguration?: {
          hostName: string;
          port: number;
          username: string;
          password: string;
          useSsl: boolean;
          fromAddress: string;
        };
      };
    };
    parameters?: Record<string, string>;
  };
  jobs: {
    analyzer?: {
      id: number;
      createdAt: string;
      startedAt?: Record<string, any>;
      finishedAt?: Record<string, any>;
      configuration: {
        allowDynamicVersions?: boolean;
        disabledPackageManagers?: Record<string, any>;
        enabledPackageManagers?: Record<string, any>;
        environmentConfig?: {
          infrastructureServices: Array<{
            name: string;
            url: string;
            description?: Record<string, any>;
            usernameSecretRef: string;
            passwordSecretRef: string;
            excludeFromNetrc?: boolean;
          }>;
          environmentDefinitions?: Record<
            string,
            Array<Record<string, string>>
          >;
          environmentVariables?: Array<{
            name: string;
            secretName: string;
          }>;
          strict?: boolean;
        };
        packageCurationProviders?: Array<{
          type: string;
          id?: string;
          enabled?: boolean;
          options?: Record<string, string>;
          secrets?: Record<string, string>;
        }>;
        packageManagerOptions?: Record<
          string,
          {
            mustRunAfter?: Record<string, any>;
            options?: Record<string, string>;
          }
        >;
        repositoryConfigPath?: Record<string, any>;
        skipExcluded?: Record<string, any>;
      };
      status: OrtRun.status;
    };
    advisor?: {
      id: number;
      createdAt: string;
      startedAt?: Record<string, any>;
      finishedAt?: Record<string, any>;
      configuration: {
        advisors?: Array<string>;
        skipExcluded?: boolean;
        config?: Record<
          string,
          {
            options: Record<string, string>;
            secrets: Record<string, string>;
          }
        >;
      };
      status: OrtRun.status;
    };
    scanner?: {
      id: number;
      createdAt: string;
      startedAt?: Record<string, any>;
      finishedAt?: Record<string, any>;
      configuration: {
        createMissingArchives?: Record<string, any>;
        detectedLicenseMappings?: Record<string, string>;
        ignorePatterns?: Record<string, any>;
        projectScanners?: Record<string, any>;
        scanners?: Record<string, any>;
        skipConcluded?: Record<string, any>;
        skipExcluded?: boolean;
        config?: Record<
          string,
          {
            options: Record<string, string>;
            secrets: Record<string, string>;
          }
        >;
      };
      status: OrtRun.status;
    };
    evaluator?: {
      id: number;
      createdAt: string;
      startedAt?: Record<string, any>;
      finishedAt?: Record<string, any>;
      configuration: {
        copyrightGarbageFile?: Record<string, any>;
        licenseClassificationsFile?: Record<string, any>;
        packageConfigurationProviders?: Array<{
          type: string;
          id?: string;
          enabled?: boolean;
          options?: Record<string, string>;
          secrets?: Record<string, string>;
        }>;
        resolutionsFile?: Record<string, any>;
        ruleSet?: Record<string, any>;
      };
      status: OrtRun.status;
    };
    reporter?: {
      id: number;
      createdAt: string;
      startedAt?: Record<string, any>;
      finishedAt?: Record<string, any>;
      configuration: {
        copyrightGarbageFile?: Record<string, any>;
        formats?: Array<string>;
        licenseClassificationsFile?: Record<string, any>;
        packageConfigurationProviders?: Array<{
          type: string;
          id?: string;
          enabled?: boolean;
          options?: Record<string, string>;
          secrets?: Record<string, string>;
        }>;
        resolutionsFile?: Record<string, any>;
        assetFiles?: Array<{
          sourcePath: string;
          targetFolder?: Record<string, any>;
          targetName?: Record<string, any>;
        }>;
        assetDirectories?: Array<{
          sourcePath: string;
          targetFolder?: Record<string, any>;
          targetName?: Record<string, any>;
        }>;
        config?: Record<
          string,
          {
            options: Record<string, string>;
            secrets: Record<string, string>;
          }
        >;
        nameMappings?: Record<
          string,
          {
            namePrefix: string;
          }
        >;
      };
      status: OrtRun.status;
      reportFilenames?: Record<string, any>;
    };
    notifier?: {
      id: number;
      createdAt: string;
      startedAt?: Record<string, any>;
      finishedAt?: Record<string, any>;
      configuration: {
        notifierRules?: Record<string, any>;
        resolutionsFile?: Record<string, any>;
        mail?: {
          recipientAddresses?: Record<string, any>;
          mailServerConfiguration?: {
            hostName: string;
            port: number;
            username: string;
            password: string;
            useSsl: boolean;
            fromAddress: string;
          };
        };
      };
      status: OrtRun.status;
    };
  };
  status: OrtRun.status;
  labels: Record<string, string>;
  issues: Array<{
    timestamp: string;
    source: string;
    message: string;
    severity: string;
  }>;
  jobConfigContext?: Record<string, any>;
  resolvedJobConfigContext?: Record<string, any>;
};

export namespace OrtRun {
  export enum status {
    CREATED = 'CREATED',
    SCHEDULED = 'SCHEDULED',
    RUNNING = 'RUNNING',
    FAILED = 'FAILED',
    FINISHED = 'FINISHED',
  }
}
