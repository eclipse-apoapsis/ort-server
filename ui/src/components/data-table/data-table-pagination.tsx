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

import {
  ChevronLeftIcon,
  ChevronRightIcon,
  DoubleArrowLeftIcon,
  DoubleArrowRightIcon,
} from '@radix-ui/react-icons';
import { Link, useNavigate } from '@tanstack/react-router';
import { type Table } from '@tanstack/react-table';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';

interface DataTablePaginationProps<TData> {
  table: Table<TData>;
  pageSizeOptions?: number[];
}

export function DataTablePagination<TData>({
  table,
  pageSizeOptions = [5, 10, 20, 30, 40, 50],
}: DataTablePaginationProps<TData>) {
  const navigate = useNavigate();
  const page = table.getState().pagination.pageIndex + 1;
  const pageSize = table.getState().pagination.pageSize;
  const pageCount = table.getPageCount();

  return (
    <div className='flex flex-col items-center justify-end gap-4 sm:flex-row sm:gap-6 lg:gap-8'>
      <div className='flex items-center space-x-2'>
        <p className='whitespace-nowrap text-sm font-medium'>Items per page</p>
        <Select
          value={`${pageSize}`}
          onValueChange={(value) => {
            navigate({
              search: {
                page: 1,
                pageSize: Number(value),
              },
            });
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
      </div>
      <div className='flex items-center whitespace-nowrap text-sm font-medium'>
        Page{' '}
        <Input
          type='number'
          value={page}
          onChange={(event) => {
            const value = Number(event.target.value);
            navigate({
              search: {
                page: value > pageCount ? pageCount : value < 1 ? 1 : value,
                pageSize,
              },
            });
          }}
          className='mx-2 [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none'
          max={pageCount}
          min={1}
        />{' '}
        of {pageCount}
      </div>
      <div className='flex items-center space-x-2'>
        <Link
          search={(prev) => ({
            ...prev,
            page: 1,
          })}
        >
          <Button
            aria-label='Go to first page'
            variant='outline'
            className='hidden size-8 p-0 lg:flex'
            disabled={!table.getCanPreviousPage()}
          >
            <DoubleArrowLeftIcon className='size-4' aria-hidden='true' />
          </Button>
        </Link>
        <Link
          search={(prev) => ({
            ...prev,
            page: page - 1,
          })}
        >
          <Button
            aria-label='Go to previous page'
            variant='outline'
            size='icon'
            className='size-8'
            disabled={!table.getCanPreviousPage()}
          >
            <ChevronLeftIcon className='size-4' aria-hidden='true' />
          </Button>
        </Link>
        <Link
          search={(prev) => ({
            ...prev,
            page: page + 1,
          })}
        >
          <Button
            aria-label='Go to next page'
            variant='outline'
            size='icon'
            className='size-8'
            disabled={!table.getCanNextPage()}
          >
            <ChevronRightIcon className='size-4' aria-hidden='true' />
          </Button>
        </Link>
        <Link
          search={(prev) => ({
            ...prev,
            page: pageCount,
          })}
        >
          <Button
            aria-label='Go to last page'
            variant='outline'
            size='icon'
            className='hidden size-8 lg:flex'
            disabled={!table.getCanNextPage()}
          >
            <DoubleArrowRightIcon className='size-4' aria-hidden='true' />
          </Button>
        </Link>
      </div>
    </div>
  );
}
