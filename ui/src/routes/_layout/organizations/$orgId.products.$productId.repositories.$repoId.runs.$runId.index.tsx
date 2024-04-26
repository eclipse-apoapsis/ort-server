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
  useRepositoriesServiceGetOrtRunByIndexKey,
} from '@/api/queries';
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from '@/components/ui/card';
import {
  TableHeader,
  TableRow,
  TableHead,
  TableBody,
  TableCell,
  Table,
} from '@/components/ui/table';
import { RepositoriesService } from '@/api/requests';
import { createFileRoute } from '@tanstack/react-router';
import { useSuspenseQuery } from '@tanstack/react-query';

const RunComponent = () => {
  const params = Route.useParams();
  
  const { data: ortRun } = useSuspenseQuery({
    queryKey: [useRepositoriesServiceGetOrtRunByIndexKey, params.orgId, params.productId, params.repoId, params.runId],
    queryFn: async () =>
      await RepositoriesService.getOrtRunByIndex(
        Number.parseInt(params.repoId),
        Number.parseInt(params.runId)
      ),
  },  
);

  return (
    <Card className="w-full max-w-4xl mx-auto">
      <CardHeader className="flex flex-row items-start">
        <div className="grid gap-2">
          <CardTitle>{ortRun.id}</CardTitle>
          <CardDescription>{ortRun.status}</CardDescription>
        </div>
      </CardHeader>
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Run parameter</TableHead>
              <TableHead>Value</TableHead>
              <TableHead className="sr-only">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow>
              <TableCell>
                Created At
              </TableCell>
              <TableCell>
                <div className="font-medium">{ortRun.createdAt}</div>
              </TableCell>
            </TableRow>
            <TableRow>
              <TableCell>
                Revision
              </TableCell>
              <TableCell>
                <div className="font-medium">{ortRun.revision}</div>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  )
}

export const Route = createFileRoute('/_layout/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runId/')({
  loader: async ({ context, params }) => {
    await context.queryClient.ensureQueryData({
        queryKey: [useRepositoriesServiceGetOrtRunByIndexKey, params.orgId, params.productId, params.repoId, params.runId],
        queryFn: () =>
          RepositoriesService.getOrtRunByIndex(
            Number.parseInt(params.repoId),
            Number.parseInt(params.runId)
          ),
    });
  },
  component: RunComponent,
})
