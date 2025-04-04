/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import { Link, LinkOptions, useNavigate } from '@tanstack/react-router';
import {
  ChevronLeft,
  ChevronRight,
  ChevronsLeft,
  ChevronsRight,
} from 'lucide-react';
import { useEffect, useState } from 'react';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';

interface DataTablePaginationProps {
  pageSizeOptions?: number[];
  currentPage: number;
  totalPages: number;
  pageSize: number;
  /**
   * A function to provide `LinkOptions` for a link to set page size parameter in the URL.
   */
  setPageSizeOptions: (pageSize: number) => LinkOptions;
  /**
   * A function to provide `LinkOptions` for a link to set current page parameter in the URL.
   */
  setCurrentPageOptions: (page: number) => LinkOptions;
}

export function DataTablePagination({
  pageSizeOptions = [5, 10, 20, 30, 40, 50],
  currentPage,
  totalPages,
  pageSize,
  setPageSizeOptions,
  setCurrentPageOptions,
}: DataTablePaginationProps) {
  const navigate = useNavigate();
  const [page, setPage] = useState(currentPage);

  // The current page lives in the URL state but a local state is used to handle the page input field.
  // This effect synchronizes the local state with the URL state.
  useEffect(() => {
    setPage(currentPage);
  }, [currentPage]);

  return (
    <div className='flex flex-col items-center justify-between gap-4 sm:flex-row sm:gap-6 lg:gap-8'>
      <div className='flex items-center space-x-2'>
        <Select
          value={`${pageSize}`}
          onValueChange={(value) => {
            const options = setPageSizeOptions(Number(value));
            navigate(options);
          }}
        >
          <SelectTrigger className='h-8 w-[4.5rem]'>
            <SelectValue placeholder={pageSize} />
          </SelectTrigger>
          <SelectContent side='top'>
            {pageSizeOptions.map((option) => (
              <SelectItem key={option} value={`${option}`}>
                {option}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <p className='text-sm font-medium whitespace-nowrap'>items per page</p>
      </div>
      <div className='flex items-center space-x-2'>
        <div className='flex items-center text-sm font-medium whitespace-nowrap'>
          Page{' '}
          <form
            className='flex items-center'
            onSubmit={(event) => {
              event.preventDefault();
              navigate(setCurrentPageOptions(page));
            }}
          >
            <Input
              type='number'
              value={page}
              onBlur={() => {
                navigate(setCurrentPageOptions(page));
              }}
              onChange={(event) => {
                const value = Number(event.target.value);
                setPage(
                  value > totalPages ? totalPages : value < 1 ? 1 : value
                );
              }}
              className='mx-2 [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none'
              max={totalPages}
              min={1}
            />{' '}
            of {totalPages}
          </form>
        </div>
        <div className='flex items-center space-x-2'>
          <Link disabled={currentPage <= 1} {...setCurrentPageOptions(1)}>
            <Button
              aria-label='Go to first page'
              variant='outline'
              className='hidden size-8 p-0 lg:flex'
              disabled={currentPage <= 1}
              onClick={() => setPage(1)}
            >
              <ChevronsLeft className='size-4' aria-hidden='true' />
            </Button>
          </Link>
          <Link
            disabled={currentPage <= 1}
            {...setCurrentPageOptions(currentPage - 1)}
          >
            <Button
              aria-label='Go to previous page'
              variant='outline'
              size='icon'
              className='size-8'
              disabled={currentPage <= 1}
              onClick={() => setPage((prevPage) => prevPage - 1)}
            >
              <ChevronLeft className='size-4' aria-hidden='true' />
            </Button>
          </Link>
          <Link
            disabled={currentPage >= totalPages}
            {...setCurrentPageOptions(currentPage + 1)}
          >
            <Button
              aria-label='Go to next page'
              variant='outline'
              size='icon'
              className='size-8'
              disabled={currentPage >= totalPages}
              onClick={() => setPage((prevPage) => prevPage + 1)}
            >
              <ChevronRight className='size-4' aria-hidden='true' />
            </Button>
          </Link>
          <Link
            disabled={currentPage >= totalPages}
            {...setCurrentPageOptions(totalPages)}
          >
            <Button
              aria-label='Go to last page'
              variant='outline'
              size='icon'
              className='hidden size-8 lg:flex'
              onClick={() => setPage(totalPages)}
              disabled={currentPage >= totalPages}
            >
              <ChevronsRight className='size-4' aria-hidden='true' />
            </Button>
          </Link>
        </div>
      </div>
    </div>
  );
}
