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
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table';

import { useVulnerabilitiesServiceGetVulnerabilitiesByRunId } from '@/api/queries';
import { prefetchUseRepositoriesServiceGetOrtRunByIndex } from '@/api/queries/prefetch';
import { useRepositoriesServiceGetOrtRunByIndexSuspense } from '@/api/queries/suspense';
import { VulnerabilityWithIdentifier } from '@/api/requests';
import { DataTable } from '@/components/data-table/data-table';
import { LoadingIndicator } from '@/components/loading-indicator';
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion';
import { Badge } from '@/components/ui/badge';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { calcOverallVulnerability } from '@/helpers/calc-overall-vulnerability';
import { getVulnerabilityRatingBackgroundColor } from '@/helpers/get-status-colors';
import { paginationSchema } from '@/schemas';

const defaultPageSize = 10;

const columns: ColumnDef<VulnerabilityWithIdentifier>[] = [
  {
    accessorKey: 'vulnerability',
    header: () => <></>,
    cell: ({ row }) => {
      const type = row.original.identifier.type;
      const namespace = row.original.identifier.namespace;
      const name = row.original.identifier.name;
      const version = row.original.identifier.version;
      const references = row.original.vulnerability.references;

      // Calculate the overall vulnerability rating based on the individual ratings
      const ratings = row.original.vulnerability.references.map((reference) =>
        Number(reference.severity)
      );
      const overallRating = calcOverallVulnerability(ratings);

      return (
        <div className='flex flex-col gap-2'>
          <div className='flex justify-between'>
            <Label className='font-semibold'>
              {type ? type.concat(':') : ''}
              {namespace ? namespace.concat('/') : ''}
              {name ? name : ''}
              {version ? '@'.concat(version) : ''}
            </Label>

            <Badge className='bg-gray-400'>
              {row.original.vulnerability.externalId}
            </Badge>
          </div>
          <div className='flex justify-between'>
            <div>{row.original.vulnerability.summary}</div>
            <Badge
              className={`${getVulnerabilityRatingBackgroundColor(overallRating)}`}
            >
              {overallRating}
            </Badge>
          </div>
          <div className='italic text-muted-foreground'>
            {row.original.vulnerability.description}
          </div>
          <Accordion type='single' collapsible className='ml-4'>
            <AccordionItem
              value='references'
              className='rounded-lg border px-2'
            >
              <AccordionTrigger>
                <Label className='font-semibold text-blue-400 hover:underline'>
                  References
                </Label>
              </AccordionTrigger>
              {references.map((reference, index) => (
                <AccordionContent key={index}>
                  <div className='flex gap-2'>
                    <div>{reference.severity}</div>
                    <div>{reference.scoringSystem}</div>
                    <Link
                      className='font-semibold text-blue-400 hover:underline'
                      to={reference.url}
                      target='_blank'
                    >
                      {reference.url}
                    </Link>
                  </div>
                </AccordionContent>
              ))}
            </AccordionItem>
          </Accordion>
        </div>
      );
    },
  },
];

const VulnerabilitiesComponent = () => {
  const params = Route.useParams();
  const search = Route.useSearch();
  const pageIndex = search.page ? search.page - 1 : 0;
  const pageSize = search.pageSize ? search.pageSize : defaultPageSize;

  const { data: ortRun } = useRepositoriesServiceGetOrtRunByIndexSuspense({
    repositoryId: Number.parseInt(params.repoId),
    ortRunIndex: Number.parseInt(params.runIndex),
  });

  const {
    data: vulnerabilities,
    isPending,
    isError,
    error,
  } = useVulnerabilitiesServiceGetVulnerabilitiesByRunId({
    runId: ortRun.id,
    limit: pageSize,
    offset: pageIndex * pageSize,
  });

  const table = useReactTable({
    data: vulnerabilities?.data || [],
    columns,
    pageCount: Math.ceil(
      (vulnerabilities?.pagination.totalCount ?? 0) / pageSize
    ),
    state: {
      pagination: {
        pageIndex,
        pageSize,
      },
    },
    getCoreRowModel: getCoreRowModel(),
    manualPagination: true,
  });

  if (isPending) {
    return <LoadingIndicator />;
  }

  if (isError) {
    return error;
  }

  return (
    <Card className='h-fit'>
      <CardHeader>
        <CardTitle>Vulnerabilities (ORT run global ID: {ortRun.id})</CardTitle>
        <CardDescription>
          These are the vulnerabilities found in the project. By clicking on
          "References" you can see more information about the vulnerability.
          Please note that the overall severity rating is calculated based on
          the highest severity rating found in the references.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <CardContent>
          <DataTable table={table} />
        </CardContent>
      </CardContent>
    </Card>
  );
};

export const Route = createFileRoute(
  '/_layout/organizations/$orgId/products/$productId/repositories/$repoId/_layout/runs/$runIndex/vulnerabilities/'
)({
  validateSearch: paginationSchema,
  loader: async ({ context, params }) => {
    await prefetchUseRepositoriesServiceGetOrtRunByIndex(context.queryClient, {
      repositoryId: Number.parseInt(params.repoId),
      ortRunIndex: Number.parseInt(params.runIndex),
    });
  },
  component: VulnerabilitiesComponent,
  pendingComponent: LoadingIndicator,
});
