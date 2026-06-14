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
import { useSearch } from '@tanstack/react-router';
import {
  createColumnHelper,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table';

import { SnippetSource } from '@/api';
import { getRunSnippetFindingSnippetsOptions } from '@/api/@tanstack/react-query.gen';
import { BreakableString } from '@/components/breakable-string';
import { CopyToClipboard } from '@/components/copy-to-clipboard';
import { DataTableCards } from '@/components/data-table-cards/data-table-cards';
import { SpdxExpressionBadgeGroup } from '@/components/licenses';
import { LoadingIndicator } from '@/components/loading-indicator';
import {
  convertToBackendSorting,
  updateColumnSorting,
} from '@/helpers/handle-multisort';
import { toastError } from '@/lib/toast';
import { formatLineNumber } from '@/lib/utils';

const snippetColumnHelper = createColumnHelper<SnippetSource>();
const defaultPageSize = 10;
const snippetFindingsRoutePath =
  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/snippet-findings/';

type SnippetFindingSnippetsTableProps = {
  runId: number;
  snippetFindingId: number;
};

const isValidRevision = (revision: string | null | undefined) =>
  revision != null && revision !== '' && revision !== '.';

const SnippetProvenance = ({ snippet }: { snippet: SnippetSource }) => {
  if (snippet.artifactUrl) {
    return (
      <div className='flex min-w-0 items-center'>
        <BreakableString text={snippet.artifactUrl} />
        <CopyToClipboard
          copyText={snippet.artifactUrl}
          className='h-5 px-2 align-middle'
        />
      </div>
    );
  }

  if (snippet.vcsUrl) {
    const text = isValidRevision(snippet.vcsRevision)
      ? `${snippet.vcsUrl} (${snippet.vcsRevision})`
      : snippet.vcsUrl;

    return (
      <div className='flex min-w-0 items-center'>
        <BreakableString text={text} />
        <CopyToClipboard copyText={text} className='h-5 px-2 align-middle' />
      </div>
    );
  }

  return <span className='text-muted-foreground'>N/A</span>;
};

const SnippetSourceCard = ({ snippet }: { snippet: SnippetSource }) => {
  return (
    <div className='flex flex-col gap-2'>
      <div className='flex items-start justify-between gap-4'>
        <div className='min-w-0 basis-2/3 text-left font-semibold'>
          <BreakableString text={snippet.purl} />
          <CopyToClipboard
            copyText={snippet.purl}
            className='h-5 px-2 align-middle'
          />
        </div>
        <div className='flex shrink-0 items-center gap-2'>
          <span className='text-muted-foreground text-sm'>Score:</span>
          <span className='font-semibold'>{snippet.score}</span>
        </div>
      </div>

      <div className='flex items-start justify-between gap-4 text-sm'>
        <div className='flex min-w-0 gap-2'>
          <div className='text-muted-foreground shrink-0'>Location:</div>
          <div className='wrap-break-word'>
            {snippet.path} ({formatLineNumber(snippet.startLine)}-
            {formatLineNumber(snippet.endLine)})
          </div>
        </div>
        <div className='flex shrink-0 gap-2'>
          <div className='text-muted-foreground shrink-0'>License:</div>
          <SpdxExpressionBadgeGroup expression={snippet.license} />
        </div>
      </div>

      <div className='flex items-center gap-2 text-sm'>
        <div className='text-muted-foreground shrink-0'>Provenance:</div>
        <SnippetProvenance snippet={snippet} />
      </div>
    </div>
  );
};

export const SnippetFindingSnippetsTable = ({
  runId,
  snippetFindingId,
}: SnippetFindingSnippetsTableProps) => {
  const search = useSearch({ from: snippetFindingsRoutePath });
  const snippetsPageIndex = search.snippetsPage ? search.snippetsPage - 1 : 0;
  const snippetsPageSize = search.snippetsPageSize || defaultPageSize;

  const {
    data: snippets,
    isPending,
    isError,
    error,
  } = useQuery({
    ...getRunSnippetFindingSnippetsOptions({
      path: {
        runId,
        snippetFindingId,
      },
      query: {
        limit: snippetsPageSize,
        offset: snippetsPageIndex * snippetsPageSize,
        sort: convertToBackendSorting(search.snippetsSortBy),
      },
    }),
  });

  const snippetColumns = [
    snippetColumnHelper.display({
      id: 'card',
      cell: ({ row }) => <SnippetSourceCard snippet={row.original} />,
    }),
    snippetColumnHelper.accessor('purl', {
      id: 'purl',
      header: 'PURL',
    }),
    snippetColumnHelper.accessor('license', {
      id: 'license',
      header: 'License',
    }),
    snippetColumnHelper.accessor('score', {
      id: 'score',
      header: 'Score',
    }),
  ];

  const snippetsTable = useReactTable({
    data: snippets?.data || [],
    columns: snippetColumns,
    pageCount: Math.ceil(
      (snippets?.pagination.totalCount ?? 0) / snippetsPageSize
    ),
    state: {
      pagination: {
        pageIndex: snippetsPageIndex,
        pageSize: snippetsPageSize,
      },
      sorting: search.snippetsSortBy,
      columnVisibility: {
        purl: false,
        license: false,
        score: false,
      },
    },
    getCoreRowModel: getCoreRowModel(),
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
    <DataTableCards
      table={snippetsTable}
      className='space-y-0 [&_tbody_tr:first-child]:border-t [&>div:first-child]:py-0'
      setCurrentPageOptions={(currentPage) => {
        return {
          to: '.',
          search: {
            ...search,
            snippetsPage: currentPage,
          },
        };
      }}
      setPageSizeOptions={(size) => {
        return {
          to: '.',
          search: {
            ...search,
            snippetsPage: 1,
            snippetsPageSize: size,
          },
        };
      }}
      setSortingOptions={(sortBy) => {
        return {
          to: '.',
          search: {
            ...search,
            snippetsSortBy: updateColumnSorting(search.snippetsSortBy, sortBy),
          },
        };
      }}
    />
  );
};
