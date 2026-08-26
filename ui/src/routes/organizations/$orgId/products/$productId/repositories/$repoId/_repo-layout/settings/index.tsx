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
  useMutation,
  useQueryClient,
  useSuspenseQuery,
} from '@tanstack/react-query';
import {
  createFileRoute,
  useNavigate,
  useRouter,
} from '@tanstack/react-router';

import {
  deleteRepositoryMutation,
  getRepositoryOptions,
  patchRepositoryMutation,
} from '@/api/@tanstack/react-query.gen';
import { DeleteDialog } from '@/components/delete-dialog';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { ApiError } from '@/lib/api-error';
import { repositoryDeleted, repositoryUpdated } from '@/lib/entity-cache';
import { toast, toastError } from '@/lib/toast';
import { EditRepositoryForm } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/settings/-components/edit-repository-form';
import { MoveRepository } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/-components';
import type { RepositoryFormValues } from '@/schemas';

const RepositorySettingsPage = () => {
  const params = Route.useParams();
  const navigate = useNavigate();
  const router = useRouter();
  const queryClient = useQueryClient();

  const repositoryId = Number.parseInt(params.repoId);

  const { data: repository } = useSuspenseQuery({
    ...getRepositoryOptions({
      path: {
        repositoryId,
      },
    }),
  });

  const { mutateAsync, isPending } = useMutation({
    ...patchRepositoryMutation(),
    async onSuccess(data) {
      toast.info('Edit repository', {
        description: `Repository "${data.url}" updated successfully.`,
      });
      repositoryUpdated(queryClient, data);
      // The breadcrumb, and through it the document title, is written by the loader of the parent
      // route, which navigating between its children does not re-run.
      await router.invalidate();
      navigate({
        to: '/organizations/$orgId/products/$productId/repositories/$repoId',
        params: {
          orgId: params.orgId,
          productId: params.productId,
          repoId: params.repoId,
        },
      });
    },
    onError(error: ApiError) {
      toastError(error.message, error);
    },
  });

  async function onSubmit(values: RepositoryFormValues) {
    await mutateAsync({
      path: {
        repositoryId: repository.id,
      },
      body: values,
    });
  }

  const { mutateAsync: deleteRepository } = useMutation({
    ...deleteRepositoryMutation(),
    onSuccess() {
      toast.info('Delete Repository', {
        description: `Repository "${repository.url}" deleted successfully.`,
      });
      repositoryDeleted(queryClient, repository);
      navigate({
        to: '/organizations/$orgId/products/$productId',
        params: { orgId: params.orgId, productId: params.productId },
      });
    },
    onError(error: ApiError) {
      toastError(error.message, error);
    },
  });

  async function handleDelete() {
    await deleteRepository({
      path: {
        repositoryId: Number.parseInt(params.repoId),
      },
    });
  }

  return (
    <div className='flex flex-col gap-8'>
      <EditRepositoryForm
        defaultValues={{
          description: repository.description || '',
          name: repository.name || '',
          url: repository.url,
          type: repository.type,
        }}
        isPending={isPending}
        onCancel={() =>
          navigate({
            to:
              '/organizations/' +
              params.orgId +
              '/products/' +
              params.productId,
          })
        }
        onSubmit={onSubmit}
      />
      <Card>
        <CardHeader>
          <CardTitle>Danger Zone</CardTitle>
        </CardHeader>
        <CardContent>
          <div className='flex flex-col gap-4'>
            <div className='flex justify-between'>
              <div>Delete this repository</div>
              <DeleteDialog
                thingName={'repository'}
                thingId={repository.url}
                uiComponent={
                  <Button variant='destructive'>Delete repository</Button>
                }
                onDelete={handleDelete}
              />
            </div>
            <div>Move this repository to another product</div>
            <MoveRepository repoUrl={repository.url} />
          </div>
        </CardContent>
      </Card>
    </div>
  );
};

export const Route = createFileRoute(
  '/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/settings/'
)({
  component: RepositorySettingsPage,
});
