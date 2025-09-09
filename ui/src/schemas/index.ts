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

import {
  $OrtRunStatus,
  $Severity,
  $VulnerabilityRating,
} from '@/api/requests/schemas.gen';

// Enum schema for the groupId parameter of the Groups endpoints
export const groupsSchema = z.enum(['admins', 'writers', 'readers']);

// Enum schema for the possible values of the status parameter of the ORT run
export const runStatusSchema = z.enum($OrtRunStatus.enum);

// Enum schema for the possible values of the severities
export const severitySchema = z.enum($Severity.enum);

// Enum schema and type for the resolved statuses of issues, vulnerabilites, and rule violations
export const itemResolvedSchema = z.enum(['Resolved', 'Unresolved']);
export type ItemResolved = z.infer<typeof itemResolvedSchema>;

// Enum schema for the possible values of the advisory overall vulnerability ratings
export const vulnerabilityRatingSchema = z.enum($VulnerabilityRating.enum);

// Enum schema and type for the possible values of the issue categories.
export const issueCategorySchema = z.enum([
  'Build System',
  'Missing Data',
  'Infrastructure',
  'Other',
]);
export type IssueCategory = z.infer<typeof issueCategorySchema>;

// Enum schema and type for the possible package ID types.
export const packageIdTypeSchema = z.enum(['PURL', 'ORT_ID']);
export type PackageIdType = z.infer<typeof packageIdTypeSchema>;

// These schemas are needed for validation now that the hey-api package doesn't
// expose the const definitions like $RepositoryType anymore; it only exposes
// the type.
//
// TODO: hey-api has a Zod plugin, but it won't live alongside the old
// 7nohe query client, so once that's removed, the code here can probably
// be refactored to use the exposed definitions from hey-api.

// Enum schema for the possible repository types.
export const repositoryTypeSchema = z.enum([
  'GIT',
  'GIT_REPO',
  'MERCURIAL',
  'SUBVERSION',
]);

// Search parameter validation schemas

export const paginationSearchParameterSchema = z.object({
  page: z.number().optional(),
  pageSize: z.number().optional(),
});

export const orgPaginationSearchParameterSchema = z.object({
  orgPage: z.number().optional(),
  orgPageSize: z.number().optional(),
});

export const productPaginationSearchParameterSchema = z.object({
  productPage: z.number().optional(),
  productPageSize: z.number().optional(),
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

export const itemStatusSearchParameterSchema = z.object({
  itemResolved: z.array(itemResolvedSchema).optional(),
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
