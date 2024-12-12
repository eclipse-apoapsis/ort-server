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
  useInfrastructureServicesServiceGetInfrastructureServicesByOrganizationIdKey,
  useInfrastructureServicesServicePatchInfrastructureServiceForOrganizationIdAndName,
} from '@/api/queries';
import { ApiError, InfrastructureServicesService } from '@/api/requests';
import { MultiSelectField } from '@/components/form/multi-select-field';
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
import { ALL_ITEMS } from '@/lib/constants';
import { toast } from '@/lib/toast';

const formSchema = z.object({
  name: z.string().optional(),
  url: z.string().url(),
  description: z.string().optional(),
  usernameSecretRef: z.string(),
  passwordSecretRef: z.string(),
  credentialsTypes: z.array(z.enum(['NETRC_FILE', 'GIT_CREDENTIALS_FILE'])),
});

type FormSchema = z.infer<typeof formSchema>;

const EditInfrastructureServicePage = () => {
  const navigate = useNavigate();
  const params = Route.useParams();

  /* Search service details from all infrastructure services of the organization
   * TODO: Edit this to fetch the details from:
   * GET /api/v1/organizations/{organizationId}/infrastructure-services/{serviceName}
   * when the endpoint is implemented
   */
  const { data: infrastructureServices } = useSuspenseQuery({
    queryKey: [
      useInfrastructureServicesServiceGetInfrastructureServicesByOrganizationIdKey,
      params.orgId,
    ],
    queryFn: () =>
      InfrastructureServicesService.getInfrastructureServicesByOrganizationId({
        organizationId: Number.parseInt(params.orgId),
        limit: ALL_ITEMS,
      }),
  });

  const service = infrastructureServices?.data.find(
    (service) => service.name === params.serviceName
  );

  const { mutateAsync, isPending } =
    useInfrastructureServicesServicePatchInfrastructureServiceForOrganizationIdAndName(
      {
        onSuccess(data) {
          toast.info('Edit Infrastructure Service', {
            description: `Infrastructure service "${data.name}" has been updated successfully.`,
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
      }
    );

  const form = useForm<FormSchema>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      name: service?.name,
      url: service?.url,
      description: service?.description ?? undefined,
      usernameSecretRef: service?.usernameSecretRef,
      passwordSecretRef: service?.passwordSecretRef,
      credentialsTypes: service?.credentialsTypes,
    },
  });

  const onSubmit = (values: FormSchema) => {
    mutateAsync({
      organizationId: Number.parseInt(params.orgId),
      serviceName: params.serviceName,
      requestBody: {
        url: values.url,
        description: values.description,
        usernameSecretRef: values.usernameSecretRef,
        passwordSecretRef: values.passwordSecretRef,
        credentialsTypes: values.credentialsTypes,
      },
    });
  };

  return (
    <Card>
      <CardHeader>Edit Infrastructure Service</CardHeader>
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)}>
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
                    The name of the infrastructure service (cannot be changed).
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
              disabled
            />
            <FormField
              control={form.control}
              name='url'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Url</FormLabel>
                  <FormControl autoFocus>
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
            <Button
              type='button'
              className='m-1'
              variant='outline'
              onClick={() =>
                navigate({
                  to: '/organizations/$orgId/infrastructure-services',
                  params: { orgId: params.orgId },
                })
              }
              disabled={isPending}
            >
              Cancel
            </Button>
            <Button type='submit' disabled={isPending}>
              {isPending ? (
                <>
                  <span className='sr-only'>
                    Editing infrastructure service...
                  </span>
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
  '/organizations/$orgId/infrastructure-services/$serviceName/edit/'
)({
  loader: async ({ context, params }) => {
    await context.queryClient.ensureQueryData({
      queryKey: [
        useInfrastructureServicesServiceGetInfrastructureServicesByOrganizationIdKey,
        params.orgId,
      ],
      queryFn: () =>
        InfrastructureServicesService.getInfrastructureServicesByOrganizationId(
          {
            organizationId: Number.parseInt(params.orgId),
            limit: ALL_ITEMS,
          }
        ),
    });
  },
  component: EditInfrastructureServicePage,
  pendingComponent: LoadingIndicator,
});
