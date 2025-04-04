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
import z from 'zod';

import {
  useRepositoriesServiceGetApiV1RepositoriesByRepositoryIdSecretsBySecretNameKey,
  useRepositoriesServicePatchApiV1RepositoriesByRepositoryIdSecretsBySecretName,
} from '@/api/queries';
import { ApiError, RepositoriesService } from '@/api/requests';
import { PasswordInput } from '@/components/form/password-input';
import { LoadingIndicator } from '@/components/loading-indicator';
import { ToastError } from '@/components/toast-error';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
} from '@/components/ui/card';
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { toast } from '@/lib/toast';

const editSecretFormSchema = z.object({
  name: z.string().optional(),
  value: z.string().optional(),
  description: z.string().optional(),
});

export type EditSecretFormValues = z.infer<typeof editSecretFormSchema>;

const EditRepositorySecretPage = () => {
  const params = Route.useParams();
  const navigate = useNavigate();

  const { data: secret } = useSuspenseQuery({
    queryKey: [
      useRepositoriesServiceGetApiV1RepositoriesByRepositoryIdSecretsBySecretNameKey,
      params.repoId,
      params.secretName,
    ],
    queryFn: () =>
      RepositoriesService.getApiV1RepositoriesByRepositoryIdSecretsBySecretName(
        {
          repositoryId: Number.parseInt(params.repoId),
          secretName: params.secretName,
        }
      ),
  });

  const form = useForm<EditSecretFormValues>({
    resolver: zodResolver(editSecretFormSchema),
    values: {
      name: secret.name,
      value: '',
      description: secret.description ?? undefined,
    },
  });

  const { mutateAsync: editSecret, isPending } =
    useRepositoriesServicePatchApiV1RepositoriesByRepositoryIdSecretsBySecretName(
      {
        onSuccess(data) {
          toast.info('Edit Repository Secret', {
            description: `Secret "${data.name}" updated successfully.`,
          });
          navigate({
            to: '/organizations/$orgId/products/$productId/repositories/$repoId/secrets',
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
      }
    );

  const onSubmit = (values: EditSecretFormValues) => {
    editSecret({
      repositoryId: Number.parseInt(params.repoId),
      secretName: secret.name,
      requestBody: {
        value: values.value,
        description: values.description,
      },
    });
  };

  return (
    <Card>
      <CardHeader>Edit Repository Secret</CardHeader>
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className='space-y-8'>
          <CardContent className='space-y-4'>
            <FormField
              control={form.control}
              name='name'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Name</FormLabel>
                  <FormControl>
                    <Input {...field} />
                  </FormControl>
                  <FormDescription>
                    The name of the secret (cannot be changed).
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
              disabled
            />
            <FormField
              control={form.control}
              name='value'
              render={({ field }) => (
                <FormItem className='mt-0'>
                  <FormLabel>Value</FormLabel>
                  <FormControl autoFocus>
                    <PasswordInput {...field} />
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
                    <Input {...field} placeholder='(optional)' />
                  </FormControl>
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
                  to: '/organizations/$orgId/products/$productId/repositories/$repoId/secrets',
                  params: {
                    orgId: params.orgId,
                    productId: params.productId,
                    repoId: params.repoId,
                  },
                })
              }
              disabled={isPending}
            >
              Cancel
            </Button>
            <Button type='submit' disabled={isPending}>
              {isPending ? (
                <>
                  <span className='sr-only'>Editing secret...</span>
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
  '/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/secrets/$secretName/edit/'
)({
  loader: async ({ context, params }) => {
    await context.queryClient.ensureQueryData({
      queryKey: [
        useRepositoriesServiceGetApiV1RepositoriesByRepositoryIdSecretsBySecretNameKey,
        params.repoId,
        params.secretName,
      ],
      queryFn: () =>
        RepositoriesService.getApiV1RepositoriesByRepositoryIdSecretsBySecretName(
          {
            repositoryId: Number.parseInt(params.repoId),
            secretName: params.secretName,
          }
        ),
    });
  },
  component: EditRepositorySecretPage,
  pendingComponent: LoadingIndicator,
});
