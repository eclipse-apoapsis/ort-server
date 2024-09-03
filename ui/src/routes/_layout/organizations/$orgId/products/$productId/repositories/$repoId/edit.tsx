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
import { useSuspenseQuery } from '@tanstack/react-query';
import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { Loader2 } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import {
  useRepositoriesServiceGetRepositoryById,
  useRepositoriesServiceGetRepositoryByIdKey,
  useRepositoriesServicePatchRepositoryById,
} from '@/api/queries';
import { $RepositoryType, ApiError, RepositoriesService } from '@/api/requests';
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
  type: z.enum($RepositoryType.enum),
});

const EditRepositoryPage = () => {
  const params = Route.useParams();
  const navigate = useNavigate();

  const { data: repository } = useSuspenseQuery({
    queryKey: [
      useRepositoriesServiceGetRepositoryById,
      params.orgId,
      params.productId,
      params.repoId,
    ],
    queryFn: async () =>
      await RepositoriesService.getRepositoryById({
        repositoryId: Number.parseInt(params.repoId),
      }),
  });

  const { mutateAsync, isPending } = useRepositoriesServicePatchRepositoryById({
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
      type: repository.type,
    },
  });

  async function onSubmit(values: z.infer<typeof formSchema>) {
    await mutateAsync({
      repositoryId: repository.id,
      requestBody: {
        url: values.url,
        type: values.type,
      },
    });
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Edit repository</CardTitle>
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
  );
};

export const Route = createFileRoute(
  '/_layout/organizations/$orgId/products/$productId/repositories/$repoId/edit'
)({
  loader: async ({ context, params }) => {
    await context.queryClient.ensureQueryData({
      queryKey: [useRepositoriesServiceGetRepositoryByIdKey, params.repoId],
      queryFn: () =>
        RepositoriesService.getRepositoryById({
          repositoryId: Number.parseInt(params.repoId),
        }),
    });
  },
  component: EditRepositoryPage,
});
