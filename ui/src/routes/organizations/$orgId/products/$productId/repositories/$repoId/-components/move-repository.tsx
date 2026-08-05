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

import { useMutation } from '@tanstack/react-query';
import { useNavigate, useParams } from '@tanstack/react-router';
import { Check, ChevronsUpDown } from 'lucide-react';
import { useState } from 'react';

import {
  getOrganizationProductsInfiniteOptions,
  getOrganizationsInfiniteOptions,
  patchRepositoryMutation,
} from '@/api/@tanstack/react-query.gen';
import { OptionalValueLong } from '@/api/types.gen';
import { MoveDialog } from '@/components/move-dialog';
import { SearchableInfiniteList } from '@/components/searchable-infinite-list';
import { Button } from '@/components/ui/button';
import { CommandItem } from '@/components/ui/command';
import { useDebounce } from '@/hooks/use-debounce';
import { useInfiniteList } from '@/hooks/use-infinite-list';
import { ApiError } from '@/lib/api-error';
import { DROPDOWN_PAGE_SIZE } from '@/lib/constants';
import { escapeRegex } from '@/lib/regex';
import { toastError } from '@/lib/toast';
import { cn } from '@/lib/utils';

interface MoveRepositoryProps {
  repoUrl: string;
}

/**
 * A selection is kept as the whole entry instead of only its ID, because the entry the user picked
 * is not necessarily part of the page that is shown once the search term changes.
 */
type Selection = { id: number; name: string };

/** Turn what the user typed into a filter, as the API reads it as a regular expression. */
const toFilter = (searchTerm: string) =>
  searchTerm ? escapeRegex(searchTerm) : undefined;

export const MoveRepository = ({ repoUrl }: MoveRepositoryProps) => {
  const params = useParams({ strict: false });
  const navigate = useNavigate();

  const repositoryId = Number.parseInt(params.repoId!);

  const [selectedOrg, setSelectedOrg] = useState<Selection | null>(null);
  const [selectedProduct, setSelectedProduct] = useState<Selection | null>(
    null
  );
  const [orgOpen, setOrgOpen] = useState(false);
  const [productOpen, setProductOpen] = useState(false);
  const [orgSearch, setOrgSearch] = useState('');
  const [productSearch, setProductSearch] = useState('');

  const debouncedOrgSearch = useDebounce(orgSearch, 300);
  const debouncedProductSearch = useDebounce(productSearch, 300);

  const organizations = useInfiniteList(
    getOrganizationsInfiniteOptions({
      query: {
        limit: DROPDOWN_PAGE_SIZE,
        filter: toFilter(debouncedOrgSearch),
      },
    }),
    { enabled: orgOpen }
  );

  const products = useInfiniteList(
    getOrganizationProductsInfiniteOptions({
      // The query is disabled until an organization has been selected.
      path: { organizationId: selectedOrg?.id ?? -1 },
      query: {
        limit: DROPDOWN_PAGE_SIZE,
        filter: toFilter(debouncedProductSearch),
      },
    }),
    { enabled: productOpen && selectedOrg !== null }
  );

  const isMoveEnabled = selectedOrg !== null && selectedProduct !== null;

  const { mutateAsync: moveRepository } = useMutation({
    ...patchRepositoryMutation(),
    onSuccess() {
      navigate({
        to: '/organizations/$orgId/products/$productId/repositories/$repoId',
        params: {
          orgId: String(selectedOrg?.id),
          productId: String(selectedProduct?.id),
          repoId: params.repoId!,
        },
        reloadDocument: true,
      });
    },
    onError(error: ApiError) {
      toastError(error.message, error);
    },
  });

  async function handleMove() {
    await moveRepository({
      path: { repositoryId },
      body: {
        productId: selectedProduct?.id as unknown as OptionalValueLong,
      },
    });
  }

  return (
    <div className='flex items-center gap-2'>
      <SearchableInfiniteList
        trigger={
          <Button
            variant='outline'
            role='combobox'
            aria-expanded={orgOpen}
            className='w-[240px] justify-between gap-2'
          >
            <span className='min-w-0 truncate text-left'>
              {selectedOrg ? selectedOrg.name : 'Select organization'}
            </span>
            <ChevronsUpDown className='ml-2 h-4 w-4 shrink-0 opacity-50' />
          </Button>
        }
        open={orgOpen}
        onOpenChange={setOrgOpen}
        list={organizations}
        getItemKey={(org) => org.id}
        renderItem={(org) => (
          <CommandItem
            value={org.name}
            onSelect={() => {
              setSelectedOrg(org.id === selectedOrg?.id ? null : org);
              // The products of another organization are different ones.
              setSelectedProduct(null);
              setProductSearch('');
              setOrgOpen(false);
            }}
          >
            <Check
              className={cn(
                'mr-2 h-4 w-4',
                selectedOrg?.id === org.id ? 'opacity-100' : 'opacity-0'
              )}
            />
            <span className='truncate'>{org.name}</span>
          </CommandItem>
        )}
        searchPlaceholder='Search organization...'
        searchTerm={orgSearch}
        onSearchTermChange={setOrgSearch}
        emptyMessage='No organization found.'
        errorMessage='Failed to load organizations'
      />
      <SearchableInfiniteList
        trigger={
          <Button
            variant='outline'
            role='combobox'
            aria-expanded={productOpen}
            className='w-[240px] justify-between gap-2'
            disabled={selectedOrg === null}
          >
            <span className='min-w-0 truncate text-left'>
              {selectedProduct ? selectedProduct.name : 'Select product'}
            </span>
            <ChevronsUpDown className='ml-2 h-4 w-4 shrink-0 opacity-50' />
          </Button>
        }
        open={productOpen}
        onOpenChange={setProductOpen}
        list={products}
        getItemKey={(product) => product.id}
        renderItem={(product) => (
          <CommandItem
            value={product.name}
            onSelect={() => {
              setSelectedProduct(
                product.id === selectedProduct?.id ? null : product
              );
              setProductOpen(false);
            }}
          >
            <Check
              className={cn(
                'mr-2 h-4 w-4',
                selectedProduct?.id === product.id ? 'opacity-100' : 'opacity-0'
              )}
            />
            <span className='truncate'>{product.name}</span>
          </CommandItem>
        )}
        searchPlaceholder='Search product...'
        searchTerm={productSearch}
        onSearchTermChange={setProductSearch}
        emptyMessage='No product found.'
        errorMessage='Failed to load products'
      />
      <div className='flex-1' />
      <MoveDialog
        thingName='repository'
        thingId={repoUrl}
        title='Confirm Move'
        tooltip='Move repository to another product'
        uiComponent={
          <Button variant='destructive' disabled={!isMoveEnabled}>
            Move repository
          </Button>
        }
        onMove={handleMove}
      />
    </div>
  );
};
