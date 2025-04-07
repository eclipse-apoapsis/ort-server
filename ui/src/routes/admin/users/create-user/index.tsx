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
  useAdminServicePostApiV1AdminUsers,
  useOrganizationsServiceGetApiV1Organizations,
  useOrganizationsServicePutApiV1OrganizationsByOrganizationIdGroupsByGroupId,
} from '@/api/queries';
import { ApiError } from '@/api/requests';
import { asOptionalField } from '@/components/form/as-optional-field';
import { OptionalInput } from '@/components/form/optional-input';
import { PasswordInput } from '@/components/form/password-input';
import { LoadingIndicator } from '@/components/loading-indicator';
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
import { Checkbox } from '@/components/ui/checkbox';
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
import MultipleSelector, { Option } from '@/components/ui/multiple-selector';
import { ALL_ITEMS } from '@/lib/constants';
import { toast } from '@/lib/toast';

const formSchema = z.object({
  username: z.string().min(1),
  firstName: asOptionalField(z.string().min(1)),
  lastName: asOptionalField(z.string().min(1)),
  email: asOptionalField(z.string().email()),
  password: asOptionalField(z.string().min(1)),
  temporary: z.boolean(),
  organizations: z.array(z.string()).min(1, {
    message: 'The user must be part of at least one organization.',
  }),
});

const CreateUser = () => {
  const navigate = useNavigate();

  const {
    data: organizations,
    isPending: orgIsPending,
    isError: orgIsError,
    error: orgError,
  } = useOrganizationsServiceGetApiV1Organizations({
    limit: ALL_ITEMS,
  });

  const { mutateAsync: createUser, isPending: isCreateUserPending } =
    useAdminServicePostApiV1AdminUsers({
      onSuccess() {
        toast.info('Create User', {
          description: `User "${form.getValues().username}" created successfully.`,
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

  const {
    mutateAsync: addUserToReaders,
    isPending: isAddUserToReadersPending,
  } =
    useOrganizationsServicePutApiV1OrganizationsByOrganizationIdGroupsByGroupId(
      {
        onSuccess(_, variables) {
          const organizationName = organizations?.data.find(
            (org) => org.id === variables.organizationId
          )?.name;

          toast.info('Add Access Rights', {
            description: `The "${variables.requestBody?.username}" user was created and added to the "${organizationName}" organization as part of the READERS group.`,
          });
          navigate({
            to: '/admin/users',
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

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      username: '',
      temporary: true,
      organizations: [],
    },
  });

  async function onSubmit(values: z.infer<typeof formSchema>) {
    await createUser({
      requestBody: {
        username: values.username,
        firstName: values.firstName,
        lastName: values.lastName,
        email: values.email,
        password: values.password,
        temporary: values.temporary,
      },
    });
    // Add the user to the READERS group for each selected organization.
    await Promise.all(
      values.organizations.map((orgId) =>
        addUserToReaders({
          organizationId: Number.parseInt(orgId),
          groupId: 'readers',
          requestBody: {
            username: values.username,
          },
        })
      )
    );
  }

  if (orgIsPending) {
    return <LoadingIndicator />;
  }

  if (orgIsError) {
    toast.error('Unable to load data', {
      description: <ToastError error={orgError} />,
      duration: Infinity,
      cancel: {
        label: 'Dismiss',
        onClick: () => {},
      },
    });
    return;
  }

  const options: Option[] = organizations.data.map((o) => ({
    label: o.name,
    value: o.id.toString(),
  }));

  return (
    <Card className='col-span-2 w-full'>
      <CardHeader>
        <CardTitle>Create User</CardTitle>
        <CardDescription>Create a new user account.</CardDescription>
      </CardHeader>
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <CardContent className='space-y-4'>
            <FormField
              control={form.control}
              name='username'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Username</FormLabel>
                  <FormControl>
                    <Input {...field} autoFocus />
                  </FormControl>
                  <FormDescription>
                    The username needs to be globally unique.
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='firstName'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>First name</FormLabel>
                  <FormControl>
                    <OptionalInput {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='lastName'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Last name</FormLabel>
                  <FormControl>
                    <OptionalInput {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='email'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Email address</FormLabel>
                  <FormControl>
                    <OptionalInput type='email' {...field} />
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
                  <FormLabel>Password</FormLabel>
                  <FormControl>
                    <PasswordInput {...field} placeholder='(optional)' />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='temporary'
              render={({ field }) => (
                <FormItem className='flex flex-row items-start space-y-0 space-x-3 rounded-md border p-4'>
                  <FormControl>
                    <Checkbox
                      checked={field.value}
                      onCheckedChange={field.onChange}
                    />
                  </FormControl>
                  <div className='space-y-1 leading-none'>
                    <FormLabel>
                      Password change required on first login
                    </FormLabel>
                  </div>
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='organizations'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Organizations</FormLabel>
                  <FormControl>
                    <MultipleSelector
                      {...field}
                      placeholder='Start typing to find organizations...'
                      badgeClassName='bg-amber-200 text-black'
                      emptyIndicator={
                        <p className='text-center text-lg leading-10 text-gray-600 dark:text-gray-400'>
                          No results found.
                        </p>
                      }
                      value={field.value.map(
                        (v) =>
                          options.find((o) => o.value === v) || {
                            label: v,
                            value: v,
                          }
                      )}
                      defaultOptions={options}
                      onChange={(selected) =>
                        field.onChange(selected.map((s) => s.value))
                      }
                    />
                  </FormControl>
                  <FormDescription>
                    At least one organization needs to be chosen, to which the
                    user is granted READERS access.
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
          </CardContent>
          <CardFooter>
            <Button
              type='submit'
              disabled={isCreateUserPending || isAddUserToReadersPending}
              className='mt-4'
            >
              {isCreateUserPending ? (
                <>
                  <span className='sr-only'>Creating user...</span>
                  <Loader2 size={16} className='mx-3 animate-spin' />
                </>
              ) : isAddUserToReadersPending ? (
                <>
                  <span className='sr-only'>
                    Adding user to organizations...
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

export const Route = createFileRoute('/admin/users/create-user/')({
  component: CreateUser,
});
