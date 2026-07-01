/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import { createFileRoute } from '@tanstack/react-router';
import z from 'zod';

import { LoadingIndicator } from '@/components/loading-indicator';
import {
  findingsPaginationSearchParameterSchema,
  paginationSearchParameterSchema,
  sortingSearchParameterSchema,
} from '@/schemas';
import { SnippetFindingsView } from './-components/snippet-findings-view';

const nestedSortingSearchParameterSchema = z.object({
  findingsSortBy: z
    .array(
      z.object({
        id: z.string(),
        desc: z.boolean(),
      })
    )
    .optional(),
  snippetsPage: z.number().optional(),
  snippetsPageSize: z.number().optional(),
  snippetsSortBy: z
    .array(
      z.object({
        id: z.string(),
        desc: z.boolean(),
      })
    )
    .optional(),
});

const snippetFindingsSearchSchema = z.object({
  ...paginationSearchParameterSchema.shape,
  ...sortingSearchParameterSchema.shape,
  ...findingsPaginationSearchParameterSchema.shape,
  ...nestedSortingSearchParameterSchema.shape,
});

export const Route = createFileRoute(
  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/snippet-findings/'
)({
  validateSearch: snippetFindingsSearchSchema,
  component: SnippetFindingsView,
  pendingComponent: LoadingIndicator,
});
