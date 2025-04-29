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

import { zodResolver } from '@hookform/resolvers/zod';
import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { Loader2 } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import {
  useRepositoriesServiceDeleteApiV1RepositoriesByRepositoryId,
  UseRepositoriesServiceGetApiV1RepositoriesByRepositoryIdKeyFn,
  useRepositoriesServicePatchApiV1RepositoriesByRepositoryId,
} from '@/api/queries';
import { useRepositoriesServiceGetApiV1RepositoriesByRepositoryIdSuspense } from '@/api/queries/suspense';
import { $RepositoryType, ApiError, RepositoriesService } from '@/api/requests';
import { DeleteDialog } from '@/components/delete-dialog';
import { ToastError } from '@/components/toast-error';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { toast } from '@/lib/toast';

const formSchema = z.object({
  url: z.string(),
  description: z.string().optional(),
  type: z.enum($RepositoryType.enum),
});

const RepositorySettingsPage = () => {
  const params = Route.useParams();
  const navigate = useNavigate();

  const repositoryId = Number.parseInt(params.repoId);

  const { data: repository } =
    useRepositoriesServiceGetApiV1RepositoriesByRepositoryIdSuspense({
      repositoryId,
    });

  const { mutateAsync, isPending } =
    useRepositoriesServicePatchApiV1RepositoriesByRepositoryId({
      onSuccess(data) {
        toast.info('Edit repository', {
          description: `Repository "${data.url}" updated successfully.`,
        });
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
        toast.error(error.message, {
          description: <ToastError error={error} />,
          duration: Infinity,
          cancel: {
            label: 'Dismiss',
            onClick: () => {},
          },
        });
      },
    });

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      url: repository.url,
      description: repository.description || '',
      type: repository.type,
    },
  });

  async function onSubmit(values: z.infer<typeof formSchema>) {
    await mutateAsync({
      repositoryId: repository.id,
      requestBody: {
        url: values.url,
        description: values.description,
        type: values.type,
      },
    });
  }

  const { mutateAsync: deleteRepository } =
    useRepositoriesServiceDeleteApiV1RepositoriesByRepositoryId({
      onSuccess() {
        toast.info('Delete Repository', {
          description: `Repository "${repository?.url}" deleted successfully.`,
        });
        navigate({
          to: '/organizations/$orgId/products/$productId',
          params: { orgId: params.orgId, productId: params.productId },
        });
      },
      onError(error: ApiError) {
        toast.error(error.message, {
          description: <ToastError error={error} />,
          duration: Infinity,
          cancel: {
            label: 'Dismiss',
            onClick: () => {},
          },
        });
      },
    });

  async function handleDelete() {
    await deleteRepository({
      repositoryId: Number.parseInt(params.repoId),
    });
  }

  return (
    <div className='flex flex-col gap-8'>
      <Card>
        <CardHeader>
          <CardTitle>Edit Repository</CardTitle>
        </CardHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className='space-y-8'>
            <CardContent className='space-y-4'>
              <FormField
                control={form.control}
                name='url'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>URL</FormLabel>
                    <FormControl autoFocus>
                      <Input {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name='description'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Description</FormLabel>
                    <FormControl>
                      <Input {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name='type'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Type</FormLabel>
                    <Select
                      onValueChange={field.onChange}
                      defaultValue={field.value}
                    >
                      <FormControl>
                        <SelectTrigger>
                          <SelectValue placeholder='Select a type' />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {Object.values($RepositoryType.enum).map((type) => (
                          <SelectItem key={type} value={type}>
                            {type}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </CardContent>
            <CardFooter>
              <Button
                type='button'
                className='m-1'
                variant='outline'
                onClick={() =>
                  navigate({
                    to:
                      '/organizations/' +
                      params.orgId +
                      '/products/' +
                      params.productId,
                  })
                }
                disabled={isPending}
              >
                Cancel
              </Button>
              <Button type='submit' disabled={isPending}>
                {isPending ? (
                  <>
                    <span className='sr-only'>Editing repository...</span>
                    <Loader2 size={16} className='mx-3 animate-spin' />
                  </>
                ) : (
                  'Submit'
                )}
              </Button>
            </CardFooter>
          </form>
        </Form>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>Danger Zone</CardTitle>
        </CardHeader>
        <CardContent>
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
        </CardContent>
      </Card>
    </div>
  );
};

export const Route = createFileRoute(
  '/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/settings/'
)({
  loader: async ({ context, params }) => {
    const repositoryId = Number.parseInt(params.repoId);
    await context.queryClient.prefetchQuery({
      queryKey: UseRepositoriesServiceGetApiV1RepositoriesByRepositoryIdKeyFn({
        repositoryId,
      }),
      queryFn: () =>
        RepositoriesService.getApiV1RepositoriesByRepositoryId({
          repositoryId,
        }),
    });
  },
  component: RepositorySettingsPage,
});
