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

import { useInfrastructureServicesServicePostInfrastructureServiceForOrganization } from '@/api/queries';
import { ApiError } from '@/api/requests';
import { MultiSelectField } from '@/components/form/multi-select-field';
import { ToastError } from '@/components/toast-error';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
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
import { toast } from '@/lib/toast';

const formSchema = z.object({
  name: z.string(),
  url: z.string().url(),
  description: z.string().optional(),
  usernameSecretRef: z.string(),
  passwordSecretRef: z.string(),
  credentialsTypes: z.array(z.enum(['NETRC_FILE', 'GIT_CREDENTIALS_FILE'])),
});

type FormSchema = z.infer<typeof formSchema>;

const CreateInfrastructureServicePage = () => {
  const navigate = useNavigate();
  const params = Route.useParams();

  const { mutateAsync, isPending } =
    useInfrastructureServicesServicePostInfrastructureServiceForOrganization({
      onSuccess(data) {
        toast.info('Create Infrastructure Service', {
          description: `New infrastructure service "${data.name}" created successfully.`,
        });
        navigate({
          to: '/organizations/$orgId/infrastructure-services',
          params: { orgId: params.orgId },
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
      organizationId: Number.parseInt(params.orgId),
      requestBody: {
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
        <CardTitle>Create Infrastructure Service</CardTitle>
        <CardDescription>
          An infrastructure service refers to essential services required by a
          repository during an ORT run. These services can, for instance,
          include source code or artifact repositories that help resolve
          dependencies for the repository being analyzed. Defining these
          services is crucial for setting up the build environment and ensuring
          the necessary credentials are available for access.
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
                  <FormLabel>Url</FormLabel>
                  <FormControl>
                    <Input {...field} type='url' />
                  </FormControl>
                  <FormDescription>
                    The URL of the infrastructure service.
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
                  <FormLabel>Username Secret</FormLabel>
                  <FormControl>
                    <Input {...field} />
                  </FormControl>
                  <FormDescription>
                    The name of the organization secret that contains the
                    username of the credentials for the infrastructure service.
                    Please note that the secret first needs to be created in
                    order to use it here.
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
                  <FormLabel>Password Secret</FormLabel>
                  <FormControl>
                    <Input {...field} />
                  </FormControl>
                  <FormDescription>
                    The name of the organization secret that contains the
                    password of the credentials for the infrastructure service.
                    Please note that the secret first needs to be created in
                    order to use it here.
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            <MultiSelectField
              form={form}
              name='credentialsTypes'
              label='Add Credentials To Files'
              description={
                <div className='space-y-2'>
                  <p>
                    Choose which files the credentials of the service should be
                    added to. Note that you can choose multiple files, or none
                    at all, in which case the credentials for this service will
                    be ignored when generating configuration files for
                    credentials.
                  </p>
                  <p>
                    In most cases, the desired option is to add the credentials
                    to the Netrc file, as this allows access to the service from
                    most external tools. Normally, Git should be able to obtain
                    the credentials from the Netrc file, however, there are rare
                    cases when Git is not able to authenticate against a
                    repository based on the information in the Netrc file. In
                    these cases, you can choose to add the credentials to the
                    Git credentials file.
                  </p>
                  <p>
                    In some cases, there could be conflicting services; for
                    instance, if multiple repositories with different
                    credentials are defined on the same repository server.
                    Therefore it is also possible to choose not to include the
                    credentials in any files.
                  </p>
                </div>
              }
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
              className='!mt-4'
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
  '/organizations/$orgId/infrastructure-services/create/'
)({
  component: CreateInfrastructureServicePage,
});
