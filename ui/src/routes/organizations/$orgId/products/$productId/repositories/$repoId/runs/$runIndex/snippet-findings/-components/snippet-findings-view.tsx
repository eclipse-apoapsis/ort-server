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

import { useSuspenseQuery } from '@tanstack/react-query';
import { useNavigate, useParams, useSearch } from '@tanstack/react-router';
import {
  createColumnHelper,
  ExpandedState,
  getCoreRowModel,
  getExpandedRowModel,
  useReactTable,
} from '@tanstack/react-table';
import { ChevronDown, ChevronUp } from 'lucide-react';
import { useState } from 'react';

import { SnippetFindingProvenance } from '@/api';
import {
  getRepositoryRunOptions,
  getRunSnippetFindingProvenancesOptions,
} from '@/api/@tanstack/react-query.gen';
import { BreakableString } from '@/components/breakable-string';
import { CopyToClipboard } from '@/components/copy-to-clipboard';
import { DataTableCards } from '@/components/data-table-cards/data-table-cards';
import { LoadingIndicator } from '@/components/loading-indicator';
import { Sha1Component } from '@/components/sha1-component';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import {
  convertToBackendSorting,
  updateColumnSorting,
} from '@/helpers/handle-multisort';
import { identifierToString } from '@/helpers/identifier-conversion';
import { ACTION_COLUMN_SIZE } from '@/lib/constants';
import { toastError } from '@/lib/toast';
import { ProvenanceSnippetFindingsTable } from './provenance-snippet-findings-table';

const provenanceColumnHelper = createColumnHelper<SnippetFindingProvenance>();
const defaultPageSize = 10;
const snippetFindingsRoutePath =
  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/snippet-findings/';

const isValidRevision = (
  revision: string | null | undefined
): revision is string =>
  revision != null && revision !== '' && revision !== '.';

const ProvenanceDetails = ({
  provenance,
}: {
  provenance: SnippetFindingProvenance;
}) => {
  if (provenance.artifactUrl) {
    return (
      <div className='flex min-w-0 items-center gap-1'>
        <a
          href={provenance.artifactUrl}
          target='_blank'
          rel='noopener noreferrer'
          className='text-blue-400 hover:underline'
        >
          <BreakableString text={provenance.artifactUrl} />
        </a>
      </div>
    );
  }

  if (provenance.vcsUrl) {
    return (
      <div className='flex min-w-0 items-center gap-1'>
        <a
          href={provenance.vcsUrl}
          target='_blank'
          rel='noopener noreferrer'
          className='text-blue-400 hover:underline'
        >
          <BreakableString text={provenance.vcsUrl} />
        </a>
        {isValidRevision(provenance.vcsRevision) && (
          <Sha1Component sha1={provenance.vcsRevision} />
        )}
      </div>
    );
  }

  return <span className='text-muted-foreground'>N/A</span>;
};

const SnippetFindingProvenanceCard = ({
  provenance,
}: {
  provenance: SnippetFindingProvenance;
}) => {
  const identifier = identifierToString(provenance.identifier);

  return (
    <div className='flex flex-col gap-2'>
      <div className='flex items-start justify-between gap-4'>
        <div className='min-w-0 basis-2/3 text-left font-semibold'>
          <BreakableString text={identifier} />
          <CopyToClipboard
            copyText={identifier}
            className='h-5 px-2 align-middle'
          />
        </div>
        <div className='flex shrink-0 items-center gap-2'>
          <Badge variant='small'>{provenance.provenanceType}</Badge>
          <div className='text-muted-foreground ml-4 text-sm'>
            {provenance.snippetFindingCount} finding
            {provenance.snippetFindingCount === 1 ? '' : 's'}
          </div>
        </div>
      </div>

      <div className='flex items-center gap-2 text-sm'>
        <div className='text-muted-foreground shrink-0'>Provenance:</div>
        <ProvenanceDetails provenance={provenance} />
      </div>
    </div>
  );
};

export const SnippetFindingsView = () => {
  const params = useParams({ from: snippetFindingsRoutePath });
  const search = useSearch({ from: snippetFindingsRoutePath });
  const navigate = useNavigate({ from: snippetFindingsRoutePath });
  const pageIndex = search.page ? search.page - 1 : 0;
  const pageSize = search.pageSize ? search.pageSize : defaultPageSize;
  const [expanded, setExpanded] = useState<ExpandedState>({});

  const { data: ortRun } = useSuspenseQuery({
    ...getRepositoryRunOptions({
      path: {
        repositoryId: Number.parseInt(params.repoId),
        ortRunIndex: Number.parseInt(params.runIndex),
      },
    }),
  });

  const { data: totalProvenances } = useSuspenseQuery({
    ...getRunSnippetFindingProvenancesOptions({
      path: { runId: ortRun.id },
      query: { limit: 1 },
    }),
  });

  const {
    data: provenances,
    isPending,
    isError,
    error,
  } = useSuspenseQuery({
    ...getRunSnippetFindingProvenancesOptions({
      path: { runId: ortRun.id },
      query: {
        limit: totalProvenances.pagination.totalCount || 1,
        sort: convertToBackendSorting(search.sortBy),
      },
    }),
  });

  const provenancesWithFindings = provenances.data.filter(
    (provenance) => provenance.snippetFindingCount > 0
  );
  const pagedProvenances = provenancesWithFindings.slice(
    pageIndex * pageSize,
    (pageIndex + 1) * pageSize
  );

  const columns = [
    provenanceColumnHelper.display({
      id: 'details',
      header: 'Details',
      size: ACTION_COLUMN_SIZE,
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
                  findingsPage: 1,
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
    provenanceColumnHelper.display({
      id: 'card',
      cell: ({ row }) => (
        <SnippetFindingProvenanceCard provenance={row.original} />
      ),
    }),
    provenanceColumnHelper.accessor((row) => row.identifier.type, {
      id: 'type',
      header: 'Type',
    }),
    provenanceColumnHelper.accessor((row) => row.identifier.namespace, {
      id: 'namespace',
      header: 'Namespace',
    }),
    provenanceColumnHelper.accessor((row) => row.identifier.name, {
      id: 'name',
      header: 'Name',
    }),
    provenanceColumnHelper.accessor((row) => row.identifier.version, {
      id: 'version',
      header: 'Version',
    }),
  ];

  const table = useReactTable({
    data: pagedProvenances,
    columns,
    pageCount: Math.ceil(provenancesWithFindings.length / pageSize),
    state: {
      pagination: {
        pageIndex,
        pageSize,
      },
      sorting: search.sortBy,
      expanded,
      columnVisibility: {
        type: false,
        namespace: false,
        name: false,
        version: false,
      },
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
    return;
  }

  const scannerWasIncludedInRun = ortRun.jobs.scanner != null;
  const noResultsContent = !scannerWasIncludedInRun ? (
    <div className='text-muted-foreground text-sm'>
      No detected snippets are available because the scanner job was not enabled
      for this run.
    </div>
  ) : provenancesWithFindings.length === 0 ? (
    <div className='text-muted-foreground text-sm'>
      No detected snippets were found for this run, or no snippet scanner was
      used.
    </div>
  ) : undefined;

  return (
    <Card className='h-fit'>
      <CardHeader>
        <CardTitle>
          Detected Snippets ({provenancesWithFindings.length} package
          provenances with findings)
        </CardTitle>
        <CardDescription>
          Snippet findings detected in package provenances by the configured
          scanners.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <DataTableCards
          table={table}
          noResultsContent={noResultsContent}
          renderSubComponent={({ row }) => (
            <ProvenanceSnippetFindingsTable
              runId={ortRun.id}
              provenance={row.original}
            />
          )}
          setCurrentPageOptions={(currentPage) => {
            return {
              to: '.',
              search: { ...search, page: currentPage },
            };
          }}
          setPageSizeOptions={(size) => {
            return {
              to: '.',
              search: { ...search, page: 1, pageSize: size },
            };
          }}
          setSortingOptions={(sortBy) => {
            return {
              to: '.',
              search: {
                ...search,
                sortBy: updateColumnSorting(search.sortBy, sortBy),
              },
            };
          }}
        />
      </CardContent>
    </Card>
  );
};
