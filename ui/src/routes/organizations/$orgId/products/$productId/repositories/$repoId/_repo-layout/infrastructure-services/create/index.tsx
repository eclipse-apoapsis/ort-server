/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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
import { useMutation } from '@tanstack/react-query';
import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { Loader2 } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { postRepositoryInfrastructureServiceMutation } from '@/api/@tanstack/react-query.gen';
import { withRepositoryPermission } from '@/components/authorization';
import { MultiSelectField } from '@/components/form/multi-select-field.tsx';
import { HelpButton } from '@/components/help/help-button';
import { SecretSelect } from '@/components/secret-select';
import { Button } from '@/components/ui/button.tsx';
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card.tsx';
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form.tsx';
import { Input } from '@/components/ui/input.tsx';
import { ApiError } from '@/lib/api-error';
import { toast, toastError } from '@/lib/toast';

const formSchema = z.object({
  name: z.string().trim().min(1),
  url: z.url(),
  description: z.string().optional(),
  usernameSecretRef: z.string(),
  passwordSecretRef: z.string(),
  credentialsTypes: z.array(z.enum(['NETRC_FILE', 'GIT_CREDENTIALS_FILE'])),
});

type FormSchema = z.infer<typeof formSchema>;

const CreateInfrastructureServicePage = () => {
  const navigate = useNavigate();
  const params = Route.useParams();
  const permissions = Route.useRouteContext().permissions;

  const { mutateAsync, isPending } = useMutation({
    ...postRepositoryInfrastructureServiceMutation(),
    onSuccess(data) {
      toast.info('Create Infrastructure Service', {
        description: `New infrastructure service "${data.name}" created successfully.`,
      });
      navigate({
        to: '/organizations/$orgId/products/$productId/repositories/$repoId/infrastructure-services',
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

  const form = useForm<FormSchema>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      name: '',
      url: '',
      usernameSecretRef: '',
      passwordSecretRef: '',
      credentialsTypes: ['NETRC_FILE'],
    },
  });

  const onSubmit = (values: FormSchema) => {
    mutateAsync({
      path: {
        repositoryId: Number.parseInt(params.repoId),
      },
      body: {
        name: values.name,
        url: values.url,
        description: values.description || undefined,
        usernameSecretRef: values.usernameSecretRef,
        passwordSecretRef: values.passwordSecretRef,
        credentialsTypes: values.credentialsTypes,
      },
    });
  };

  /*
   * Sources for descriptions used in the form:
   * - https://github.com/eclipse-apoapsis/ort-server/blob/e4c5284a75cc281de11c31f1958b0e3a50dcf270/model/src/commonMain/kotlin/InfrastructureService.kt
   * - https://github.com/eclipse-apoapsis/ort-server/blob/e4c5284a75cc281de11c31f1958b0e3a50dcf270/model/src/commonMain/kotlin/CredentialsType.kt
   */

  return (
    <Card>
      <CardHeader>
        <div className='flex items-center gap-1'>
          <CardTitle>Create Infrastructure Service</CardTitle>
          <HelpButton topic='infrastructure-services' />
        </div>
        <CardDescription>
          An infrastructure service provides the credentials for an external
          service, such as a source code or artifact repository, that a run
          needs to access.
        </CardDescription>
      </CardHeader>
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <CardContent className='space-y-4'>
            <FormField
              control={form.control}
              name='name'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Name</FormLabel>
                  <FormControl autoFocus>
                    <Input {...field} />
                  </FormControl>
                  <FormDescription>
                    The name of the infrastructure service.
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='url'
              render={({ field }) => (
                <FormItem>
                  <div className='flex items-center gap-1'>
                    <FormLabel>URL Prefix</FormLabel>
                    <HelpButton topic='infrastructure-services#url' />
                  </div>
                  <FormControl>
                    <Input {...field} type='url' />
                  </FormControl>
                  <FormDescription>
                    The URL prefix to match against the service's URL.
                  </FormDescription>
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
                  <FormDescription>
                    The description of the infrastructure service.
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='usernameSecretRef'
              render={({ field }) => (
                <FormItem>
                  <div className='flex items-center gap-1'>
                    <FormLabel>Username Secret</FormLabel>
                    <HelpButton topic='infrastructure-services#username-secret-ref' />
                  </div>
                  <SecretSelect
                    value={field.value}
                    onChange={field.onChange}
                    placeholder='Select an existing username secret from the list'
                    orgId={params.orgId}
                    productId={params.productId}
                    repositoryId={params.repoId}
                    permissions={permissions}
                  />
                  <FormDescription>
                    The name of the secret that contains the username of the
                    credentials for the infrastructure service. Please note that
                    the secret first needs to be created in order to use it
                    here.
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='passwordSecretRef'
              render={({ field }) => (
                <FormItem>
                  <div className='flex items-center gap-1'>
                    <FormLabel>Password Secret</FormLabel>
                    <HelpButton topic='infrastructure-services#password-secret-ref' />
                  </div>
                  <SecretSelect
                    value={field.value}
                    onChange={field.onChange}
                    placeholder='Select an existing password secret from the list'
                    orgId={params.orgId}
                    productId={params.productId}
                    repositoryId={params.repoId}
                    permissions={permissions}
                  />
                  <FormDescription>
                    The name of the secret that contains the password of the
                    credentials for the infrastructure service. Please note that
                    the secret first needs to be created in order to use it
                    here.
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            <MultiSelectField
              form={form}
              name='credentialsTypes'
              label='Add Credentials To Files'
              description='Choose the configuration files that external tools read the credentials of the service from.'
              helpTopic='infrastructure-services#credentials-types'
              options={[
                {
                  id: 'NETRC_FILE',
                  label: 'Netrc File',
                },
                {
                  id: 'GIT_CREDENTIALS_FILE',
                  label: 'Git Credentials File',
                },
              ]}
              className='mt-4!'
            />
          </CardContent>
          <CardFooter>
            <Button type='submit' disabled={isPending}>
              {isPending ? (
                <>
                  <span className='sr-only'>
                    Creating infrastructure service...
                  </span>
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
  '/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/infrastructure-services/create/'
)({
  // Creating an infrastructure service needs write permission, while the list needs only read
  // permission.
  component: withRepositoryPermission('WRITE', CreateInfrastructureServicePage),
});
