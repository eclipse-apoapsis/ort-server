/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

export type CreateOrtRun = {
    revision: string;
    path?: Record<string, any>;
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
            packageManagerOptions?: Record<string, {
                mustRunAfter?: Record<string, any>;
                options?: Record<string, string>;
            }>;
            repositoryConfigPath?: Record<string, any>;
            skipExcluded?: Record<string, any>;
        };
        advisor?: {
            advisors?: Array<string>;
            skipExcluded?: boolean;
            config?: Record<string, {
                options: Record<string, string>;
                secrets: Record<string, string>;
            }>;
        };
        scanner?: {
            createMissingArchives?: Record<string, any>;
            detectedLicenseMappings?: Record<string, string>;
            ignorePatterns?: Record<string, any>;
            projectScanners?: Record<string, any>;
            scanners?: Record<string, any>;
            skipConcluded?: Record<string, any>;
            skipExcluded?: boolean;
            config?: Record<string, {
                options: Record<string, string>;
                secrets: Record<string, string>;
            }>;
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
            config?: Record<string, {
                options: Record<string, string>;
                secrets: Record<string, string>;
            }>;
            nameMappings?: Record<string, {
                namePrefix: string;
            }>;
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
    labels?: Record<string, string>;
    jobConfigContext?: Record<string, any>;
};

