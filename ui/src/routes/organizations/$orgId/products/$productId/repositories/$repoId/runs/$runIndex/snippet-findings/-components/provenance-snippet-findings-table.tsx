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

import { useQuery } from '@tanstack/react-query';
import { useNavigate, useSearch } from '@tanstack/react-router';
import {
  createColumnHelper,
  ExpandedState,
  getCoreRowModel,
  getExpandedRowModel,
  useReactTable,
} from '@tanstack/react-table';
import { ChevronDown, ChevronUp } from 'lucide-react';
import { useState } from 'react';

import { SnippetFinding, SnippetFindingProvenance } from '@/api';
import { getRunProvenanceSnippetFindingsOptions } from '@/api/@tanstack/react-query.gen';
import { BreakableString } from '@/components/breakable-string';
import { DataTable } from '@/components/data-table/data-table';
import { LoadingIndicator } from '@/components/loading-indicator';
import { Button } from '@/components/ui/button';
import {
  convertToBackendSorting,
  updateColumnSorting,
} from '@/helpers/handle-multisort';
import { toastError } from '@/lib/toast';
import { formatLineNumber } from '@/lib/utils';
import { SnippetFindingSnippetsTable } from './snippet-finding-snippets-table';

const findingColumnHelper = createColumnHelper<SnippetFinding>();
const defaultPageSize = 10;
const snippetFindingsRoutePath =
  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/snippet-findings/';

type ProvenanceSnippetFindingsTableProps = {
  runId: number;
  provenance: SnippetFindingProvenance;
};

export const ProvenanceSnippetFindingsTable = ({
  runId,
  provenance,
}: ProvenanceSnippetFindingsTableProps) => {
  const search = useSearch({ from: snippetFindingsRoutePath });
  const navigate = useNavigate({ from: snippetFindingsRoutePath });
  const findingsPageIndex = search.findingsPage ? search.findingsPage - 1 : 0;
  const findingsPageSize = search.findingsPageSize || defaultPageSize;
  const [expanded, setExpanded] = useState<ExpandedState>({});

  const {
    data: findings,
    isPending,
    isError,
    error,
  } = useQuery({
    ...getRunProvenanceSnippetFindingsOptions({
      path: {
        runId,
        provenanceId: provenance.id,
      },
      query: {
        limit: findingsPageSize,
        offset: findingsPageIndex * findingsPageSize,
        sort: convertToBackendSorting(search.findingsSortBy),
      },
    }),
  });

  const findingColumns = [
    findingColumnHelper.display({
      id: 'details',
      header: 'Details',
      meta: {
        widthPercentage: 8,
      },
      cell: function CellComponent({ row }) {
        return row.getCanExpand() ? (
          <Button
            variant='outline'
            size='sm'
            onClick={() => {
              const isOpening = !row.getIsExpanded();

              setExpanded(isOpening ? { [row.id]: true } : {});
              navigate({
                search: {
                  ...search,
                  snippetsPage: 1,
                },
                replace: true,
              });
            }}
            style={{ cursor: 'pointer' }}
          >
            {row.getIsExpanded() ? (
              <ChevronUp className='h-4 w-4' />
            ) : (
              <ChevronDown className='h-4 w-4' />
            )}
          </Button>
        ) : null;
      },
    }),
    findingColumnHelper.accessor('path', {
      id: 'path',
      header: 'Path',
      cell: ({ row }) => <BreakableString text={row.original.path} />,
      meta: {
        isGrow: true,
      },
    }),
    findingColumnHelper.accessor('startLine', {
      id: 'startLine',
      header: 'Start Line',
      enableSorting: false,
      cell: ({ row }) => formatLineNumber(row.original.startLine),
      meta: {
        widthPercentage: 10,
      },
    }),
    findingColumnHelper.accessor('endLine', {
      id: 'endLine',
      header: 'End Line',
      enableSorting: false,
      cell: ({ row }) => formatLineNumber(row.original.endLine),
      meta: {
        widthPercentage: 10,
      },
    }),
    findingColumnHelper.accessor('snippetCount', {
      id: 'snippetCount',
      header: 'Snippet Count',
      meta: {
        widthPercentage: 14,
      },
    }),
  ];

  const findingsTable = useReactTable({
    data: findings?.data || [],
    columns: findingColumns,
    pageCount: Math.ceil(
      (findings?.pagination.totalCount ?? 0) / findingsPageSize
    ),
    state: {
      pagination: {
        pageIndex: findingsPageIndex,
        pageSize: findingsPageSize,
      },
      sorting: search.findingsSortBy,
      expanded,
    },
    onExpandedChange: setExpanded,
    getCoreRowModel: getCoreRowModel(),
    getExpandedRowModel: getExpandedRowModel(),
    getRowCanExpand: () => true,
    manualPagination: true,
  });

  if (isPending) {
    return <LoadingIndicator />;
  }

  if (isError) {
    toastError('Unable to load data', error);
    return <></>;
  }

  return (
    <DataTable
      table={findingsTable}
      className='[&_tbody_tr:first-child]:border-t'
      renderSubComponent={({ row }) => (
        <SnippetFindingSnippetsTable
          runId={runId}
          snippetFindingId={row.original.id}
        />
      )}
      setCurrentPageOptions={(currentPage) => {
        return {
          to: '.',
          search: {
            ...search,
            findingsPage: currentPage,
          },
        };
      }}
      setPageSizeOptions={(size) => {
        return {
          to: '.',
          search: {
            ...search,
            findingsPage: 1,
            findingsPageSize: size,
          },
        };
      }}
      setSortingOptions={(sortBy) => {
        return {
          to: '.',
          search: {
            ...search,
            findingsSortBy: updateColumnSorting(search.findingsSortBy, sortBy),
          },
        };
      }}
    />
  );
};
