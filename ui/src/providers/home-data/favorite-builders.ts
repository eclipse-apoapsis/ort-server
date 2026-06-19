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

import type { Organization, OrtRun, Product, Repository } from '@/api';
import {
  buildOrganizationFavoriteId,
  buildProductFavoriteId,
  buildRepositoryFavoriteId,
  buildRunFavoriteId,
} from './favorites';
import { buildRecentRunId } from './recent-runs';
import type { FavoriteItemInput, RecentRunItemInput } from './types';

const getRepositoryName = (repository: Repository) =>
  repository.name || repository.url;

export const buildOrganizationFavorite = (
  organization: Organization
): FavoriteItemInput => ({
  id: buildOrganizationFavoriteId(organization.id),
  type: 'organization',
  name: organization.name,
  breadcrumbs: [organization.name],
  to: '/organizations/$orgId',
  params: { orgId: organization.id.toString() },
});

export const buildProductFavorite = (
  organization: Organization,
  product: Product
): FavoriteItemInput => ({
  id: buildProductFavoriteId(organization.id, product.id),
  type: 'product',
  name: product.name,
  breadcrumbs: [organization.name, product.name],
  to: '/organizations/$orgId/products/$productId',
  params: {
    orgId: organization.id.toString(),
    productId: product.id.toString(),
  },
});

/** Build a repository favorite using the repository name, falling back to its URL. */
export const buildRepositoryFavorite = (
  organization: Organization,
  product: Product,
  repository: Repository
): FavoriteItemInput => ({
  id: buildRepositoryFavoriteId(organization.id, product.id, repository.id),
  type: 'repository',
  name: getRepositoryName(repository),
  breadcrumbs: [organization.name, product.name, getRepositoryName(repository)],
  to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs',
  params: {
    orgId: organization.id.toString(),
    productId: product.id.toString(),
    repoId: repository.id.toString(),
  },
});

export const buildRunFavorite = (
  organization: Organization,
  product: Product,
  repository: Repository,
  run: Pick<OrtRun, 'id' | 'index'>
): FavoriteItemInput => ({
  id: buildRunFavoriteId(run.id),
  type: 'run',
  name: `Run ${run.index}`,
  breadcrumbs: [
    organization.name,
    product.name,
    getRepositoryName(repository),
    `Run ${run.index}`,
  ],
  to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex',
  params: {
    orgId: organization.id.toString(),
    productId: product.id.toString(),
    repoId: repository.id.toString(),
    runIndex: run.index.toString(),
  },
});

/** Build a recent run snapshot from the current organization, product, repository, and run. */
export const buildRecentRun = (
  organization: Organization,
  product: Product,
  repository: Repository,
  run: OrtRun
): RecentRunItemInput => ({
  id: buildRecentRunId(run.id),
  runId: run.id,
  runIndex: run.index,
  organizationId: organization.id,
  organizationName: organization.name,
  productId: product.id,
  productName: product.name,
  repositoryId: repository.id,
  repositoryName: getRepositoryName(repository),
  revision: run.revision,
  path: run.path ?? undefined,
  status: run.status,
  to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex',
  params: {
    orgId: organization.id.toString(),
    productId: product.id.toString(),
    repoId: repository.id.toString(),
    runIndex: run.index.toString(),
  },
  createdAt: run.createdAt,
});
