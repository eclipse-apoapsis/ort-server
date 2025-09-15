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

import { useQuery } from '@tanstack/react-query';
import { Link, useParams, useRouter } from '@tanstack/react-router';
import { Check, ChevronDown } from 'lucide-react';

import { ApiError } from '@/api/requests';
import { BreadcrumbItem, BreadcrumbLink } from '@/components/ui/breadcrumb';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {
  getOrganizationProductsOptions,
  getOrganizationsOptions,
  getOrtRunsByRepositoryIdOptions,
  getRepositoriesByProductIdOptions,
} from '@/hey-api/@tanstack/react-query.gen';
import { ALL_ITEMS } from '@/lib/constants';

type SiblingsProps = {
  entity: 'organization' | 'product' | 'repository' | 'run';
  pathName?: string;
};

export const Siblings = ({ entity, pathName }: SiblingsProps) => {
  const router = useRouter();
  const breadcrumbs = router.options.context.breadcrumbs;
  const params = useParams({ strict: false });

  // To improve the performance of the siblings component, set a stale time of
  // 2 hours for all queries. This is because the entities (organizations, products,
  // repositories, runs) are not expected to change frequently, and when changes do
  // occur, cache invalidation will ensure that the latest data is fetched.
  const staleTime = 2 * 60 * 60 * 1000;

  // By using the `enabled` option in the query hooks, unnecessary API calls can be prevented.
  // Now, only the single query relevant to the clicked dropdown will be executed. Also ensure
  // that queries that depend on a path parameter (like orgId) do not run until that parameter
  // is actually present.
  const {
    data: organizations,
    isPending: isOrgPending,
    isError: isOrgError,
    error: orgError,
  } = useQuery({
    ...getOrganizationsOptions({ query: { limit: ALL_ITEMS } }),
    staleTime: staleTime,
    enabled: entity === 'organization',
  });

  const {
    data: products,
    isPending: isProductsPending,
    isError: isProductsError,
    error: productsError,
  } = useQuery({
    ...getOrganizationProductsOptions({
      path: { organizationId: Number(params.orgId) ?? '' },
      query: { limit: ALL_ITEMS },
    }),
    staleTime: staleTime,
    enabled: entity === 'product' || !!params.orgId,
  });

  const {
    data: repositories,
    isPending: isRepositoriesPending,
    isError: isRepositoriesError,
    error: repositoriesError,
  } = useQuery({
    ...getRepositoriesByProductIdOptions({
      path: { productId: Number(params.productId) ?? '' },
      query: { limit: ALL_ITEMS },
    }),
    staleTime: staleTime,
    enabled: entity === 'repository' || !!params.productId,
  });

  const {
    data: runs,
    isPending: isRunsPending,
    isError: isRunsError,
    error: runsError,
  } = useQuery({
    ...getOrtRunsByRepositoryIdOptions({
      path: { repositoryId: Number(params.repoId) ?? '' },
      query: { limit: ALL_ITEMS, sort: '-index' },
    }),
    staleTime: staleTime,
    enabled: entity === 'run' || !!params.repoId,
  });

  const name =
    entity === 'organization'
      ? breadcrumbs.organization
      : entity === 'product'
        ? breadcrumbs.product
        : entity === 'repository'
          ? breadcrumbs.repo
          : breadcrumbs.run;

  const orgs = organizations?.data;
  const prods = products?.data;
  const repos = repositories?.data;
  const runIndexes = runs?.data;

  return (
    <BreadcrumbItem>
      {entity === 'organization' && orgs && orgs.length > 1 && (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <ChevronDown className='ml-1 size-4 cursor-pointer' />
          </DropdownMenuTrigger>
          <DropdownMenuContent className='max-h-96' align='start'>
            <>
              {isOrgPending && <DropdownMenuItem>Loading...</DropdownMenuItem>}
              {isOrgError && (
                <DropdownMenuItem className='text-red-500'>
                  Error:{' '}
                  {`Failed to load organizations: ${(orgError as ApiError).message}`}
                </DropdownMenuItem>
              )}
              {orgs.map((org) => (
                <DropdownMenuItem key={org.id} className='ml-2' asChild>
                  <Link
                    to='/organizations/$orgId'
                    params={{ orgId: org.id.toString() ?? '' }}
                  >
                    <div className='grid w-full grid-cols-6 items-center gap-2'>
                      <div className='col-span-5'>{org.name}</div>
                      {org.id === Number(params.orgId) && (
                        <Check className='ml-auto h-4 w-4' />
                      )}
                    </div>
                  </Link>
                </DropdownMenuItem>
              ))}
            </>
          </DropdownMenuContent>
        </DropdownMenu>
      )}
      {entity === 'product' && prods && prods.length > 1 && (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <ChevronDown className='ml-1 size-4 cursor-pointer' />
          </DropdownMenuTrigger>
          <DropdownMenuContent className='max-h-96' align='start'>
            <>
              {isProductsPending && (
                <DropdownMenuItem>Loading...</DropdownMenuItem>
              )}
              {isProductsError && (
                <DropdownMenuItem className='text-red-500'>
                  Error:{' '}
                  {`Failed to load products: ${(productsError as ApiError).message}`}
                </DropdownMenuItem>
              )}
              {prods.map((prod) => (
                <DropdownMenuItem key={prod.id} className='ml-2' asChild>
                  <Link
                    to='/organizations/$orgId/products/$productId'
                    params={{
                      orgId: params.orgId ?? '',
                      productId: prod.id.toString() ?? '',
                    }}
                  >
                    <div className='grid w-full grid-cols-6 items-center gap-2'>
                      <div className='col-span-5'>{prod.name}</div>
                      {prod.id === Number(params.productId) && (
                        <Check className='ml-auto h-4 w-4' />
                      )}
                    </div>
                  </Link>
                </DropdownMenuItem>
              ))}
            </>
          </DropdownMenuContent>
        </DropdownMenu>
      )}
      {entity === 'repository' && repos && repos.length > 1 && (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <ChevronDown className='ml-1 size-4 cursor-pointer' />
          </DropdownMenuTrigger>
          <DropdownMenuContent className='max-h-96' align='start'>
            <>
              {isRepositoriesPending && (
                <DropdownMenuItem>Loading...</DropdownMenuItem>
              )}
              {isRepositoriesError && (
                <DropdownMenuItem className='text-red-500'>
                  Error:{' '}
                  {`Failed to load repositories: ${(repositoriesError as ApiError).message}`}
                </DropdownMenuItem>
              )}
              {repos.map((repo) => (
                <DropdownMenuItem key={repo.id} className='ml-2' asChild>
                  <Link
                    to='/organizations/$orgId/products/$productId/repositories/$repoId'
                    params={{
                      orgId: params.orgId ?? '',
                      productId: params.productId ?? '',
                      repoId: repo.id.toString() ?? '',
                    }}
                  >
                    <div className='grid w-full grid-cols-6 items-center gap-2'>
                      <div className='col-span-5'>{repo.url}</div>
                      {repo.id === Number(params.repoId) && (
                        <Check className='ml-auto h-4 w-4' />
                      )}
                    </div>
                  </Link>
                </DropdownMenuItem>
              ))}
            </>
          </DropdownMenuContent>
        </DropdownMenu>
      )}
      {entity === 'run' && runIndexes && runIndexes.length > 1 && (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <ChevronDown className='ml-1 size-4 cursor-pointer' />
          </DropdownMenuTrigger>
          <DropdownMenuContent className='max-h-96' align='start'>
            <>
              {isRunsPending && <DropdownMenuItem>Loading...</DropdownMenuItem>}
              {isRunsError && (
                <DropdownMenuItem className='text-red-500'>
                  Error:{' '}
                  {`Failed to load runs: ${(runsError as ApiError).message}`}
                </DropdownMenuItem>
              )}
              {runIndexes.map((run) => (
                <DropdownMenuItem key={run.index} className='ml-2' asChild>
                  <Link
                    to='/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex'
                    params={{
                      orgId: params.orgId ?? '',
                      productId: params.productId ?? '',
                      repoId: params.repoId ?? '',
                      runIndex: run.index.toString() ?? '',
                    }}
                  >
                    <div className='grid w-full grid-cols-6 items-center gap-2'>
                      <div className='col-span-5'>{run.index}</div>
                      {run.index === Number(params.runIndex) && (
                        <Check className='ml-auto h-4 w-4' />
                      )}
                    </div>
                  </Link>
                </DropdownMenuItem>
              ))}
            </>
          </DropdownMenuContent>
        </DropdownMenu>
      )}
      <BreadcrumbLink asChild>
        <Link to={pathName}>{name}</Link>
      </BreadcrumbLink>
    </BreadcrumbItem>
  );
};
