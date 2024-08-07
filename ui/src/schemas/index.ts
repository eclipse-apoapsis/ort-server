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

import z from 'zod';

// Pagination schema that is used for search parameter validation
export const paginationSchema = z.object({
  page: z.number().optional(),
  pageSize: z.number().optional(),
});

const keyValueSchema = z.object({
  key: z.string().min(1),
  value: z.string(), // Allow empty values for now
});

export const formSchema = z.object({
  revision: z.string(),
  path: z.string(),
  jobConfigs: z.object({
    analyzer: z.object({
      enabled: z.boolean(),
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
