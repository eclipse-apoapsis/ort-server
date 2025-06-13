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

import { Link, useParams, useRouter } from '@tanstack/react-router';
import { ChevronDown } from 'lucide-react';

import {
  useOrganizationsServiceGetApiV1Organizations,
  useOrganizationsServiceGetApiV1OrganizationsByOrganizationIdProducts,
  useProductsServiceGetApiV1ProductsByProductIdRepositories,
  useRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRuns,
} from '@/api/queries';
import { ApiError } from '@/api/requests';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { ALL_ITEMS } from '@/lib/constants';
import { BreadcrumbItem, BreadcrumbLink } from './ui/breadcrumb';

type SiblingsProps = {
  entity: 'organization' | 'product' | 'repository' | 'run';
  pathName?: string;
};

export const Siblings = ({ entity, pathName }: SiblingsProps) => {
  const router = useRouter();
  const breadcrumbs = router.options.context.breadcrumbs;
  const params = useParams({ strict: false });

  // This logic is used to determine the current "entity" level the user is at
  // (from route params), and to fetch the appropriate siblings based on the current
  // route.
  // For example, if the user is viewing a pdroduct, we fetch all "sigling" products
  // under the current organization. If they are viewing a repository, we fetch
  // all repositories under the current product, and so on.
  //
  // By using the `enabled` option in the query hooks, we can conditionally fetch the
  // data only when the relevant params are present, to prevent unnecessary API calls
  // and query client's cache pollution.

  const {
    data: organizations,
    isPending: isOrgPending,
    isError: isOrgError,
    error: orgError,
  } = useOrganizationsServiceGetApiV1Organizations({
    limit: ALL_ITEMS,
  });

  const {
    data: products,
    isPending: isProductsPending,
    isError: isProductsError,
    error: productsError,
  } = useOrganizationsServiceGetApiV1OrganizationsByOrganizationIdProducts({
    organizationId: Number(params.orgId) ?? '',
  });

  const {
    data: repositories,
    isPending: isRepositoriesPending,
    isError: isRepositoriesError,
    error: repositoriesError,
  } = useProductsServiceGetApiV1ProductsByProductIdRepositories({
    productId: Number(params.productId) ?? '',
  });

  const {
    data: runs,
    isPending: isRunsPending,
    isError: isRunsError,
    error: runsError,
  } = useRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRuns({
    repositoryId: Number(params.repoId) ?? '',
  });

  const name =
    entity === 'organization'
      ? breadcrumbs.organization
      : entity === 'product'
        ? breadcrumbs.product
        : entity === 'repository'
          ? breadcrumbs.repo
          : breadcrumbs.run;

  const orgSiblings = organizations?.data.filter(
    (org) => org.id !== Number(params.orgId)
  );

  const prodSiblings = products?.data.filter(
    (prod) => prod.id !== Number(params.productId)
  );
  const repoSiblings = repositories?.data.filter(
    (repo) => repo.id !== Number(params.repoId)
  );
  const runSiblings = runs?.data
    .sort((a, b) => b.index - a.index)
    .filter((run) => run.index !== Number(params.runIndex));

  return (
    <BreadcrumbItem>
      <BreadcrumbLink asChild>
        <Link to={pathName}>{name}</Link>
      </BreadcrumbLink>
      {entity === 'organization' && orgSiblings && orgSiblings.length > 0 && (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <ChevronDown className='mr-1 size-4 cursor-pointer' />
          </DropdownMenuTrigger>
          <DropdownMenuContent className='max-h-96'>
            <>
              {isOrgPending && <DropdownMenuItem>Loading...</DropdownMenuItem>}
              {isOrgError && (
                <DropdownMenuItem className='text-red-500'>
                  Error:{' '}
                  {`Failed to load organizations: ${(orgError as ApiError).message}`}
                </DropdownMenuItem>
              )}
              {orgSiblings.map((org) => (
                <DropdownMenuItem key={org.id}>
                  <Link
                    to='/organizations/$orgId'
                    params={{ orgId: org.id.toString() ?? '' }}
                  >
                    {org.name}
                  </Link>
                </DropdownMenuItem>
              ))}
            </>
          </DropdownMenuContent>
        </DropdownMenu>
      )}
      {entity === 'product' && prodSiblings && prodSiblings.length > 0 && (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <ChevronDown className='mr-1 size-4 cursor-pointer' />
          </DropdownMenuTrigger>
          <DropdownMenuContent className='max-h-96'>
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
              {prodSiblings.map((prod) => (
                <DropdownMenuItem key={prod.id}>
                  <Link
                    to='/organizations/$orgId/products/$productId'
                    params={{
                      orgId: params.orgId ?? '',
                      productId: prod.id.toString() ?? '',
                    }}
                  >
                    {prod.name}
                  </Link>
                </DropdownMenuItem>
              ))}
            </>
          </DropdownMenuContent>
        </DropdownMenu>
      )}
      {entity === 'repository' && repoSiblings && repoSiblings.length > 0 && (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <ChevronDown className='mr-1 size-4 cursor-pointer' />
          </DropdownMenuTrigger>
          <DropdownMenuContent className='max-h-96'>
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
              {repoSiblings?.map((repo) => (
                <DropdownMenuItem key={repo.id}>
                  <Link
                    to='/organizations/$orgId/products/$productId/repositories/$repoId'
                    params={{
                      orgId: params.orgId ?? '',
                      productId: params.productId ?? '',
                      repoId: repo.id.toString() ?? '',
                    }}
                  >
                    {repo.url}
                  </Link>
                </DropdownMenuItem>
              ))}
            </>
          </DropdownMenuContent>
        </DropdownMenu>
      )}
      {entity === 'run' && runSiblings && runSiblings.length > 0 && (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <ChevronDown className='mr-1 size-4 cursor-pointer' />
          </DropdownMenuTrigger>
          <DropdownMenuContent className='max-h-96'>
            <>
              {isRunsPending && <DropdownMenuItem>Loading...</DropdownMenuItem>}
              {isRunsError && (
                <DropdownMenuItem className='text-red-500'>
                  Error:{' '}
                  {`Failed to load runs: ${(runsError as ApiError).message}`}
                </DropdownMenuItem>
              )}
              {runSiblings?.map((run) => (
                <DropdownMenuItem key={run.index}>
                  <Link
                    to='/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex'
                    params={{
                      orgId: params.orgId ?? '',
                      productId: params.productId ?? '',
                      repoId: params.repoId ?? '',
                      runIndex: run.index.toString() ?? '',
                    }}
                  >
                    {run.index}
                  </Link>
                </DropdownMenuItem>
              ))}
            </>
          </DropdownMenuContent>
        </DropdownMenu>
      )}
    </BreadcrumbItem>
  );
};
