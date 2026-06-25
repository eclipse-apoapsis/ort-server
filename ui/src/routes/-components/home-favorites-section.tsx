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

import { DragDropProvider } from '@dnd-kit/react';
import { isSortable, useSortable } from '@dnd-kit/react/sortable';
import { useQuery } from '@tanstack/react-query';
import { Link, type LinkProps } from '@tanstack/react-router';
import { AxiosError } from 'axios';
import {
  Building2,
  FolderGit2,
  GripVerticalIcon,
  Package,
  PlayCircle,
  Star,
} from 'lucide-react';
import { useEffect, useMemo, type ReactNode } from 'react';

import type { Organization, OrtRun, Product, Repository } from '@/api';
import {
  getOrganizationOptions,
  getProductOptions,
  getRepositoryOptions,
  getRepositoryRunOptions,
} from '@/api/@tanstack/react-query.gen';
import { FavoriteButton } from '@/components/favorite-button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { cn } from '@/lib/utils';
import {
  buildOrganizationFavorite,
  buildProductFavorite,
  buildRepositoryFavorite,
  buildRunFavorite,
  FavoriteItem,
  FavoriteItemInput,
  FavoriteRouteParams,
  FavoriteType,
  useHomeFavoriteActions,
  useHomeFavoriteGroupOrder,
} from '@/providers/home-data';
import { HomeEmptyState } from './home-empty-state';

const moveItem = <T,>(
  items: readonly T[],
  fromIndex: number,
  toIndex: number
) => {
  const nextItems = [...items];
  const [movedItem] = nextItems.splice(fromIndex, 1);

  if (movedItem !== undefined) {
    nextItems.splice(toIndex, 0, movedItem);
  }

  return nextItems;
};

const isNotFoundError = (error: unknown) =>
  error instanceof AxiosError &&
  (error.status ?? error.response?.status) === 404;

const favoriteRefreshQueryOptions = {
  // Favorite refreshes only keep persisted display data (names / breadcrumbs) up to date.
  // They are not foreground page data, so avoid eager refetches on every mount or focus.
  staleTime: 5 * 60 * 1000,
  refetchOnWindowFocus: false,
};

type FavoriteGroup = {
  type: FavoriteType;
  title: string;
  icon: typeof Building2;
  items: FavoriteItem[];
};

const favoriteTypeLabels: Record<FavoriteType, string> = {
  organization: 'Organizations',
  product: 'Products',
  repository: 'Repositories',
  run: 'Runs',
};

const favoriteTypeIcons: Record<FavoriteType, typeof Building2> = {
  organization: Building2,
  product: Package,
  repository: FolderGit2,
  run: PlayCircle,
};

type FavoriteRefreshData = {
  organization?: Organization;
  product?: Product;
  repository?: Repository;
  run?: Pick<OrtRun, 'id' | 'index'>;
};

type FavoriteRefreshBuilder = (
  data: FavoriteRefreshData
) => FavoriteItemInput | undefined;

const favoriteRefreshBuilders: Record<FavoriteType, FavoriteRefreshBuilder> = {
  organization: ({ organization }) =>
    organization ? buildOrganizationFavorite(organization) : undefined,
  product: ({ organization, product }) =>
    organization && product
      ? buildProductFavorite(organization, product)
      : undefined,
  repository: ({ organization, product, repository }) =>
    organization && product && repository
      ? buildRepositoryFavorite(organization, product, repository)
      : undefined,
  run: ({ organization, product, repository, run }) =>
    organization && product && repository && run
      ? buildRunFavorite(organization, product, repository, run)
      : undefined,
};

/** Return a numeric route parameter from a persisted favorite, if present. */
const getFavoriteRouteParam = (favorite: FavoriteItem, param: string) => {
  const value = favorite.params?.[param];
  const numberValue = value ? Number.parseInt(value) : Number.NaN;

  return Number.isNaN(numberValue) ? undefined : numberValue;
};

const areArraysEqual = (first: string[], second: string[]) =>
  first.length === second.length &&
  first.every((value, index) => value === second[index]);

const areRouteParamsEqual = (
  first?: FavoriteRouteParams,
  second?: FavoriteRouteParams
) => {
  const firstParams = first ?? {};
  const secondParams = second ?? {};
  const firstKeys = Object.keys(firstParams);
  const secondKeys = Object.keys(secondParams);

  return (
    firstKeys.length === secondKeys.length &&
    firstKeys.every((key) => firstParams[key] === secondParams[key])
  );
};

/** Return whether a stored favorite already matches the refreshed display data. */
const areFavoritesEqual = (
  favorite: FavoriteItem,
  refreshedFavorite: FavoriteItemInput
) =>
  favorite.id === refreshedFavorite.id &&
  favorite.type === refreshedFavorite.type &&
  favorite.name === refreshedFavorite.name &&
  favorite.to === refreshedFavorite.to &&
  areRouteParamsEqual(favorite.params, refreshedFavorite.params) &&
  areArraysEqual(favorite.breadcrumbs, refreshedFavorite.breadcrumbs);

/** Render a favorite and refresh its stored display data if backing data changed. */
const FavoriteListItem = ({ favorite }: { favorite: FavoriteItem }) => {
  const { updateFavorite, removeFavorite } = useHomeFavoriteActions();
  const orgId = getFavoriteRouteParam(favorite, 'orgId');
  const productId = getFavoriteRouteParam(favorite, 'productId');
  const repoId = getFavoriteRouteParam(favorite, 'repoId');
  const runIndex = getFavoriteRouteParam(favorite, 'runIndex');
  const needsOrganization = orgId !== undefined;
  const needsProduct =
    favorite.type !== 'organization' && productId !== undefined;
  const needsRepository =
    (favorite.type === 'repository' || favorite.type === 'run') &&
    repoId !== undefined;
  const needsRun =
    favorite.type === 'run' && repoId !== undefined && runIndex !== undefined;

  const organizationQuery = useQuery({
    ...getOrganizationOptions({ path: { organizationId: orgId ?? 0 } }),
    ...favoriteRefreshQueryOptions,
    enabled: needsOrganization,
    retry: (failureCount, error) => !isNotFoundError(error) && failureCount < 3,
  });

  const productQuery = useQuery({
    ...getProductOptions({ path: { productId: productId ?? 0 } }),
    ...favoriteRefreshQueryOptions,
    enabled: needsProduct,
    retry: (failureCount, error) => !isNotFoundError(error) && failureCount < 3,
  });

  const repositoryQuery = useQuery({
    ...getRepositoryOptions({ path: { repositoryId: repoId ?? 0 } }),
    ...favoriteRefreshQueryOptions,
    enabled: needsRepository,
    retry: (failureCount, error) => !isNotFoundError(error) && failureCount < 3,
  });

  const runQuery = useQuery({
    ...getRepositoryRunOptions({
      path: { repositoryId: repoId ?? 0, ortRunIndex: runIndex ?? 0 },
    }),
    ...favoriteRefreshQueryOptions,
    enabled: needsRun,
    retry: (failureCount, error) => !isNotFoundError(error) && failureCount < 3,
  });

  const refreshedFavorite = useMemo<FavoriteItemInput | undefined>(() => {
    const organization = organizationQuery.data;
    const product = productQuery.data;
    const repository = repositoryQuery.data;
    const run = runQuery.data;

    const refreshed = favoriteRefreshBuilders[favorite.type]({
      organization,
      product,
      repository,
      run,
    });

    return refreshed
      ? { ...refreshed, starredAt: favorite.starredAt }
      : undefined;
  }, [
    favorite.starredAt,
    favorite.type,
    organizationQuery.data,
    productQuery.data,
    repositoryQuery.data,
    runQuery.data,
  ]);

  useEffect(() => {
    if (
      isNotFoundError(organizationQuery.error) ||
      isNotFoundError(productQuery.error) ||
      isNotFoundError(repositoryQuery.error) ||
      isNotFoundError(runQuery.error)
    ) {
      removeFavorite(favorite.id);
    }
  }, [
    favorite.id,
    organizationQuery.error,
    productQuery.error,
    removeFavorite,
    repositoryQuery.error,
    runQuery.error,
  ]);

  useEffect(() => {
    if (refreshedFavorite && !areFavoritesEqual(favorite, refreshedFavorite)) {
      updateFavorite(refreshedFavorite);
    }
  }, [favorite, refreshedFavorite, updateFavorite]);

  return (
    <li className='flex items-center justify-between gap-3 rounded-lg border p-2'>
      <Link
        to={favorite.to as LinkProps['to']}
        params={favorite.params as LinkProps['params']}
        className='min-w-0 flex-1 text-sm break-words text-blue-400 hover:underline'
      >
        {favorite.breadcrumbs.join(' / ')}
      </Link>
      <FavoriteButton
        favorite={favorite}
        size='xs'
        variant='ghost'
        className='size-6 p-0'
      />
    </li>
  );
};

const FavoriteGroupCard = ({
  group,
  dragHandle,
}: {
  group: FavoriteGroup;
  dragHandle?: ReactNode;
}) => {
  const Icon = group.icon;

  return (
    <Card>
      <CardHeader>
        <CardTitle className='flex items-center gap-2 text-base'>
          {dragHandle}
          <Icon className='h-4 w-4' />
          {group.title}
        </CardTitle>
      </CardHeader>
      <CardContent>
        {group.items.length > 0 ? (
          <ul className='space-y-2'>
            {group.items.map((favorite) => (
              <FavoriteListItem key={favorite.id} favorite={favorite} />
            ))}
          </ul>
        ) : (
          <HomeEmptyState>
            No favorite {group.title.toLowerCase()} yet.
          </HomeEmptyState>
        )}
      </CardContent>
    </Card>
  );
};

const SortableFavoriteGroupCard = ({
  group,
  index,
}: {
  group: FavoriteGroup;
  index: number;
}) => {
  const { ref, handleRef, isDragging } = useSortable({
    id: group.type,
    index,
    type: 'favorite-group',
    accept: 'favorite-group',
  });

  return (
    <div ref={ref} className={cn(isDragging && 'opacity-60')}>
      <FavoriteGroupCard
        group={group}
        dragHandle={
          <button
            ref={handleRef}
            type='button'
            aria-label={`Reorder ${group.title}`}
            className='text-muted-foreground flex h-4 w-4 cursor-grab items-center justify-center rounded-sm hover:text-black focus-visible:ring-2 focus-visible:outline-none active:cursor-grabbing'
          >
            <GripVerticalIcon className='h-4 w-4' />
          </button>
        }
      />
    </div>
  );
};

/** Render favorite groups and allow users to persist their preferred group order. */
export const HomeFavoritesSection = ({
  favorites,
}: {
  favorites: FavoriteItem[];
}) => {
  const favoriteGroupOrder = useHomeFavoriteGroupOrder();
  const { setFavoriteGroupOrder } = useHomeFavoriteActions();
  const groups: FavoriteGroup[] = favoriteGroupOrder.map((type) => ({
    type,
    title: favoriteTypeLabels[type],
    icon: favoriteTypeIcons[type],
    items: [...favorites.filter((favorite) => favorite.type === type)].sort(
      (first, second) =>
        first.breadcrumbs
          .join(' / ')
          .localeCompare(second.breadcrumbs.join(' / '), undefined, {
            sensitivity: 'base',
          })
    ),
  }));

  return (
    <Card>
      <CardHeader>
        <CardTitle className='flex items-center gap-2'>
          <Star className='fill-foreground text-foreground h-5 w-5 dark:fill-white dark:text-white' />
          Favorites
        </CardTitle>
        <CardDescription>
          Star organizations, products, repositories, and runs to keep them on
          your home page.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {favorites.length > 0 ? (
          <DragDropProvider
            onDragEnd={(event) => {
              if (event.canceled) return;

              const { source } = event.operation;

              if (isSortable(source)) {
                const { initialIndex, index } = source;

                if (initialIndex !== index) {
                  setFavoriteGroupOrder(
                    moveItem(
                      groups.map((group) => group.type),
                      initialIndex,
                      index
                    )
                  );
                }
              }
            }}
          >
            <div className='flex flex-col gap-4'>
              {groups.map((group, index) => (
                <SortableFavoriteGroupCard
                  key={group.type}
                  group={group}
                  index={index}
                />
              ))}
            </div>
          </DragDropProvider>
        ) : (
          <HomeEmptyState>
            Use the star button on organization, product, repository, or run
            pages to add favorites here.
          </HomeEmptyState>
        )}
      </CardContent>
    </Card>
  );
};
