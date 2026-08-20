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

import { useQuery, useSuspenseQuery } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';
import { ExpandedState } from '@tanstack/react-table';
import {
  getCoreRowModel,
  getExpandedRowModel,
  legacyCreateColumnHelper,
  LegacyRow,
  useLegacyTable,
} from '@tanstack/react-table/legacy';
import { ChevronDown, ChevronUp } from 'lucide-react';
import { useMemo, useState } from 'react';
import z from 'zod';

import { Project } from '@/api';
import {
  getRepositoryRunOptions,
  getRunProjectLicensesOptions,
  getRunProjectsOptions,
} from '@/api/@tanstack/react-query.gen';
import { BreakableString } from '@/components/breakable-string';
import { DataTableCards } from '@/components/data-table-cards/data-table-cards';
import { MarkItems } from '@/components/data-table/mark-items';
import {
  LicensesAccordion,
  SpdxExpressionBadgeGroup,
} from '@/components/licenses';
import { LoadingIndicator } from '@/components/loading-indicator';
import { RenderProperty } from '@/components/render-property';
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
  EMPTY_SORTING_STATE,
  updateColumnSorting,
} from '@/helpers/handle-multisort';
import { identifierToString } from '@/helpers/identifier-conversion';
import { ACTION_COLUMN_SIZE } from '@/lib/constants';
import { routePrefetchStaleTime } from '@/lib/query-client';
import { toastError } from '@/lib/toast';
import { getRepositoryTypeLabel } from '@/lib/types';
import {
  declaredLicenseSearchParameterSchema,
  definitionFilePathSearchParameterSchema,
  markedSearchParameterSchema,
  paginationSearchParameterSchema,
  projectIdentifierSearchParameterSchema,
  sortingSearchParameterSchema,
} from '@/schemas';

const defaultPageSize = 10;

const supportedSortColumns = new Set([
  'identifier',
  'declaredLicense',
  'definitionFilePath',
]);

const columnHelper = legacyCreateColumnHelper<Project>();

const LicenseList = ({ licenses }: { licenses: string[] }) => (
  <div className='flex flex-wrap gap-1'>
    {licenses.map((license) => (
      <SpdxExpressionBadgeGroup key={license} expression={license} />
    ))}
  </div>
);

// Component to render a single project card in the list.
const ProjectCard = ({ project }: { project: Project }) => {
  const declaredLicenses = [
    ...(project.processedDeclaredLicense.spdxExpression
      ? [project.processedDeclaredLicense.spdxExpression]
      : []),
    ...project.processedDeclaredLicense.unmappedLicenses,
  ];

  return (
    <div className='flex flex-col gap-1'>
      <div className='flex items-center justify-between'>
        <div className='font-semibold'>
          <BreakableString text={identifierToString(project.identifier)} />
        </div>
        {project.homepageUrl ? (
          <a
            href={project.homepageUrl}
            target='_blank'
            rel='noopener noreferrer'
            className='text-blue-400 hover:underline'
          >
            {project.homepageUrl}
          </a>
        ) : (
          <div className='text-muted-foreground italic'>No homepage</div>
        )}
      </div>
      {project.definitionFilePath ? (
        <div className='flex gap-2 text-sm'>
          <div className='text-muted-foreground'>Definition File:</div>
          <div className='wrap-break-word'>{project.definitionFilePath}</div>
        </div>
      ) : (
        <div className='text-muted-foreground italic'>No definition file</div>
      )}
      {declaredLicenses.length > 0 ? (
        <div className='flex gap-2 text-sm'>
          <div className='font-semibold'>Declared License: </div>
          <LicenseList licenses={declaredLicenses} />
        </div>
      ) : (
        <div className='text-muted-foreground italic'>No declared license</div>
      )}
    </div>
  );
};

const renderSubComponent = ({
  row,
  runId,
}: {
  row: LegacyRow<Project>;
  runId: number;
}) => {
  const project = row.original;

  return (
    <div className='flex flex-col gap-4'>
      <RenderProperty label='Authors' value={project.authors} />
      <RenderProperty
        label='Description'
        value={project.description}
        type='textblock'
      />
      <LicensesAccordion
        runId={runId}
        identifier={project.identifier}
        declaredLicenses={[
          ...(project.processedDeclaredLicense.spdxExpression
            ? [project.processedDeclaredLicense.spdxExpression]
            : []),
          ...project.processedDeclaredLicense.unmappedLicenses,
        ]}
      />
      <RenderProperty label='Homepage' value={project.homepageUrl} type='url' />
      <RenderProperty label='CPE' value={project.cpe} />
      <div>
        <div className='font-semibold'>Processed Declared License</div>
        <div className='ml-2'>
          <div className='space-y-1'>
            <div className='text-sm font-semibold'>SPDX expression</div>
            {project.processedDeclaredLicense.spdxExpression ? (
              <SpdxExpressionBadgeGroup
                expression={project.processedDeclaredLicense.spdxExpression}
              />
            ) : (
              <div className='text-muted-foreground italic'>No value</div>
            )}
          </div>
          <RenderProperty
            label='Mapped licenses'
            value={project.processedDeclaredLicense.mappedLicenses}
            type='keyvalue'
          />
          <div className='space-y-1'>
            <div className='text-sm font-semibold'>Unmapped licenses</div>
            {project.processedDeclaredLicense.unmappedLicenses.length > 0 ? (
              <LicenseList
                licenses={project.processedDeclaredLicense.unmappedLicenses}
              />
            ) : (
              <div className='text-muted-foreground italic'>No value</div>
            )}
          </div>
        </div>
      </div>

      <div>
        <div className='font-semibold'>
          {getRepositoryTypeLabel(project.vcsProcessed.type)} Repository
        </div>
        <div className='ml-2'>
          <RenderProperty
            label='URL'
            value={project.vcsProcessed.url}
            type='url'
          />
          <RenderProperty
            label='Revision'
            value={project.vcsProcessed.revision}
          />
          <RenderProperty label='Path' value={project.vcsProcessed.path} />
        </div>
      </div>
      <RenderProperty label='Scopes' value={project.scopeNames} type='array' />
    </div>
  );
};

const ProjectsComponent = () => {
  const params = Route.useParams();
  const search = Route.useSearch();
  const navigate = Route.useNavigate();

  // Memoize the search parameters to prevent unnecessary re-rendering

  const pageIndex = useMemo(
    () => (search.page ? search.page - 1 : 0),
    [search.page]
  );

  const pageSize = useMemo(
    () => (search.pageSize ? search.pageSize : defaultPageSize),
    [search.pageSize]
  );

  const projectIdentifier = useMemo(
    () => (search.projectId ? search.projectId : undefined),
    [search.projectId]
  );

  const definitionFilePath = useMemo(
    () => (search.definitionFilePath ? search.definitionFilePath : undefined),
    [search.definitionFilePath]
  );

  const declaredLicense = useMemo(
    () => (search.declaredLicense ? search.declaredLicense : undefined),
    [search.declaredLicense]
  );

  const columnFilters = useMemo(() => {
    const filters = [];

    if (projectIdentifier) {
      filters.push({ id: 'identifier', value: projectIdentifier });
    }
    if (definitionFilePath) {
      filters.push({ id: 'definitionFilePath', value: definitionFilePath });
    }
    if (declaredLicense) {
      filters.push({ id: 'declaredLicense', value: declaredLicense });
    }
    return filters;
  }, [projectIdentifier, definitionFilePath, declaredLicense]);

  const sortBy = useMemo(
    () =>
      search.sortBy?.filter((sort) => supportedSortColumns.has(sort.id)) ??
      EMPTY_SORTING_STATE,
    [search.sortBy]
  );

  const { data: ortRun } = useSuspenseQuery({
    ...getRepositoryRunOptions({
      path: {
        repositoryId: Number.parseInt(params.repoId),
        ortRunIndex: Number.parseInt(params.runIndex),
      },
    }),
  });

  const {
    data: totalProjects,
    isPending: totalIsPending,
    isError: totalIsError,
    error: totalError,
  } = useQuery({
    ...getRunProjectsOptions({
      path: { runId: ortRun.id },
      query: { limit: 1 },
    }),
  });

  const {
    data: declaredLicenseOptions,
    isPending: licensesIsPending,
    isError: licensesIsError,
    error: licensesError,
  } = useQuery({
    ...getRunProjectLicensesOptions({
      path: { runId: ortRun.id },
    }),
    staleTime: routePrefetchStaleTime,
  });

  const {
    data: projects,
    isPending,
    isError,
    error,
  } = useQuery({
    ...getRunProjectsOptions({
      path: { runId: ortRun.id },
      query: {
        limit: pageSize,
        offset: pageIndex * pageSize,
        sort: convertToBackendSorting(sortBy),
        identifier: projectIdentifier,
        declaredLicense: declaredLicense?.join(','),
        definitionFilePath,
      },
    }),
    staleTime: routePrefetchStaleTime,
  });

  const columns = columnHelper.columns([
    columnHelper.display({
      id: 'details',
      header: 'Details',
      size: ACTION_COLUMN_SIZE,
      cell: function CellComponent({ row }) {
        return row.getCanExpand() ? (
          <div className='flex items-center gap-1'>
            <Button
              variant='outline'
              size='sm'
              {...{
                onClick: row.getToggleExpandedHandler(),
                style: { cursor: 'pointer' },
              }}
            >
              {row.getIsExpanded() ? (
                <ChevronUp className='h-4 w-4' />
              ) : (
                <ChevronDown className='h-4 w-4' />
              )}
            </Button>
            <MarkItems
              row={row}
              setMarked={(marked) => {
                return {
                  to: Route.to,
                  search: {
                    ...search,
                    // If no items are marked for inspection, remove the "marked" parameter
                    // from search parameters.
                    marked: marked === '' ? undefined : marked,
                  },
                };
              }}
            />
          </div>
        ) : (
          'No info'
        );
      },
    }),
    columnHelper.display({
      id: 'card',
      cell: ({ row }) => <ProjectCard project={row.original} />,
    }),
    columnHelper.accessor(
      (project) => {
        return identifierToString(project.identifier);
      },
      {
        id: 'identifier',
        header: 'Project ID',
        meta: {
          filter: {
            filterVariant: 'text',
            setFilterValue: (value: string | undefined) => {
              navigate({
                search: { ...search, page: 1, projectId: value },
              });
            },
          },
        },
      }
    ),
    columnHelper.accessor('processedDeclaredLicense', {
      id: 'declaredLicense',
      header: 'Declared License',
      meta: {
        filter: {
          filterVariant: 'select',
          selectOptions: [
            ...(declaredLicenseOptions?.processedDeclaredLicenses ?? []).map(
              (license) => ({
                label: license,
                value: license,
                group: 'Processed licenses',
              })
            ),
            ...(declaredLicenseOptions?.unmappedDeclaredLicenses ?? []).map(
              (license) => ({
                label: license,
                value: license,
                group: 'Unmapped licenses',
              })
            ),
          ],
          setSelected: (licenses: string[]) => {
            navigate({
              search: {
                ...search,
                page: 1,
                declaredLicense: licenses.length === 0 ? undefined : licenses,
              },
            });
          },
          align: 'end',
        },
      },
    }),
    columnHelper.accessor('definitionFilePath', {
      id: 'definitionFilePath',
      header: 'Definition File',
      meta: {
        filter: {
          filterVariant: 'text',
          setFilterValue: (value: string | undefined) => {
            navigate({
              search: { ...search, page: 1, definitionFilePath: value },
            });
          },
        },
      },
    }),
  ]);

  const [expanded, setExpanded] = useState<ExpandedState>(
    search.marked ? { [search.marked]: true } : {}
  );

  const table = useLegacyTable({
    data: projects?.data || [],
    columns,
    pageCount: Math.ceil((projects?.pagination.totalCount ?? 0) / pageSize),
    state: {
      pagination: {
        pageIndex,
        pageSize,
      },
      columnFilters,
      columnVisibility: {
        identifier: false,
        declaredLicense: false,
        definitionFilePath: false,
      },
      sorting: sortBy,
      expanded: expanded,
    },
    onExpandedChange: setExpanded,
    getCoreRowModel: getCoreRowModel(),
    getExpandedRowModel: getExpandedRowModel(),
    getRowCanExpand: () => true,
    manualFiltering: true,
    manualPagination: true,
    manualSorting: true,
    getRowId: (row) => identifierToString(row.identifier),
  });

  if (isPending || totalIsPending || licensesIsPending) {
    return <LoadingIndicator />;
  }

  if (isError || totalIsError || licensesIsError) {
    toastError('Unable to load data', error || totalError || licensesError);
    return;
  }

  const filtersInUse = table.getState().columnFilters.length > 0;
  const matching = `, ${projects.pagination.totalCount} matching filters`;

  return (
    <Card className='h-fit'>
      <CardHeader>
        <CardTitle>
          Projects ({totalProjects.pagination.totalCount} in total
          {filtersInUse && matching})
        </CardTitle>
        <CardDescription>This view shows all projects.</CardDescription>
      </CardHeader>
      <CardContent>
        <DataTableCards
          table={table}
          renderSubComponent={({ row }) =>
            renderSubComponent({ row, runId: ortRun.id })
          }
          setCurrentPageOptions={(currentPage) => {
            return {
              to: Route.to,
              search: { ...search, page: currentPage },
            };
          }}
          setPageSizeOptions={(size) => {
            return {
              to: Route.to,
              search: { ...search, page: 1, pageSize: size },
            };
          }}
          setSortingOptions={(sortBy) => {
            return {
              to: Route.to,
              search: {
                ...search,
                sortBy: updateColumnSorting(
                  search.sortBy?.filter((sort) =>
                    supportedSortColumns.has(sort.id)
                  ),
                  sortBy
                ),
              },
            };
          }}
        />
      </CardContent>
    </Card>
  );
};

export const Route = createFileRoute(
  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/projects/'
)({
  validateSearch: z.object({
    ...declaredLicenseSearchParameterSchema.shape,
    ...definitionFilePathSearchParameterSchema.shape,
    ...paginationSearchParameterSchema.shape,
    ...projectIdentifierSearchParameterSchema.shape,
    ...sortingSearchParameterSchema.shape,
    ...markedSearchParameterSchema.shape,
  }),
  loader: async ({ context: { queryClient }, params }) => {
    const ortRun = await queryClient.ensureQueryData({
      ...getRepositoryRunOptions({
        path: {
          repositoryId: Number.parseInt(params.repoId),
          ortRunIndex: Number.parseInt(params.runIndex),
        },
      }),
    });

    await Promise.all([
      queryClient.prefetchQuery({
        ...getRunProjectsOptions({
          path: { runId: ortRun.id },
          query: { limit: defaultPageSize, offset: 0 },
        }),
        staleTime: routePrefetchStaleTime,
      }),
      queryClient.prefetchQuery({
        ...getRunProjectLicensesOptions({
          path: { runId: ortRun.id },
        }),
        staleTime: routePrefetchStaleTime,
      }),
    ]);
  },
  component: ProjectsComponent,
  pendingComponent: LoadingIndicator,
});
