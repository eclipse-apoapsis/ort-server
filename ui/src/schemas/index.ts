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

import { OrtRunStatus, Severity, VulnerabilityRating } from '@/api/requests';

// Enum schema for the groupId parameter of the Groups endpoints
export const groupsSchema = z.enum(['admins', 'writers', 'readers']);

// Enum schema for the possible values of the status parameter of the ORT run
export const runStatusSchema: z.ZodEnum<[OrtRunStatus, ...OrtRunStatus[]]> =
  z.enum(['CREATED', 'ACTIVE', 'FAILED', 'FINISHED', 'FINISHED_WITH_ISSUES']);

// Enum schema for the possible values of the severities
export const severitySchema: z.ZodEnum<[Severity, ...Severity[]]> = z.enum([
  'HINT',
  'WARNING',
  'ERROR',
]);

// Enum schema for the possible values of the advisory overall vulnerability ratings
export const vulnerabilityRatingSchema: z.ZodEnum<
  [VulnerabilityRating, ...VulnerabilityRating[]]
> = z.enum(['NONE', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL']);

// Enum schema and type for the possible values of the issue categories.
export const issueCategorySchema = z.enum([
  'Build System',
  'Missing Data',
  'Infrastructure',
  'Other',
]);
export type IssueCategory = z.infer<typeof issueCategorySchema>;

// Search parameter validation schemas

export const paginationSearchParameterSchema = z.object({
  page: z.number().optional(),
  pageSize: z.number().optional(),
});

// sortBy needs to be of form "columnId.asc" or "columnId.desc"
export const sortingSearchParameterSchema = z.object({
  sortBy: z
    .array(
      z.object({
        id: z.string(),
        desc: z.boolean(),
      })
    )
    .optional(),
});

export const statusSearchParameterSchema = z.object({
  status: z.array(runStatusSchema).optional(),
});

export const severitySearchParameterSchema = z.object({
  severity: z.array(severitySchema).optional(),
});

export const packageIdentifierSearchParameterSchema = z.object({
  pkgId: z.string().optional(),
});

export const projectIdentifierSearchParameterSchema = z.object({
  projectId: z.string().optional(),
});

export const definitionFilePathSearchParameterSchema = z.object({
  definitionFilePath: z.string().optional(),
});

// Refine validates that the license texts are unique.
export const declaredLicenseSearchParameterSchema = z.object({
  declaredLicense: z
    .array(z.string())
    .refine((items) => new Set(items).size === items.length)
    .optional(),
});

export const issueCategorySearchParameterSchema = z.object({
  category: z.array(issueCategorySchema).optional(),
});

export const vulnerabilityRatingSearchParameterSchema = z.object({
  rating: z.array(vulnerabilityRatingSchema).optional(),
});

// This schema is used to validate the search parameter for the items marked for inspection
// so the link can be shared.
export const markedSearchParameterSchema = z.object({
  marked: z.string().optional(),
});
