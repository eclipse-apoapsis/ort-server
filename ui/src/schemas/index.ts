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

import { zOrtRunStatus, zSeverity, zVulnerabilityRating } from '@/api/zod.gen';

// Enum schema for the groupId parameter of the Groups endpoints
export const groupsSchema = z.enum(['admins', 'writers', 'readers']);

// Enum schema and type for the resolved statuses of issues, vulnerabilites, and rule violations
export const itemResolvedSchema = z.enum(['Resolved', 'Unresolved']);
export type ItemResolved = z.infer<typeof itemResolvedSchema>;

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
  status: z.array(zOrtRunStatus).optional(),
});

export const severitySearchParameterSchema = z.object({
  severity: z.array(zSeverity).optional(),
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
  rating: z.array(zVulnerabilityRating).optional(),
});

export const externalIdSearchParameterSchema = z.object({
  externalId: z.string().optional(),
});

// This schema is used to validate the search parameter for the items marked for inspection
// so the link can be shared.
export const markedSearchParameterSchema = z.object({
  marked: z.string().optional(),
});

// This schema is used to validate the filter parameter when filtering
// organizations, products, or repositories with regexp.
export const filterByNameSearchParameterSchema = z.object({
  filter: z.string().optional(),
});
