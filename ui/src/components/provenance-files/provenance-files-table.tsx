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

import { Download, XCircle } from 'lucide-react';
import { useMemo, useRef, useState } from 'react';

import { ProvenanceFile } from '@/api';
import { DataTable } from '@/components/data-table/data-table';
import { DeleteDialog } from '@/components/delete-dialog';
import { DeleteIconButton } from '@/components/delete-icon-button';
import { TimestampWithUTC } from '@/components/timestamp-with-utc';
import { TooltipIfTruncated } from '@/components/tooltip-if-truncated';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { formatFileSize } from '@/helpers/format-file-size';
import { EMPTY_SORTING_STATE } from '@/helpers/handle-multisort';
import {
  createAppColumnHelper,
  selectNoTableState,
  useAppTable,
} from '@/hooks/use-app-table';
import { AdminProvenanceFilesSearchParameters } from '@/schemas';
import { provenanceFileKindLabels } from './utils';

type DataTableProps = React.ComponentProps<typeof DataTable>;

type ProvenanceFilesTableProps = {
  /** The files on the current page. */
  files: ProvenanceFile[];
  /** The total number of files matching the current filter. */
  totalCount: number;
  /** The current search parameters of the page. */
  search: AdminProvenanceFilesSearchParameters;
  /** The page size to use if none is set in the search parameters. */
  defaultPageSize: number;
  /** The human-readable name of a single file, used in the delete dialog. */
  thingName: string;
  /** Download the file with the given ID. This should be a stable function to avoid recreating the columns. */
  onDownload: (id: string) => Promise<unknown>;
  /** Delete the file with the given ID. This should be a stable function to avoid recreating the columns. */
  onDelete: (id: string) => Promise<unknown>;
  /** Apply the given filter to the table. */
  onFilterChange: (filter: string | undefined) => void;
  setCurrentPageOptions: DataTableProps['setCurrentPageOptions'];
  setPageSizeOptions: DataTableProps['setPageSizeOptions'];
  setSortingOptions: DataTableProps['setSortingOptions'];
};

const columnHelper = createAppColumnHelper<ProvenanceFile>();

const createColumns = (
  thingName: string,
  onDownload: (id: string) => Promise<unknown>,
  onDelete: (id: string) => Promise<unknown>
) =>
  columnHelper.columns([
    columnHelper.accessor('kind', {
      header: 'Kind',
      enableSorting: false,
      size: 130,
      cell: ({ row }) => (
        <Badge variant='outline'>
          {provenanceFileKindLabels[row.original.kind]}
        </Badge>
      ),
    }),
    columnHelper.accessor('url', {
      id: 'url',
      header: 'URL',
      cell: ({ row }) => (
        <TooltipIfTruncated text={row.original.url ?? row.original.key} />
      ),
    }),
    columnHelper.display({
      id: 'version',
      header: 'Revision / Hash',
      enableSorting: false,
      cell: ({ row }) => {
        const { vcsType, revision, hashValue, hashAlgorithm } = row.original;
        const text = revision
          ? `${vcsType ? `${vcsType}: ` : ''}${revision}`
          : hashValue
            ? `${hashAlgorithm ? `${hashAlgorithm}: ` : ''}${hashValue}`
            : '';

        return <TooltipIfTruncated text={text} />;
      },
    }),
    columnHelper.accessor('sizeInBytes', {
      id: 'sizeInBytes',
      header: 'Size',
      size: 100,
      cell: ({ row }) => <>{formatFileSize(row.original.sizeInBytes)}</>,
    }),
    columnHelper.accessor('lastModified', {
      id: 'lastModified',
      header: 'Last modified',
      size: 180,
      cell: ({ row }) =>
        row.original.lastModified ? (
          <TimestampWithUTC timestamp={row.original.lastModified} />
        ) : null,
    }),
    columnHelper.display({
      id: 'actions',
      header: () => <div className='text-right'>Actions</div>,
      enableSorting: false,
      size: 100,
      cell: ({ row }) => (
        <div className='flex justify-end gap-1'>
          <Button
            size='sm'
            variant='outline'
            className='h-8 px-2'
            onClick={() => onDownload(row.original.id)}
          >
            <span className='sr-only'>Download</span>
            <Download size={16} />
          </Button>
          <DeleteDialog
            thingName={thingName}
            description={
              <>
                Are you sure you want to delete the {thingName} for{' '}
                <span className='font-bold break-all'>
                  {row.original.url ?? row.original.key}
                </span>
                ? It will be recreated when the provenance is scanned again.
                However, currently active runs that depend on this {thingName}{' '}
                might fail or produce inconsistent results.
              </>
            }
            uiComponent={<DeleteIconButton />}
            onDelete={async () => {
              await onDelete(row.original.id);
            }}
          />
        </div>
      ),
    }),
  ]);

const FilterInput = ({
  filter,
  onFilterChange,
}: {
  filter: string;
  onFilterChange: (filter: string | undefined) => void;
}) => {
  const [input, setInput] = useState(filter);
  const lastApplied = useRef(filter);

  const apply = (value: string) => {
    const normalized = value.trim();
    if (normalized === lastApplied.current) return;

    lastApplied.current = normalized;
    onFilterChange(normalized || undefined);
  };

  return (
    <form
      className='mb-4 max-w-xl'
      onSubmit={(event) => {
        event.preventDefault();
        apply(input);
      }}
    >
      <label
        className='mb-2 block text-sm font-medium'
        htmlFor='provenance-file-filter'
      >
        Filter
      </label>
      <div className='flex gap-2'>
        <Input
          id='provenance-file-filter'
          type='search'
          placeholder='Filter by URL, revision, hash, or storage key'
          value={input}
          onChange={(event) => setInput(event.target.value)}
          onBlur={() => apply(input)}
        />
        <Button
          type='button'
          variant='ghost'
          className='px-2'
          onMouseDown={(event) => event.preventDefault()}
          onClick={() => {
            setInput('');
            apply('');
          }}
        >
          <XCircle
            className={
              input.length === 0
                ? 'h-fit text-gray-400 opacity-40'
                : 'h-fit text-gray-400 opacity-100'
            }
          />
          <span className='sr-only'>Clear filter</span>
        </Button>
      </div>
    </form>
  );
};

/**
 * A table of stored provenance files, such as file archives or file lists, with a filter and a delete action.
 */
export const ProvenanceFilesTable = ({
  files,
  totalCount,
  search,
  defaultPageSize,
  thingName,
  onDownload,
  onDelete,
  onFilterChange,
  setCurrentPageOptions,
  setPageSizeOptions,
  setSortingOptions,
}: ProvenanceFilesTableProps) => {
  const pageIndex = search.page ? search.page - 1 : 0;
  const pageSize = search.pageSize ?? defaultPageSize;
  const columns = useMemo(
    () => createColumns(thingName, onDownload, onDelete),
    [thingName, onDownload, onDelete]
  );

  const table = useAppTable(
    {
      data: files,
      columns,
      pageCount: Math.ceil(totalCount / pageSize),
      state: {
        pagination: { pageIndex, pageSize },
        sorting: search.sortBy ?? EMPTY_SORTING_STATE,
      },
      manualPagination: true,
      manualSorting: true,
    },
    selectNoTableState
  );

  return (
    <>
      <FilterInput
        key={search.filter ?? ''}
        filter={search.filter ?? ''}
        onFilterChange={onFilterChange}
      />
      <DataTable
        table={table}
        setCurrentPageOptions={setCurrentPageOptions}
        setPageSizeOptions={setPageSizeOptions}
        setSortingOptions={setSortingOptions}
      />
    </>
  );
};
