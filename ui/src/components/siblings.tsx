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

import type { OrtRunSummary } from '@/api';
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
import { Sha1Component } from '@/components/sha1-component';
import { TooltipIfTruncated } from '@/components/tooltip-if-truncated';
import {
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbPage,
} from '@/components/ui/breadcrumb';
import { Button } from '@/components/ui/button';
import { buttonVariants } from '@/components/ui/button-variants';
import { CommandItem } from '@/components/ui/command';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import {
  getStatusBackgroundColor,
  getStatusClass,
} from '@/helpers/get-status-class';
import { useDebounce } from '@/hooks/use-debounce';
import { useInfiniteList } from '@/hooks/use-infinite-list';
import { DROPDOWN_PAGE_SIZE } from '@/lib/constants';
import { PagedResponse } from '@/lib/infinite-list';
import { toSearchFilter } from '@/lib/regex';
import { cn } from '@/lib/utils';

type SiblingsProps = {
  entity: 'organization' | 'product' | 'repository' | 'run';
  pathName?: string;
};

// To improve the performance of the siblings component, set a stale time of
// 2 hours for all queries but the list of runs. This is because the entities
// (organizations, products, repositories, runs) are not expected to change
// frequently. What keeps a stale time this long honest is `@/lib/entity-cache`,
// which every mutation of these entities calls to invalidate the lists shown here,
// the counts below included. The list of runs is the exception, as its entries show
// a status and a resolved revision that the server changes while a run is in
// progress, without any mutation in the UI to invalidate them.
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

/**
 * The tick that marks the entry of the page itself. The other entries keep its space, so that the
 * text of all entries ends at the same place.
 */
const CurrentMark = ({ current }: { current: boolean }) => (
  <Check className={cn('ml-auto size-4', !current && 'invisible')} />
);

/** An organization, product or repository in a dropdown, with its description below its name. */
const EntityEntry = ({
  name,
  description,
  current,
}: {
  name: string;
  description?: string | null;
  current: boolean;
}) => (
  <div className='flex w-full min-w-0 items-center gap-3'>
    <div className='flex min-w-0 flex-col'>
      <span className='truncate'>{name}</span>
      {description && (
        <span className='text-muted-foreground truncate text-xs'>
          {description}
        </span>
      )}
    </div>
    <CurrentMark current={current} />
  </div>
);

const isFullSha1 = (revision: string) => /^[0-9a-f]{40}$/i.test(revision);

/**
 * A run in a dropdown: its status and index, next to its time of creation above its revision.
 */
const RunEntry = ({
  run,
  current,
}: {
  run: OrtRunSummary;
  current: boolean;
}) => {
  // A revision that is a commit already needs no resolved commit next to it, and is shortened like
  // one.
  const sha1 =
    run.resolvedRevision && run.resolvedRevision !== run.revision
      ? run.resolvedRevision
      : undefined;
  const revision = isFullSha1(run.revision)
    ? run.revision.slice(0, 7)
    : run.revision;

  return (
    <div className='grid w-full min-w-0 grid-cols-[auto_2rem_minmax(0,1fr)_auto] items-center gap-x-3 tabular-nums'>
      <Tooltip>
        <TooltipTrigger asChild>
          <span
            className={cn(
              getStatusClass(run.status),
              getStatusBackgroundColor(run.status),
              'row-span-2 size-2.5'
            )}
          >
            <span className='sr-only'>{run.status}</span>
          </span>
        </TooltipTrigger>
        <TooltipContent>{run.status}</TooltipContent>
      </Tooltip>
      <span className='row-span-2'>{run.index}</span>
      <span className='text-muted-foreground text-xs'>
        {new Date(run.createdAt).toLocaleString(undefined, {
          year: 'numeric',
          month: '2-digit',
          day: '2-digit',
          hour: '2-digit',
          minute: '2-digit',
        })}
      </span>
      <div className='row-span-2'>
        <CurrentMark current={current} />
      </div>
      <div className='col-start-3 flex min-w-0 items-baseline gap-1 text-xs'>
        <TooltipIfTruncated
          text={revision}
          className={cn('min-w-0', isFullSha1(run.revision) && 'font-mono')}
        />
        {sha1 && (
          <Sha1Component
            sha1={sha1}
            copyable={false}
            withTooltip={false}
            className='text-muted-foreground shrink-0 text-[0.6875rem]'
          />
        )}
      </div>
    </div>
  );
};

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
      staleTime: 0,
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
    <BreadcrumbItem className='min-w-0 gap-0'>
      {currentRepoUrl && (
        <Button
          variant='ghost'
          className='size-6'
          aria-label='Open the repository in a new tab'
          asChild
        >
          <Link to={currentRepoUrl} target='_blank' rel='noopener noreferrer'>
            <ExternalLink className='mx-0.5 h-3 w-3 text-blue-400' />
          </Link>
        </Button>
      )}
      {/* The page of a run is the deepest one the breadcrumbs lead to, so its breadcrumb is not a
          link: it only opens the other runs of the repository, or shows the index if there are none. */}
      {entity !== 'run' && (
        <BreadcrumbLink
          asChild
          className={cn(
            buttonVariants({ variant: 'ghost' }),
            // Let a long name be cut short where the header has no room for it.
            'h-6 min-w-0 shrink pr-0.5 pl-2 font-normal'
          )}
        >
          <Link to={pathName} title={name}>
            <span className='truncate'>{name}</span>
          </Link>
        </BreadcrumbLink>
      )}
      {entity === 'run' && runCount <= 1 && (
        <BreadcrumbPage className='px-2'>{name}</BreadcrumbPage>
      )}
      {entity === 'organization' && organizationCount > 1 && (
        <SearchableInfiniteList
          trigger={
            <Button
              variant='ghost'
              className='size-6'
              aria-label='Show the other organizations'
            >
              <ChevronDown className='mx-1 size-4 cursor-pointer' />
            </Button>
          }
          open={isOpen}
          onOpenChange={handleOpenChange}
          list={organizations}
          getItemKey={(org) => org.id}
          renderItem={(org) => (
            <CommandItem asChild value={org.name} onSelect={closeDropdown}>
              <Link
                to='/organizations/$orgId'
                params={{ orgId: org.id.toString() }}
                preload={false}
              >
                <EntityEntry
                  name={org.name}
                  description={org.description}
                  current={org.id === Number(params.orgId)}
                />
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
          trigger={
            <Button
              variant='ghost'
              className='size-6'
              aria-label='Show the other products'
            >
              <ChevronDown className='mx-1 size-4 cursor-pointer' />
            </Button>
          }
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
                  productId: prod.id.toString(),
                }}
                preload={false}
              >
                <EntityEntry
                  name={prod.name}
                  description={prod.description}
                  current={prod.id === Number(params.productId)}
                />
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
          trigger={
            <Button
              variant='ghost'
              className='size-6'
              aria-label='Show the other repositories'
            >
              <ChevronDown className='mx-1 size-4 cursor-pointer' />
            </Button>
          }
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
                  repoId: repo.id.toString(),
                }}
                preload={false}
              >
                <EntityEntry
                  name={repo.name || repo.url}
                  description={repo.description}
                  current={repo.id === Number(params.repoId)}
                />
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
          trigger={
            <Button
              variant='ghost'
              className='h-6 gap-1 px-2 font-normal has-[>svg]:px-2'
            >
              {name}
              <ChevronDown className='size-4' />
            </Button>
          }
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
                  runIndex: run.index.toString(),
                }}
                preload={false}
              >
                <RunEntry
                  run={run}
                  current={run.index === Number(params.runIndex)}
                />
              </Link>
            </CommandItem>
          )}
          searchable={false}
          // A fixed width, so that a long revision is cut short instead of widening the list.
          className='w-80'
          emptyMessage='No runs found.'
          errorMessage='Failed to load runs'
        />
      )}
    </BreadcrumbItem>
  );
};
