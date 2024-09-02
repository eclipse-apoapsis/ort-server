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

import { useSuspenseQuery } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';

import { useRepositoriesServiceGetOrtRunByIndexKey } from '@/api/queries';
import { RepositoriesService } from '@/api/requests';
import { LoadingIndicator } from '@/components/loading-indicator';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Label } from '@/components/ui/label';

const IssuesComponent = () => {
  const params = Route.useParams();
  const locale = navigator.language;

  const { data: ortRun } = useSuspenseQuery({
    queryKey: [
      useRepositoriesServiceGetOrtRunByIndexKey,
      params.repoId,
      params.runIndex,
    ],
    queryFn: async () =>
      await RepositoriesService.getOrtRunByIndex({
        repositoryId: Number.parseInt(params.repoId),
        ortRunIndex: Number.parseInt(params.runIndex),
      }),
  });

  return (
    <Card className='mx-auto h-fit w-full max-w-4xl'>
      <CardHeader>
        <CardTitle>Technical issues related to the ORT Server</CardTitle>
        <CardDescription>
          These are issues related to the ORT Server, for example failing
          configuration.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {ortRun.issues.length > 0 ? (
          ortRun.issues.map((issue) => (
            <Card key={issue.message}>
              <CardHeader>
                <CardTitle>
                  <div className='text-sm'>
                    <Label className='font-semibold'>Created at:</Label>{' '}
                    {new Date(issue.timestamp).toLocaleString(locale)}
                  </div>
                </CardTitle>
                <CardDescription>
                  <div className='flex flex-col text-sm'>
                    <div>
                      <Label className='font-semibold'>Severity:</Label>{' '}
                      {issue.severity}
                    </div>
                    <div>
                      <Label className='font-semibold'>Source:</Label>{' '}
                      {issue.source}
                    </div>
                  </div>
                </CardDescription>
                <CardContent className='text-sm'>{issue.message}</CardContent>
              </CardHeader>
            </Card>
          ))
        ) : (
          <Label className='font-semibold'>No issues found.</Label>
        )}
      </CardContent>
    </Card>
  );
};

export const Route = createFileRoute(
  '/_layout/organizations/$orgId/products/$productId/repositories/$repoId/_layout/runs/$runIndex/issues/'
)({
  loader: async ({ context, params }) => {
    await context.queryClient.ensureQueryData({
      queryKey: [
        useRepositoriesServiceGetOrtRunByIndexKey,
        params.repoId,
        params.runIndex,
      ],
      queryFn: () =>
        RepositoriesService.getOrtRunByIndex({
          repositoryId: Number.parseInt(params.repoId),
          ortRunIndex: Number.parseInt(params.runIndex),
        }),
    });
  },
  component: IssuesComponent,
  pendingComponent: LoadingIndicator,
});
