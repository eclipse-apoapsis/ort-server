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
  getRepositoryInfrastructureServicesQueryKey,
  getRepositoryOptions,
  getRepositorySecretsQueryKey,
  patchRepositoryMutation,
} from '@/api/@tanstack/react-query.gen';
import { DeleteDialog } from '@/components/delete-dialog';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { ApiError } from '@/lib/api-error';
import { repositoryDeleted, repositoryUpdated } from '@/lib/entity-cache';
import {
  deleteRepositoryWithContents,
  type RepositoryDeletionPhase,
} from '@/lib/repository-deletion';
import { toast, toastError } from '@/lib/toast';
import { EditRepositoryForm } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/settings/-components/edit-repository-form';
import { MoveRepository } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/-components';
import type { RepositoryFormValues } from '@/schemas';

export const RepositorySettingsPage = () => {
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

  async function handleDelete() {
    const deletionState: { failedPhase: RepositoryDeletionPhase } = {
      failedPhase: 'infrastructure-services',
    };

    try {
      await deleteRepositoryWithContents(
        repository.id,
        ({ phase, deletedCount }) => {
          if (phase === 'infrastructure-services') {
            if (deletedCount > 0) {
              toast.info('Delete Infrastructure Services', {
                description: `Deleted ${deletedCount} infrastructure services from repository "${repository.url}".`,
              });
            }

            deletionState.failedPhase = 'secrets';
          } else if (phase === 'secrets') {
            if (deletedCount > 0) {
              toast.info('Delete Secrets', {
                description: `Deleted ${deletedCount} secrets from repository "${repository.url}".`,
              });
            }

            deletionState.failedPhase = 'repository';
          } else {
            toast.info('Delete Repository', {
              description: `Repository "${repository.url}" deleted successfully.`,
            });
          }
        }
      );
    } catch (error) {
      queryClient.invalidateQueries({
        queryKey: getRepositoryInfrastructureServicesQueryKey({
          path: { repositoryId: repository.id },
        }),
      });
      queryClient.invalidateQueries({
        queryKey: getRepositorySecretsQueryKey({
          path: { repositoryId: repository.id },
        }),
      });

      const failedEntities =
        deletionState.failedPhase === 'repository'
          ? 'repository'
          : `the ${deletionState.failedPhase.replace('-', ' ')} of repository`;
      toastError(
        `Could not delete ${failedEntities} "${repository.url}". ` +
          'Some infrastructure services and secrets may already have been deleted. ' +
          'Retry the deletion to remove the rest.',
        error
      );
      return;
    }

    repositoryDeleted(queryClient, repository);
    navigate({
      to: '/organizations/$orgId/products/$productId',
      params: { orgId: params.orgId, productId: params.productId },
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
                description='This deletes the repository together with all its ORT runs and their results, its secrets and its infrastructure services. Deletion is irreversible.'
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
