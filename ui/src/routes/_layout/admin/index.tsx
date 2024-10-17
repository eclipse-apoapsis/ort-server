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

import { createFileRoute, Link } from '@tanstack/react-router';
import {
  ColumnDef,
  flexRender,
  getCoreRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  SortingState,
  useReactTable,
} from '@tanstack/react-table';
import {
  ChevronDownIcon,
  ChevronsUpDownIcon,
  ChevronUpIcon,
} from 'lucide-react';
import { useState } from 'react';
import { z } from 'zod';

import { useOrganizationsServiceGetOrganizationsSuspense } from '@/api/queries/suspense';
import { Organization } from '@/api/requests';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { cn } from '@/lib/utils';

const columns: ColumnDef<Organization>[] = [
  {
    accessorKey: 'id',
    header: ({ column }) => {
      return (
        <Button
          variant='ghost'
          className='px-0'
          onClick={() => column.toggleSorting(column.getIsSorted() === 'asc')}
        >
          <Label className='cursor-pointer font-bold'>Id</Label>
          {column.getIsSorted() === 'desc' ? (
            <ChevronDownIcon className='ml-2 h-4 w-4' />
          ) : column.getIsSorted() === 'asc' ? (
            <ChevronUpIcon className='ml-2 h-4 w-4' />
          ) : (
            <ChevronsUpDownIcon className='ml-2 h-4 w-4' />
          )}
        </Button>
      );
    },
    cell: ({ row }) => row.original.id,
  },
  {
    accessorKey: 'name',
    header: ({ column }) => {
      return (
        <Button
          variant='ghost'
          className='px-0'
          onClick={() => column.toggleSorting(column.getIsSorted() === 'asc')}
        >
          <Label className='cursor-pointer font-bold'>Name</Label>
          {column.getIsSorted() === 'desc' ? (
            <ChevronDownIcon className='ml-2 h-4 w-4' />
          ) : column.getIsSorted() === 'asc' ? (
            <ChevronUpIcon className='ml-2 h-4 w-4' />
          ) : (
            <ChevronsUpDownIcon className='ml-2 h-4 w-4' />
          )}
        </Button>
      );
    },
    cell: ({ row }) => (
      <Link
        className='font-semibold text-blue-400 hover:underline'
        to='/organizations/$orgId'
        params={{ orgId: row.original.id.toString() }}
      >
        {row.original.name}
      </Link>
    ),
  },
];

interface DataTableProps<TData, TValue> {
  columns: ColumnDef<TData, TValue>[];
  data: TData[];
}

const DataTable = <TData, TValue>({
  columns,
  data,
}: DataTableProps<TData, TValue>) => {
  const [sorting, setSorting] = useState<SortingState>([
    { id: 'id', desc: false },
  ]);

  const table = useReactTable({
    data,
    columns,
    getCoreRowModel: getCoreRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    onSortingChange: setSorting,
    getSortedRowModel: getSortedRowModel(),
    state: {
      sorting,
    },
  });

  return (
    <div>
      <div className='rounded-md border'>
        <Table>
          <TableHeader>
            {table.getHeaderGroups().map((headerGroup) => (
              <TableRow key={headerGroup.id}>
                {headerGroup.headers.map((header) => {
                  return (
                    <TableHead key={header.id}>
                      {header.isPlaceholder
                        ? null
                        : flexRender(
                            header.column.columnDef.header,
                            header.getContext()
                          )}
                    </TableHead>
                  );
                })}
              </TableRow>
            ))}
          </TableHeader>
          <TableBody>
            {table.getRowModel().rows?.length ? (
              table.getRowModel().rows.map((row) => (
                <TableRow
                  key={row.id}
                  data-state={row.getIsSelected() && 'selected'}
                >
                  {row.getVisibleCells().map((cell) => (
                    <TableCell key={cell.id}>
                      {flexRender(
                        cell.column.columnDef.cell,
                        cell.getContext()
                      )}
                    </TableCell>
                  ))}
                </TableRow>
              ))
            ) : (
              <TableRow>
                <TableCell
                  colSpan={columns.length}
                  className='h-24 text-center'
                >
                  No results.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>
      <div className='flex items-center justify-end space-x-2 py-4'>
        <Button
          variant='outline'
          size='sm'
          onClick={() => table.previousPage()}
          disabled={!table.getCanPreviousPage()}
        >
          Previous
        </Button>
        <Button
          variant='outline'
          size='sm'
          onClick={() => table.nextPage()}
          disabled={!table.getCanNextPage()}
        >
          Next
        </Button>
      </div>
    </div>
  );
};

const OverviewContent = () => {
  const { data } = useOrganizationsServiceGetOrganizationsSuspense({
    limit: 1000,
  });

  return (
    <>
      <div className='grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4'>
        <Card>
          <CardHeader className='flex flex-row items-center justify-between space-y-0 pb-2'>
            <CardTitle className='text-sm'>Organizations count</CardTitle>
          </CardHeader>
          <CardContent>
            <div className='text-2xl font-bold'>{data.data.length}</div>
            <p className='hidden text-xs text-muted-foreground'>total</p>
          </CardContent>
        </Card>
      </div>
      <div className='grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-7'>
        <Card className='col-span-4'>
          <CardHeader>
            <CardTitle>Organizations</CardTitle>
          </CardHeader>
          <CardContent>
            <div className='w-full'>
              <DataTable columns={columns} data={data.data} />
            </div>
          </CardContent>
        </Card>
      </div>
    </>
  );
};

const AdminDashboard = () => {
  const navigate = Route.useNavigate();
  const search = Route.useSearch();

  return (
    <>
      <div className='flex flex-col'>
        <div className='flex-1 space-y-4'>
          <div className='flex items-center justify-between space-y-2'>
            <h2 className='text-3xl font-bold tracking-tight'>Dashboard</h2>
          </div>
          <div className='space-y-4'>
            <div className='inline-flex h-9 items-center justify-center rounded-lg bg-muted p-1 text-muted-foreground'>
              <Button
                variant='ghost'
                className={cn(
                  search.tab === undefined || search.tab === 'overview'
                    ? 'bg-background text-foreground shadow hover:bg-background'
                    : undefined,
                  'h-7 px-3 font-semibold'
                )}
                onClick={() => navigate({ search: { tab: 'overview' } })}
              >
                Overview
              </Button>
            </div>
            <div className='mt-2 space-y-4 ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2'>
              {(search.tab === 'overview' || !search.tab) && (
                <OverviewContent />
              )}
            </div>
          </div>
        </div>
      </div>
    </>
  );
};

const adminSearchSchema = z.object({
  tab: z.string().optional(),
});

export const Route = createFileRoute('/_layout/admin/')({
  validateSearch: adminSearchSchema,
  component: AdminDashboard,
});
