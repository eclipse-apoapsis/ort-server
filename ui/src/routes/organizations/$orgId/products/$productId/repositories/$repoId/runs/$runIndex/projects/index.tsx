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

import { TooltipContent, TooltipTrigger } from '@radix-ui/react-tooltip';
import { createFileRoute } from '@tanstack/react-router';
import {
  createColumnHelper,
  getCoreRowModel,
  getExpandedRowModel,
  getFilteredRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  Row,
  useReactTable,
} from '@tanstack/react-table';
import { ChevronDown, ChevronUp } from 'lucide-react';
import { useMemo } from 'react';

import { useRunsServiceGetApiV1RunsByRunIdProjects } from '@/api/queries';
import { prefetchUseRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRunsByOrtRunIndex } from '@/api/queries/prefetch';
import { useRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRunsByOrtRunIndexSuspense } from '@/api/queries/suspense';
import { Project } from '@/api/requests';
import { DataTable } from '@/components/data-table/data-table';
import { LoadingIndicator } from '@/components/loading-indicator';
import { ToastError } from '@/components/toast-error';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Tooltip } from '@/components/ui/tooltip';
import { updateColumnSorting } from '@/helpers/handle-multisort';
import { identifierToString } from '@/helpers/identifier-to-string';
import { ALL_ITEMS } from '@/lib/constants';
import { toast } from '@/lib/toast';
import {
  declaredLicenseSearchParameterSchema,
  definitionFilePathSearchParameterSchema,
  paginationSearchParameterSchema,
  projectIdentifierSearchParameterSchema,
  sortingSearchParameterSchema,
} from '@/schemas';

const defaultPageSize = 10;

const columnHelper = createColumnHelper<Project>();

const renderSubComponent = ({ row }: { row: Row<Project> }) => {
  const project = row.original;

  return (
    <div className='flex flex-col gap-4'>
      {project.authors.length > 0 && (
        <div className='flex gap-2'>
          <div className='font-semibold'>Authors:</div>
          <div>{project.authors}</div>
        </div>
      )}
      {project.description && (
        <div className='flex gap-2'>
          <div className='font-semibold'>Description:</div>
          <div>{project.description}</div>
        </div>
      )}
      {project.homepageUrl && (
        <div className='flex gap-2'>
          <div className='font-semibold'>Homepage:</div>
          <div>{project.homepageUrl}</div>
        </div>
      )}
      <div>
        <div className='font-semibold'>Repository</div>
        <div className='ml-2'>
          {project.vcsProcessed.url && (
            <div className='flex gap-2'>
              <div className='font-semibold'>URL:</div>
              <a
                href={project.vcsProcessed.url}
                target='_blank'
                rel='noopener noreferrer'
                className='text-blue-400 hover:underline'
              >
                {project.vcsProcessed.url}
              </a>
            </div>
          )}
          {project.vcsProcessed.type && (
            <div className='flex gap-2'>
              <div className='font-semibold'>Type:</div>
              <div>{project.vcsProcessed.type}</div>
            </div>
          )}
          {project.vcsProcessed.revision && (
            <div className='flex gap-2'>
              <div className='font-semibold'>Revision:</div>
              <div>{project.vcsProcessed.revision}</div>
            </div>
          )}
          {project.vcsProcessed.path && (
            <div className='flex gap-2'>
              <div className='font-semibold'>Path:</div>
              <div>{project.vcsProcessed.path} </div>
            </div>
          )}
        </div>
      </div>
      {project.scopeNames.length > 0 && (
        <div className='flex flex-col'>
          <div className='font-semibold'>Scopes</div>
          <div>
            {project.scopeNames.sort().map((scope) => (
              <div className='text-muted-foreground ml-2' key={scope}>
                {scope}
              </div>
            ))}
          </div>
        </div>
      )}
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

  const { data: ortRun } =
    useRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRunsByOrtRunIndexSuspense(
      {
        repositoryId: Number.parseInt(params.repoId),
        ortRunIndex: Number.parseInt(params.runIndex),
      }
    );

  const {
    data: projects,
    isPending,
    isError,
    error,
  } = useRunsServiceGetApiV1RunsByRunIdProjects({
    runId: ortRun.id,
    limit: ALL_ITEMS,
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
      id: 'moreInfo',
      header: 'Details',
      size: 50,
      cell: function CellComponent({ row }) {
        return row.getCanExpand() ? (
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
        ) : (
          'No info'
        );
      },
      enableSorting: false,
      enableColumnFilter: false,
    }),
    columnHelper.accessor(
      (project) => {
        return identifierToString(project.identifier);
      },
      {
        id: 'projectIdentifier',
        header: 'Project ID',
        cell: ({ getValue }) => {
          return <div className='font-semibold'>{getValue()}</div>;
        },
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
    columnHelper.accessor('definitionFilePath', {
      header: 'Definition File',
      cell: ({ getValue }) => {
        return <div>{getValue()}</div>;
      },
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
    columnHelper.accessor('processedDeclaredLicense', {
      id: 'declaredLicense',
      header: 'Declared License',
      cell: ({ getValue }) => {
        return (
          <div>
            {getValue().spdxExpression && (
              <div>{getValue().spdxExpression}</div>
            )}
            {getValue().unmappedLicenses.length > 0 && (
              <Tooltip>
                <TooltipTrigger>
                  <div className='italic'>
                    {getValue().unmappedLicenses.join(', ')}
                  </div>
                </TooltipTrigger>
                <TooltipContent>Unmapped licenses</TooltipContent>
              </Tooltip>
            )}
          </div>
        );
      },
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
  ];

  const table = useReactTable({
    data: projects?.data || [],
    columns,
    state: {
      pagination: {
        pageIndex,
        pageSize,
      },
      columnFilters,
      sorting: sortBy,
    },
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
        <DataTable
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
  validateSearch: declaredLicenseSearchParameterSchema
    .merge(definitionFilePathSearchParameterSchema)
    .merge(paginationSearchParameterSchema)
    .merge(projectIdentifierSearchParameterSchema)
    .merge(sortingSearchParameterSchema),
  loader: async ({ context, params }) => {
    await prefetchUseRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRunsByOrtRunIndex(
      context.queryClient,
      {
        repositoryId: Number.parseInt(params.repoId),
        ortRunIndex: Number.parseInt(params.runIndex),
      }
    );
  },
  component: ProjectsComponent,
  pendingComponent: LoadingIndicator,
});
