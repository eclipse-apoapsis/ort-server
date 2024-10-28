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

import { OrtRunStatus, Severity } from '@/api/requests';

// Pagination schema that is used for search parameter validation
export const paginationSchema = z.object({
  page: z.number().optional(),
  pageSize: z.number().optional(),
});

// Grouping schema that is used for search parameter validation
export const tableGroupingSchema = z.object({
  groups: z.array(z.string()).optional(),
});

// Enum schema for the groupId parameter of the Groups endpoints
export const groupsSchema = z.enum(['admins', 'writers', 'readers']);

// Enum schema for the possible values of the status parameter of the ORT run
export const ortRunStatus: z.ZodEnum<[OrtRunStatus, ...OrtRunStatus[]]> =
  z.enum(['CREATED', 'ACTIVE', 'FAILED', 'FINISHED', 'FINISHED_WITH_ISSUES']);

// Status schema that is used for search parameter validation
export const statusSchema = z.object({
  status: z.array(ortRunStatus).optional(),
});

// Enum schema for the possible values of the issue severities
export const issueSeverity: z.ZodEnum<[Severity, ...Severity[]]> = z.enum([
  'HINT',
  'WARNING',
  'ERROR',
]);

// Issue severity schema that is used for search parameter validation
export const issueSeveritySchema = z.object({
  severity: z.array(issueSeverity).optional(),
});
