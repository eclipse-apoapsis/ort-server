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
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { Loader2 } from 'lucide-react';
import { useForm } from 'react-hook-form';

import { postRepositoryMutation } from '@/api/@tanstack/react-query.gen';
import { zRepositoryType } from '@/api/zod.gen';
import { OptionalInput } from '@/components/form/optional-input.tsx';
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
import { useUser } from '@/hooks/use-user';
import { ApiError } from '@/lib/api-error';
import { repositoryCreated } from '@/lib/entity-cache';
import { toast, toastError } from '@/lib/toast';
import { getRepositoryTypeLabel } from '@/lib/types';
import { repositoryFormSchema, type RepositoryFormValues } from '@/schemas';

interface CreateRepositoryFormProps {
  isPending: boolean;
  onSubmit: (values: RepositoryFormValues) => Promise<void> | void;
}

export const CreateRepositoryForm = ({
  isPending,
  onSubmit,
}: CreateRepositoryFormProps) => {
  const form = useForm<RepositoryFormValues>({
    resolver: zodResolver(repositoryFormSchema),
    defaultValues: {
      description: '',
      name: '',
      url: '',
      type: 'GIT',
    },
    mode: 'onChange',
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>Add Repository</CardTitle>
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
              name='name'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Name</FormLabel>
                  <FormControl>
                    <OptionalInput {...field} />
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
                    <OptionalInput {...field} />
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
                      {Object.values(zRepositoryType.enum).map((type) => (
                        <SelectItem key={type} value={type}>
                          {getRepositoryTypeLabel(type)}
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
              type='submit'
              disabled={isPending || !form.formState.isValid}
            >
              {isPending ? (
                <>
                  <span className='sr-only'>Creating repository...</span>
                  <Loader2 size={16} className='mx-3 animate-spin' />
                </>
              ) : (
                'Create'
              )}
            </Button>
          </CardFooter>
        </form>
      </Form>
    </Card>
  );
};

const CreateRepositoryPage = () => {
  const navigate = useNavigate();
  const params = Route.useParams();
  const queryClient = useQueryClient();
  const { refreshUser } = useUser();

  const { mutateAsync, isPending } = useMutation({
    ...postRepositoryMutation(),
    onSuccess(data) {
      // Refresh the user token and data to get the new roles after creating a new repository.
      refreshUser();

      toast.info('Add Repository', {
        description: `Repository ${data.url} added successfully.`,
      });
      repositoryCreated(queryClient, data.productId);
      navigate({
        to: '/organizations/$orgId/products/$productId/repositories/$repoId',
        params: {
          orgId: params.orgId,
          productId: params.productId,
          repoId: data.id.toString(),
        },
      });
    },
    onError(error: ApiError) {
      toastError(error.message, error);
    },
  });

  async function onSubmit(values: RepositoryFormValues) {
    await mutateAsync({
      path: { productId: Number.parseInt(params.productId) },
      body: values,
    });
  }

  return <CreateRepositoryForm isPending={isPending} onSubmit={onSubmit} />;
};

export const Route = createFileRoute(
  '/organizations/$orgId/products/$productId/create-repository/'
)({
  component: CreateRepositoryPage,
});
