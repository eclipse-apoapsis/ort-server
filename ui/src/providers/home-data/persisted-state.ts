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

import { z } from 'zod';

import { zOrtRunStatus } from '@/api/zod.gen';
import {
  DEFAULT_FAVORITE_GROUP_ORDER,
  normalizeFavoriteGroupOrder,
} from './favorites';
import { buildRecentRunId } from './recent-runs';
import type { FavoriteItem, FavoriteType, RecentRunItem } from './types';

/*
 * Migration of the persisted home data stores.
 *
 * Zustand keeps persisted state only when the stored version matches the configured one,
 * unless a migration is given. Without one it drops the whole payload, which would lose
 * the recent runs and favorites of every user on each version bump. The shapes used before
 * the current version were never released, so the migrations here do not convert between
 * known shapes. They validate the payload against the current shape and keep everything
 * that still fits, which also covers a payload written by a newer deployment, because
 * zustand migrates on any version mismatch, in both directions.
 *
 * The schemas are deliberately permissive. They require only the fields that cannot be
 * reconstructed, backfill everything else, drop unusable optional values instead of the
 * whole entry, and pass unknown fields through so that a newer shape survives a downgrade.
 */

export type PersistedRecentRunsState = {
  recentRunsByUser: Record<string, RecentRunItem[]>;
};

export type PersistedFavoritesState = {
  favoritesByUser: Record<string, FavoriteItem[]>;
  favoriteGroupOrderByUser: Record<string, FavoriteType[]>;
};

/** Timestamp for entries stored before the timestamp fields existed. */
const FALLBACK_TIMESTAMP = new Date(0).toISOString();

const RUN_ROUTE =
  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex';

const optionalText = z.string().min(1).optional().catch(undefined);

const routeParamsSchema = z.record(z.string(), z.string());

const favoriteTypeSchema = z.custom<FavoriteType>((value) =>
  DEFAULT_FAVORITE_GROUP_ORDER.some((favoriteType) => favoriteType === value)
);

/**
 * A recent run is kept as long as it identifies a run within the hierarchy, because the
 * route and its parameters can be rebuilt from those identifiers.
 */
const recentRunItemSchema = z
  .looseObject({
    id: optionalText,
    runId: z.number(),
    runIndex: z.number(),
    organizationId: z.number(),
    organizationName: z.string().catch(''),
    productId: z.number(),
    productName: z.string().catch(''),
    repositoryId: z.number(),
    repositoryName: z.string().catch(''),
    revision: optionalText,
    path: optionalText,
    status: zOrtRunStatus.optional().catch(undefined),
    to: optionalText,
    params: routeParamsSchema.optional().catch(undefined),
    createdAt: optionalText,
    recordedAt: optionalText,
  })
  .transform((item): RecentRunItem => ({
    ...item,
    id: item.id ?? buildRecentRunId(item.runId),
    to: item.to ?? RUN_ROUTE,
    params: item.params ?? {
      orgId: item.organizationId.toString(),
      productId: item.productId.toString(),
      repoId: item.repositoryId.toString(),
      runIndex: item.runIndex.toString(),
    },
    recordedAt: item.recordedAt ?? item.createdAt ?? FALLBACK_TIMESTAMP,
  }));

/** A favorite is kept as long as it has an identity, a label and a link target. */
const favoriteItemSchema = z
  .looseObject({
    id: z.string().min(1),
    type: favoriteTypeSchema,
    name: z.string().min(1),
    breadcrumbs: z.array(z.string()).optional().catch(undefined),
    to: z.string().min(1),
    params: routeParamsSchema.optional().catch(undefined),
    starredAt: optionalText,
  })
  .transform((item): FavoriteItem => ({
    ...item,
    breadcrumbs: item.breadcrumbs ?? [item.name],
    starredAt: item.starredAt ?? FALLBACK_TIMESTAMP,
  }));

const itemsByUserSchema = <T extends z.ZodType>(itemSchema: T) =>
  z
    .record(
      z.string(),
      z
        .array(z.unknown())
        .catch([])
        .transform((items) =>
          items.flatMap((item) => {
            const parsedItem = itemSchema.safeParse(item);

            return parsedItem.success ? [parsedItem.data as z.output<T>] : [];
          })
        )
    )
    .catch({});

const favoriteGroupOrderByUserSchema = z
  .record(
    z.string(),
    z
      .array(z.unknown())
      .catch([])
      .transform((favoriteGroupOrder) =>
        normalizeFavoriteGroupOrder(
          favoriteGroupOrder.filter(
            (favoriteType) => favoriteTypeSchema.safeParse(favoriteType).success
          ) as FavoriteType[]
        )
      )
  )
  .catch({});

const persistedRecentRunsStateSchema = z.looseObject({
  recentRunsByUser: itemsByUserSchema(recentRunItemSchema),
});

const persistedFavoritesStateSchema = z.looseObject({
  favoritesByUser: itemsByUserSchema(favoriteItemSchema),
  favoriteGroupOrderByUser: favoriteGroupOrderByUserSchema,
});

/** Drop user entries that did not survive the migration. */
const withoutEmptyUsers = <T>(
  itemsByUser: Record<string, T[]>
): Record<string, T[]> =>
  Object.fromEntries(
    Object.entries(itemsByUser).filter(([, items]) => items.length > 0)
  );

/** Migrate a persisted recent runs payload of an unknown version to the current shape. */
export const migrateRecentRunsState = (
  persistedState: unknown
): PersistedRecentRunsState => {
  const state = persistedRecentRunsStateSchema.safeParse(persistedState);

  return {
    recentRunsByUser: withoutEmptyUsers(
      state.success ? state.data.recentRunsByUser : {}
    ),
  };
};

/** Migrate a persisted favorites payload of an unknown version to the current shape. */
export const migrateFavoritesState = (
  persistedState: unknown
): PersistedFavoritesState => {
  const state = persistedFavoritesStateSchema.safeParse(persistedState);

  return {
    favoritesByUser: withoutEmptyUsers(
      state.success ? state.data.favoritesByUser : {}
    ),
    favoriteGroupOrderByUser: state.success
      ? state.data.favoriteGroupOrderByUser
      : {},
  };
};
