/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import {
  useQuery,
  type QueryFunction,
  type QueryKey,
  type SkipToken,
} from '@tanstack/react-query';
import { Link, useParams, useRouter } from '@tanstack/react-router';
import { Check, ChevronDown, ExternalLink } from 'lucide-react';
import { useState } from 'react';

import {
  getOrganizationProductsInfiniteOptions,
  getOrganizationProductsOptions,
  getOrganizationsInfiniteOptions,
  getOrganizationsOptions,
  getProductRepositoriesInfiniteOptions,
  getProductRepositoriesOptions,
  getRepositoryOptions,
  getRepositoryRunsInfiniteOptions,
  getRepositoryRunsOptions,
} from '@/api/@tanstack/react-query.gen';
import { SearchableInfiniteList } from '@/components/searchable-infinite-list';
import { BreadcrumbItem, BreadcrumbLink } from '@/components/ui/breadcrumb';
import { Button } from '@/components/ui/button';
import { CommandItem } from '@/components/ui/command';
import { useDebounce } from '@/hooks/use-debounce';
import { useInfiniteList } from '@/hooks/use-infinite-list';
import { DROPDOWN_PAGE_SIZE } from '@/lib/constants';
import { PagedResponse } from '@/lib/infinite-list';
import { toSearchFilter } from '@/lib/regex';

type SiblingsProps = {
  entity: 'organization' | 'product' | 'repository' | 'run';
  pathName?: string;
};

// To improve the performance of the siblings component, set a stale time of
// 2 hours for all queries. This is because the entities (organizations, products,
// repositories, runs) are not expected to change frequently, and when changes do
// occur, cache invalidation will ensure that the latest data is fetched.
const staleTime = 2 * 60 * 60 * 1000;

/**
 * Ask a paged endpoint of the API how many entries it has, by reading a single one of them.
 *
 * This is what tells a breadcrumb whether its dropdown is worth showing at all, without loading the
 * list itself. Pass the generated `*Options` for the endpoint, with a `limit` of 1 and neither a
 * `filter` nor a `sort`, so that the count is cached on its own and searching the list that the
 * dropdown shows leaves it alone.
 */
function useTotalCount<
  TData extends PagedResponse<unknown>,
  TQueryKey extends QueryKey,
>(
  {
    queryKey,
    queryFn,
  }: {
    queryKey: TQueryKey;
    queryFn?: QueryFunction<TData, TQueryKey> | SkipToken;
  },
  enabled: boolean
) {
  const { data } = useQuery({
    queryKey,
    queryFn,
    enabled,
    staleTime: staleTime,
    select: (page) => page.pagination.totalCount,
  });

  return data ?? 0;
}

export const Siblings = ({ entity, pathName }: SiblingsProps) => {
  const router = useRouter();
  const breadcrumbs = router.options.context.breadcrumbs;
  const params = useParams({ strict: false });

  // Only one of the dropdowns below is rendered, the one that belongs to this breadcrumb, so a
  // single open state and a single search term are enough for all of them.
  const [isOpen, setIsOpen] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const filter = toSearchFilter(useDebounce(searchTerm, 300));

  // The links in the dropdowns below turn preloading off. The router preloads on hover, and the
  // loader of a route writes the name of the entity it loads into the breadcrumbs, so merely
  // scrolling past the entries would replace the name shown in this breadcrumb, besides fetching
  // an entity and its permissions for every entry the pointer happens to cross.
  const closeDropdown = () => setIsOpen(false);

  const handleOpenChange = (open: boolean) => {
    setIsOpen(open);
    // Start from the whole list again the next time the dropdown is opened.
    if (!open) setSearchTerm('');
  };

  // A dropdown is only shown when there is more than one entry to pick from, which the counts below
  // tell for the price of a single entry each. Queries that depend on a path parameter (like orgId)
  // do not run until that parameter is actually present.
  const organizationCount = useTotalCount(
    getOrganizationsOptions({ query: { limit: 1 } }),
    entity === 'organization'
  );

  const productCount = useTotalCount(
    getOrganizationProductsOptions({
      path: { organizationId: Number(params.orgId) },
      query: { limit: 1 },
    }),
    entity === 'product' && !!params.orgId
  );

  const repositoryCount = useTotalCount(
    getProductRepositoriesOptions({
      path: { productId: Number(params.productId) },
      query: { limit: 1 },
    }),
    entity === 'repository' && !!params.productId
  );

  const runCount = useTotalCount(
    getRepositoryRunsOptions({
      path: { repositoryId: Number(params.repoId) },
      query: { limit: 1 },
    }),
    entity === 'run' && !!params.repoId
  );

  // The lists themselves are fetched only while their dropdown is open, so a breadcrumb that is
  // never clicked costs no more than the count above.
  const organizations = useInfiniteList(
    getOrganizationsInfiniteOptions({
      query: { limit: DROPDOWN_PAGE_SIZE, filter: filter },
    }),
    { staleTime: staleTime, enabled: entity === 'organization' && isOpen }
  );

  const products = useInfiniteList(
    getOrganizationProductsInfiniteOptions({
      path: { organizationId: Number(params.orgId) },
      query: { limit: DROPDOWN_PAGE_SIZE, filter: filter },
    }),
    {
      staleTime: staleTime,
      enabled: entity === 'product' && isOpen && !!params.orgId,
    }
  );

  const repositories = useInfiniteList(
    getProductRepositoriesInfiniteOptions({
      path: { productId: Number(params.productId) },
      query: { limit: DROPDOWN_PAGE_SIZE, filter: filter },
    }),
    {
      staleTime: staleTime,
      enabled: entity === 'repository' && isOpen && !!params.productId,
    }
  );

  const runs = useInfiniteList(
    getRepositoryRunsInfiniteOptions({
      path: { repositoryId: Number(params.repoId) },
      query: { limit: DROPDOWN_PAGE_SIZE, sort: '-index' },
    }),
    {
      staleTime: staleTime,
      enabled: entity === 'run' && isOpen && !!params.repoId,
    }
  );

  // The repository of the page itself, which the route loader has already put into the cache.
  const { data: currentRepo } = useQuery({
    ...getRepositoryOptions({
      path: { repositoryId: Number(params.repoId) },
    }),
    staleTime: staleTime,
    enabled: entity === 'repository' && !!params.repoId,
  });

  const name =
    entity === 'organization'
      ? breadcrumbs.organization
      : entity === 'product'
        ? breadcrumbs.product
        : entity === 'repository'
          ? breadcrumbs.repo
          : breadcrumbs.run;

  // The query above is cached under the same key for the run breadcrumb, which also knows the
  // repository, so the link is shown for the repository breadcrumb only.
  const currentRepoUrl = entity === 'repository' ? currentRepo?.url : undefined;

  return (
    <BreadcrumbItem>
      {entity === 'organization' && organizationCount > 1 && (
        <SearchableInfiniteList
          trigger={<ChevronDown className='ml-1 size-4 cursor-pointer' />}
          open={isOpen}
          onOpenChange={handleOpenChange}
          list={organizations}
          getItemKey={(org) => org.id}
          renderItem={(org) => (
            <CommandItem asChild value={org.name} onSelect={closeDropdown}>
              <Link
                to='/organizations/$orgId'
                params={{ orgId: org.id.toString() ?? '' }}
                preload={false}
              >
                <div className='grid w-full grid-cols-6 items-center gap-2'>
                  <div className='col-span-5'>{org.name}</div>
                  {org.id === Number(params.orgId) && (
                    <Check className='ml-auto h-4 w-4' />
                  )}
                </div>
              </Link>
            </CommandItem>
          )}
          searchPlaceholder='Search organizations...'
          searchTerm={searchTerm}
          onSearchTermChange={setSearchTerm}
          emptyMessage='No organizations found.'
          errorMessage='Failed to load organizations'
        />
      )}
      {entity === 'product' && productCount > 1 && (
        <SearchableInfiniteList
          trigger={<ChevronDown className='ml-1 size-4 cursor-pointer' />}
          open={isOpen}
          onOpenChange={handleOpenChange}
          list={products}
          getItemKey={(prod) => prod.id}
          renderItem={(prod) => (
            <CommandItem asChild value={prod.name} onSelect={closeDropdown}>
              <Link
                to='/organizations/$orgId/products/$productId'
                params={{
                  orgId: params.orgId ?? '',
                  productId: prod.id.toString() ?? '',
                }}
                preload={false}
              >
                <div className='grid w-full grid-cols-6 items-center gap-2'>
                  <div className='col-span-5'>{prod.name}</div>
                  {prod.id === Number(params.productId) && (
                    <Check className='ml-auto h-4 w-4' />
                  )}
                </div>
              </Link>
            </CommandItem>
          )}
          searchPlaceholder='Search products...'
          searchTerm={searchTerm}
          onSearchTermChange={setSearchTerm}
          emptyMessage='No products found.'
          errorMessage='Failed to load products'
        />
      )}
      {entity === 'repository' && repositoryCount > 1 && (
        <SearchableInfiniteList
          trigger={<ChevronDown className='ml-1 size-4 cursor-pointer' />}
          open={isOpen}
          onOpenChange={handleOpenChange}
          list={repositories}
          getItemKey={(repo) => repo.id}
          renderItem={(repo) => (
            <CommandItem
              asChild
              value={repo.name || repo.url}
              onSelect={closeDropdown}
            >
              <Link
                to='/organizations/$orgId/products/$productId/repositories/$repoId'
                params={{
                  orgId: params.orgId ?? '',
                  productId: params.productId ?? '',
                  repoId: repo.id.toString() ?? '',
                }}
                preload={false}
              >
                <div className='grid w-full grid-cols-6 items-center gap-2'>
                  <div className='col-span-5'>{repo.name || repo.url}</div>
                  {repo.id === Number(params.repoId) && (
                    <Check className='ml-auto h-4 w-4' />
                  )}
                </div>
              </Link>
            </CommandItem>
          )}
          searchPlaceholder='Search repositories...'
          searchTerm={searchTerm}
          onSearchTermChange={setSearchTerm}
          emptyMessage='No repositories found.'
          errorMessage='Failed to load repositories'
        />
      )}
      {/* The runs of a repository cannot be filtered on the server, so this list has no search. */}
      {entity === 'run' && runCount > 1 && (
        <SearchableInfiniteList
          trigger={<ChevronDown className='ml-1 size-4 cursor-pointer' />}
          open={isOpen}
          onOpenChange={handleOpenChange}
          list={runs}
          getItemKey={(run) => run.index}
          renderItem={(run) => (
            <CommandItem
              asChild
              value={run.index.toString()}
              onSelect={closeDropdown}
            >
              <Link
                to='/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex'
                params={{
                  orgId: params.orgId ?? '',
                  productId: params.productId ?? '',
                  repoId: params.repoId ?? '',
                  runIndex: run.index.toString() ?? '',
                }}
                preload={false}
              >
                <div className='grid w-full grid-cols-6 items-center gap-2'>
                  <div className='col-span-5'>{run.index}</div>
                  {run.index === Number(params.runIndex) && (
                    <Check className='ml-auto h-4 w-4' />
                  )}
                </div>
              </Link>
            </CommandItem>
          )}
          searchable={false}
          emptyMessage='No runs found.'
          errorMessage='Failed to load runs'
        />
      )}
      <BreadcrumbLink asChild>
        <Link to={pathName}>{name}</Link>
      </BreadcrumbLink>
      {currentRepoUrl && (
        <Button variant='ghost' size='icon' className='h-4 w-4' asChild>
          <Link to={currentRepoUrl} target='_blank' rel='noopener noreferrer'>
            <ExternalLink className='h-3 w-3 text-blue-400' />
          </Link>
        </Button>
      )}
    </BreadcrumbItem>
  );
};
