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

import { useMutation, useQuery } from '@tanstack/react-query';
import { useNavigate, useParams } from '@tanstack/react-router';
import { Check, ChevronsUpDown } from 'lucide-react';
import { useState } from 'react';

import {
  getOrganizationProductsOptions,
  getOrganizationsOptions,
  patchRepositoryMutation,
} from '@/api/@tanstack/react-query.gen';
import { OptionalValueLong } from '@/api/types.gen';
import { MoveDialog } from '@/components/move-dialog';
import { ToastError } from '@/components/toast-error';
import { Button } from '@/components/ui/button';
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/ui/command';
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover';
import { ApiError } from '@/lib/api-error';
import { ALL_ITEMS } from '@/lib/constants';
import { toast } from '@/lib/toast';
import { cn } from '@/lib/utils';

interface MoveRepositoryProps {
  repoUrl: string;
}

export const MoveRepository = ({ repoUrl }: MoveRepositoryProps) => {
  const params = useParams({ strict: false });
  const navigate = useNavigate();

  const repositoryId = Number.parseInt(params.repoId!);

  const [selectedOrgId, setSelectedOrgId] = useState<number | null>(null);
  const [selectedProductId, setSelectedProductId] = useState<number | null>(
    null
  );
  const [orgOpen, setOrgOpen] = useState(false);
  const [productOpen, setProductOpen] = useState(false);

  const { data: orgsData } = useQuery({
    ...getOrganizationsOptions({
      query: { limit: ALL_ITEMS },
    }),
  });

  const { data: productsData } = useQuery({
    ...getOrganizationProductsOptions({
      path: { organizationId: selectedOrgId! },
      query: { limit: ALL_ITEMS },
    }),
    enabled: selectedOrgId !== null,
  });

  const organizations = orgsData?.data ?? [];
  const products = productsData?.data ?? [];

  const selectedOrg = organizations.find((o) => o.id === selectedOrgId);
  const selectedProduct = products.find((p) => p.id === selectedProductId);

  const isMoveEnabled = selectedOrgId !== null && selectedProductId !== null;

  const { mutateAsync: moveRepository } = useMutation({
    ...patchRepositoryMutation(),
    onSuccess() {
      navigate({
        to: '/organizations/$orgId/products/$productId/repositories/$repoId',
        params: {
          orgId: String(selectedOrgId),
          productId: String(selectedProductId),
          repoId: params.repoId!,
        },
        reloadDocument: true,
      });
    },
    onError(error: ApiError) {
      toast.error(error.message, {
        description: <ToastError error={error} />,
        duration: Infinity,
        cancel: {
          label: 'Dismiss',
          onClick: () => {},
        },
      });
    },
  });

  async function handleMove() {
    await moveRepository({
      path: { repositoryId },
      body: {
        productId: selectedProductId as unknown as OptionalValueLong,
      },
    });
  }

  return (
    <div className='flex items-center gap-2'>
      <Popover open={orgOpen} onOpenChange={setOrgOpen}>
        <PopoverTrigger asChild>
          <Button
            variant='outline'
            role='combobox'
            aria-expanded={orgOpen}
            className='w-[200px] justify-between'
          >
            {selectedOrg ? selectedOrg.name : 'Select organization'}
            <ChevronsUpDown className='ml-2 h-4 w-4 shrink-0 opacity-50' />
          </Button>
        </PopoverTrigger>
        <PopoverContent className='w-[200px] p-0'>
          <Command>
            <CommandInput placeholder='Search organization...' />
            <CommandList>
              <CommandEmpty>No organization found.</CommandEmpty>
              <CommandGroup>
                {organizations.map((org) => (
                  <CommandItem
                    key={org.id}
                    value={org.name}
                    onSelect={() => {
                      const newOrgId = org.id === selectedOrgId ? null : org.id;
                      setSelectedOrgId(newOrgId);
                      setSelectedProductId(null);
                      setOrgOpen(false);
                    }}
                  >
                    <Check
                      className={cn(
                        'mr-2 h-4 w-4',
                        selectedOrgId === org.id ? 'opacity-100' : 'opacity-0'
                      )}
                    />
                    {org.name}
                  </CommandItem>
                ))}
              </CommandGroup>
            </CommandList>
          </Command>
        </PopoverContent>
      </Popover>
      <Popover open={productOpen} onOpenChange={setProductOpen}>
        <PopoverTrigger asChild>
          <Button
            variant='outline'
            role='combobox'
            aria-expanded={productOpen}
            className='w-[200px] justify-between'
            disabled={selectedOrgId === null}
          >
            {selectedProduct ? selectedProduct.name : 'Select product'}
            <ChevronsUpDown className='ml-2 h-4 w-4 shrink-0 opacity-50' />
          </Button>
        </PopoverTrigger>
        <PopoverContent className='w-[200px] p-0'>
          <Command>
            <CommandInput placeholder='Search product...' />
            <CommandList>
              <CommandEmpty>No product found.</CommandEmpty>
              <CommandGroup>
                {products.map((product) => (
                  <CommandItem
                    key={product.id}
                    value={product.name}
                    onSelect={() => {
                      setSelectedProductId(
                        product.id === selectedProductId ? null : product.id
                      );
                      setProductOpen(false);
                    }}
                  >
                    <Check
                      className={cn(
                        'mr-2 h-4 w-4',
                        selectedProductId === product.id
                          ? 'opacity-100'
                          : 'opacity-0'
                      )}
                    />
                    {product.name}
                  </CommandItem>
                ))}
              </CommandGroup>
            </CommandList>
          </Command>
        </PopoverContent>
      </Popover>
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
