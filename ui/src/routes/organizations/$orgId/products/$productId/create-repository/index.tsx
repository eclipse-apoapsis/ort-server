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
  useProductsServicePostApiV1ProductsByProductIdRepositories,
  useRepositoriesServicePostApiV1RepositoriesByRepositoryIdInfrastructureServices,
  useRepositoriesServicePostApiV1RepositoriesByRepositoryIdSecrets,
} from '@/api/queries';
import { $RepositoryType, ApiError } from '@/api/requests';
import { asOptionalField } from '@/components/form/as-optional-field.ts';
import { OptionalInput } from '@/components/form/optional-input.tsx';
import { PasswordInput } from '@/components/form/password-input.tsx';
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
  FormDescription,
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
import { toast } from '@/lib/toast';
import { getRepositoryTypeLabel } from '@/lib/types';

const formSchema = z
  .object({
    url: z.string().url(),
    description: asOptionalField(z.string().min(1)),
    type: z.enum($RepositoryType.enum),
    username: asOptionalField(z.string().min(1)),
    password: asOptionalField(z.string().min(1)),
  })
  .refine(
    (data) =>
      (!data.username && !data.password) || (data.username && data.password),
    {
      message: 'Username and password must both be set or both be empty.',
      path: ['username'],
    }
  );

const CreateRepositoryPage = () => {
  const navigate = useNavigate();
  const params = Route.useParams();
  const { refreshUser } = useUser();

  const {
    mutateAsync: createRepository,
    isPending: isPendingCreateRepository,
  } = useProductsServicePostApiV1ProductsByProductIdRepositories({
    onSuccess(data) {
      // Refresh the user token and data to get the new roles after creating a new repository.
      refreshUser();

      toast.info('Add Repository', {
        description: `Repository ${data.url} added successfully.`,
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

  const { mutateAsync: createSecret, isPending: isPendingCreateSecret } =
    useRepositoriesServicePostApiV1RepositoriesByRepositoryIdSecrets({
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

  const {
    mutateAsync: createInfrastructureService,
    isPending: isPendingCreateInfrastructureService,
  } =
    useRepositoriesServicePostApiV1RepositoriesByRepositoryIdInfrastructureServices(
      {
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

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      url: '',
      type: 'GIT',
    },
  });

  async function onSubmit(values: z.infer<typeof formSchema>) {
    const createRepositoryResponse = await createRepository({
      productId: Number.parseInt(params.productId),
      requestBody: {
        url: values.url,
        description: values.description,
        type: values.type,
      },
    });

    if (values.username && values.password && createRepositoryResponse?.id) {
      const createUsernameResponse = await createSecret({
        repositoryId: createRepositoryResponse?.id,
        requestBody: {
          name: `username-repo-${createRepositoryResponse?.id}`,
          value: values.username,
          description: `Username for accessing repository ${createRepositoryResponse?.id}`,
        },
      });

      const createPasswordResponse = await createSecret({
        repositoryId: createRepositoryResponse?.id,
        requestBody: {
          name: `password-repo-${createRepositoryResponse?.id}`,
          value: values.password,
          description: `Password for accessing repository ${createRepositoryResponse?.id}`,
        },
      });

      if (createUsernameResponse.name && createPasswordResponse.name) {
        const createInfrastructureServiceResponse =
          await createInfrastructureService({
            repositoryId: createRepositoryResponse.id,
            requestBody: {
              name: `infrastructure-service-repo-${createRepositoryResponse.id}`,
              url: createRepositoryResponse.url,
              description: `Infrastructure service for accessing repository ${createRepositoryResponse.id}`,
              credentialsTypes: ['NETRC_FILE', 'GIT_CREDENTIALS_FILE'],
              usernameSecretRef: createUsernameResponse.name,
              passwordSecretRef: createPasswordResponse.name,
            },
          });

        if (createInfrastructureServiceResponse.name) {
          await navigate({
            to: '/organizations/$orgId/products/$productId/repositories/$repoId',
            params: {
              orgId: params.orgId,
              productId: params.productId,
              repoId: createRepositoryResponse.id.toString(),
            },
          });
        }
      }
    } else if (createRepositoryResponse?.id) {
      // If no credentials are provided, just navigate to the repository details page.
      await navigate({
        to: '/organizations/$orgId/products/$productId/repositories/$repoId',
        params: {
          orgId: params.orgId,
          productId: params.productId,
          repoId: createRepositoryResponse.id.toString(),
        },
      });
    }
  }

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
                          {getRepositoryTypeLabel(type)}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
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
              name='username'
              render={({ field }) => (
                <FormItem>
                  <FormDescription>
                    <b>Note:</b> If you have multiple repositories that use the
                    same credentials, it is recommended <b>not</b> to specify
                    the credentials here. Instead, reuse an existing{' '}
                    <b>Infrastructure Service</b> or create a new one that
                    provides access to this repository. Using{' '}
                    <b>shared credentials</b> in this way simplifies
                    maintenance, especially when updating expired passwords or
                    personal access tokens.
                  </FormDescription>
                  <FormLabel>Username</FormLabel>
                  <FormControl>
                    <OptionalInput {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='password'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Password or Personal Access Token (PAT)</FormLabel>
                  <FormControl>
                    <PasswordInput {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </CardContent>
          <CardFooter>
            <Button
              type='submit'
              disabled={
                isPendingCreateRepository ||
                isPendingCreateSecret ||
                isPendingCreateInfrastructureService
              }
            >
              {isPendingCreateRepository ||
              isPendingCreateSecret ||
              isPendingCreateInfrastructureService ? (
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

export const Route = createFileRoute(
  '/organizations/$orgId/products/$productId/create-repository/'
)({
  component: CreateRepositoryPage,
});
