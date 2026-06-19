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

import { useQuery } from '@tanstack/react-query';
import { Star } from 'lucide-react';
import { ComponentProps } from 'react';

import type { Organization, OrtRun, Product, Repository } from '@/api';
import {
  getOrganizationOptions,
  getProductOptions,
  getRepositoryOptions,
} from '@/api/@tanstack/react-query.gen';
import { Button } from '@/components/ui/button';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { cn } from '@/lib/utils';
import {
  buildOrganizationFavorite,
  buildProductFavorite,
  buildRepositoryFavorite,
  buildRunFavorite,
  FavoriteItemInput,
  useHomeFavoriteActions,
  useIsHomeDataEnabled,
  useIsHomeFavorite,
} from '@/providers/home-data';

type FavoriteButtonProps = Omit<
  ComponentProps<typeof Button>,
  'aria-label' | 'aria-pressed' | 'onClick' | 'type'
> & {
  favorite: FavoriteItemInput;
};

type EntityFavoriteButtonProps = Omit<FavoriteButtonProps, 'favorite'>;

type FavoriteEntityId = number | string;

const toNumber = (id: FavoriteEntityId) =>
  typeof id === 'number' ? id : Number.parseInt(id);

export const FavoriteButton = ({
  favorite,
  className,
  disabled,
  size = 'icon',
  variant = 'outline',
  ...props
}: FavoriteButtonProps) => {
  const isFavorite = useIsHomeFavorite(favorite.id);
  const isEnabled = useIsHomeDataEnabled();
  const { toggleFavorite } = useHomeFavoriteActions();
  // Disable the button when favorites cannot be persisted (e.g. no user is
  // known yet), so a click is never silently dropped.
  const isDisabled = disabled || !isEnabled;
  const label = isFavorite ? 'Remove from favorites' : 'Add to favorites';
  const tooltip = isEnabled ? label : 'Sign in to manage favorites';

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Button
          aria-label={`${label}: ${favorite.name}`}
          aria-pressed={isFavorite}
          className={className}
          disabled={isDisabled}
          onClick={() => toggleFavorite(favorite)}
          size={size}
          type='button'
          variant={variant}
          {...props}
        >
          <Star
            className={cn(
              'h-4 w-4 text-white/50',
              isFavorite && 'fill-white text-white'
            )}
          />
        </Button>
      </TooltipTrigger>
      <TooltipContent>{tooltip}</TooltipContent>
    </Tooltip>
  );
};

export const OrganizationFavoriteButton = ({
  organization,
  ...props
}: EntityFavoriteButtonProps & { organization: Organization }) => (
  <FavoriteButton
    favorite={buildOrganizationFavorite(organization)}
    {...props}
  />
);

export const ProductFavoriteButton = ({
  organization,
  organizationId,
  product,
  ...props
}: EntityFavoriteButtonProps & {
  organization?: Organization;
  organizationId: FavoriteEntityId;
  product: Product;
}) => {
  const { data: loadedOrganization } = useQuery({
    ...getOrganizationOptions({
      path: { organizationId: toNumber(organizationId) },
    }),
    enabled: organization === undefined,
  });
  const favoriteOrganization = organization ?? loadedOrganization;

  return favoriteOrganization ? (
    <FavoriteButton
      favorite={buildProductFavorite(favoriteOrganization, product)}
      {...props}
    />
  ) : null;
};

export const RepositoryFavoriteButton = ({
  organization,
  organizationId,
  product,
  productId,
  repository,
  ...props
}: EntityFavoriteButtonProps & {
  organization?: Organization;
  organizationId: FavoriteEntityId;
  product?: Product;
  productId: FavoriteEntityId;
  repository: Repository;
}) => {
  const { data: loadedOrganization } = useQuery({
    ...getOrganizationOptions({
      path: { organizationId: toNumber(organizationId) },
    }),
    enabled: organization === undefined,
  });
  const { data: loadedProduct } = useQuery({
    ...getProductOptions({ path: { productId: toNumber(productId) } }),
    enabled: product === undefined,
  });
  const favoriteOrganization = organization ?? loadedOrganization;
  const favoriteProduct = product ?? loadedProduct;

  return favoriteOrganization && favoriteProduct ? (
    <FavoriteButton
      favorite={buildRepositoryFavorite(
        favoriteOrganization,
        favoriteProduct,
        repository
      )}
      {...props}
    />
  ) : null;
};

export const RunFavoriteButton = ({
  organization,
  organizationId,
  product,
  productId,
  repository,
  repositoryId,
  run,
  ...props
}: EntityFavoriteButtonProps & {
  organization?: Organization;
  organizationId: FavoriteEntityId;
  product?: Product;
  productId: FavoriteEntityId;
  repository?: Repository;
  repositoryId: FavoriteEntityId;
  run: Pick<OrtRun, 'id' | 'index'>;
}) => {
  const { data: loadedOrganization } = useQuery({
    ...getOrganizationOptions({
      path: { organizationId: toNumber(organizationId) },
    }),
    enabled: organization === undefined,
  });
  const { data: loadedProduct } = useQuery({
    ...getProductOptions({ path: { productId: toNumber(productId) } }),
    enabled: product === undefined,
  });
  const { data: loadedRepository } = useQuery({
    ...getRepositoryOptions({ path: { repositoryId: toNumber(repositoryId) } }),
    enabled: repository === undefined,
  });
  const favoriteOrganization = organization ?? loadedOrganization;
  const favoriteProduct = product ?? loadedProduct;
  const favoriteRepository = repository ?? loadedRepository;

  return favoriteOrganization && favoriteProduct && favoriteRepository ? (
    <FavoriteButton
      favorite={buildRunFavorite(
        favoriteOrganization,
        favoriteProduct,
        favoriteRepository,
        run
      )}
      {...props}
    />
  ) : null;
};
