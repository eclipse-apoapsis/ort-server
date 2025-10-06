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
import {
  createColumnHelper,
  ExpandedState,
  getCoreRowModel,
  getExpandedRowModel,
  getFilteredRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  Row,
  useReactTable,
} from '@tanstack/react-table';
import { ChevronDown, ChevronUp } from 'lucide-react';
import { useMemo, useState } from 'react';
import z from 'zod';

import { Project } from '@/api';
import {
  getRepositoryRunOptions,
  getRunProjectsOptions,
} from '@/api/@tanstack/react-query.gen';
import { BreakableString } from '@/components/breakable-string';
import { DataTableCards } from '@/components/data-table-cards/data-table-cards';
import { MarkItems } from '@/components/data-table/mark-items';
import { LoadingIndicator } from '@/components/loading-indicator';
import { RenderProperty } from '@/components/render-property';
import { ToastError } from '@/components/toast-error';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { updateColumnSorting } from '@/helpers/handle-multisort';
import { identifierToString } from '@/helpers/identifier-conversion';
import { ACTION_COLUMN_SIZE, ALL_ITEMS } from '@/lib/constants';
import { toast } from '@/lib/toast';
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

const columnHelper = createColumnHelper<Project>();

// Component to render a single project card in the list.
const ProjectCard = ({ project }: { project: Project }) => {
  const declaredLicenses = [
    ...(project.processedDeclaredLicense.spdxExpression
      ? [project.processedDeclaredLicense.spdxExpression]
      : []),
    ...(project.processedDeclaredLicense.unmappedLicenses ?? []),
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
          <div className='break-words'>{project.definitionFilePath}</div>
        </div>
      ) : (
        <div className='text-muted-foreground italic'>No definition file</div>
      )}
      {declaredLicenses.length > 0 ? (
        <div className='flex gap-2 text-sm'>
          <div className='font-semibold'>Declared License: </div>
          <div className='text-muted-foreground break-words'>
            {declaredLicenses.join(', ')}
          </div>
        </div>
      ) : (
        <div className='text-muted-foreground italic'>No declared license</div>
      )}
    </div>
  );
};

const renderSubComponent = ({ row }: { row: Row<Project> }) => {
  const project = row.original;

  return (
    <div className='flex flex-col gap-4'>
      <RenderProperty label='Authors' value={project.authors} />
      <RenderProperty
        label='Description'
        value={project.description}
        type='textblock'
      />
      <RenderProperty label='Homepage' value={project.homepageUrl} type='url' />
      <RenderProperty label='CPE' value={project.cpe} />
      <div>
        <div className='font-semibold'>Processed Declared License</div>
        <div className='ml-2'>
          <RenderProperty
            label='SPDX expression'
            value={project.processedDeclaredLicense.spdxExpression}
          />
          <RenderProperty
            label='Mapped licenses'
            value={project.processedDeclaredLicense.mappedLicenses}
            type='keyvalue'
          />
          <RenderProperty
            label='Unmapped licenses'
            value={project.processedDeclaredLicense.unmappedLicenses}
            type='array'
          />
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
      filters.push({ id: 'projectIdentifier', value: projectIdentifier });
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
    () => (search.sortBy ? search.sortBy : undefined),
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
    data: projects,
    isPending,
    isError,
    error,
  } = useQuery({
    ...getRunProjectsOptions({
      path: { runId: ortRun.id },
      query: { limit: ALL_ITEMS },
    }),
  });

  // Use memoizing to ensure that the filter options (decuplidated SPDX expressions
  // and unmapped licenses) are only calculated when the projects data changes.
  // "individualLicenses" will contain all SDPX expressions as first elements (sorted),
  // followed by all unmapped licenses (sorted).
  const individualLicenses = useMemo(() => {
    const spdxExpressions = new Set<string>();
    const unmappedLicenses = new Set<string>();

    projects?.data.forEach((project) => {
      const spdxExpression = project.processedDeclaredLicense.spdxExpression;
      if (spdxExpression) {
        spdxExpressions.add(spdxExpression);
      }
      project.processedDeclaredLicense.unmappedLicenses.forEach((license) => {
        unmappedLicenses.add(license);
      });
    });

    return [
      ...Array.from(spdxExpressions).sort(),
      ...Array.from(unmappedLicenses).sort(),
    ];
  }, [projects]);

  const columns = [
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
        id: 'projectIdentifier',
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
      filterFn: (row, _columnId, filterValue): boolean => {
        return (
          filterValue.includes(
            row.original.processedDeclaredLicense.spdxExpression
          ) ||
          row.original.processedDeclaredLicense.unmappedLicenses.some(
            (license) => filterValue.includes(license)
          )
        );
      },
      meta: {
        filter: {
          filterVariant: 'select',
          selectOptions: individualLicenses.map((license) => ({
            label: license,
            value: license,
          })),
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
  ];

  const [expanded, setExpanded] = useState<ExpandedState>(
    search.marked ? { [search.marked]: true } : {}
  );

  const table = useReactTable({
    data: projects?.data || [],
    columns,
    state: {
      pagination: {
        pageIndex,
        pageSize,
      },
      columnFilters,
      columnVisibility: {
        projectIdentifier: false,
        declaredLicense: false,
        definitionFilePath: false,
      },
      sorting: sortBy,
      expanded: expanded,
    },
    onExpandedChange: setExpanded,
    getCoreRowModel: getCoreRowModel(),
    getExpandedRowModel: getExpandedRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getRowCanExpand: () => true,
  });

  if (isPending) {
    return <LoadingIndicator />;
  }

  if (isError) {
    toast.error('Unable to load data', {
      description: <ToastError error={error} />,
      duration: Infinity,
      cancel: {
        label: 'Dismiss',
        onClick: () => {},
      },
    });
    return;
  }

  const filtersInUse = table.getState().columnFilters.length > 0;
  const matching = `, ${table.getPrePaginationRowModel().rows.length} matching filters`;

  return (
    <Card className='h-fit'>
      <CardHeader>
        <CardTitle>
          Projects ({projects.pagination.totalCount} in total
          {filtersInUse && matching})
        </CardTitle>
        <CardDescription>This view shows all projects.</CardDescription>
      </CardHeader>
      <CardContent>
        <DataTableCards
          table={table}
          renderSubComponent={renderSubComponent}
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
                sortBy: updateColumnSorting(search.sortBy, sortBy),
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
    await queryClient.prefetchQuery({
      ...getRepositoryRunOptions({
        path: {
          repositoryId: Number.parseInt(params.repoId),
          ortRunIndex: Number.parseInt(params.runIndex),
        },
      }),
    });
  },
  component: ProjectsComponent,
  pendingComponent: LoadingIndicator,
});
