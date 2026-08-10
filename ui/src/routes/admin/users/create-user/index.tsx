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
import { useMutation } from '@tanstack/react-query';
import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { Loader2 } from 'lucide-react';
import { useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import {
  getOrganizationsInfiniteOptions,
  postUserMutation,
  putOrganizationRoleToUserMutation,
} from '@/api/@tanstack/react-query.gen';
import { asOptionalField } from '@/components/form/as-optional-field';
import { OptionalInput } from '@/components/form/optional-input';
import { PasswordInput } from '@/components/form/password-input';
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
import { useDebounce } from '@/hooks/use-debounce';
import { useInfiniteList } from '@/hooks/use-infinite-list';
import { ApiError } from '@/lib/api-error';
import { DROPDOWN_PAGE_SIZE } from '@/lib/constants';
import { toSearchFilter } from '@/lib/regex';
import { toast, toastError } from '@/lib/toast';

const formSchema = z.object({
  username: z.string().trim().min(1),
  firstName: asOptionalField(z.string().min(1)),
  lastName: asOptionalField(z.string().min(1)),
  email: asOptionalField(z.email()),
  password: asOptionalField(z.string().min(1)),
  temporary: z.boolean(),
  organizations: z.array(z.string()).min(1, {
    error: 'The user must be part of at least one organization.',
  }),
});

const CreateUser = () => {
  const navigate = useNavigate();

  // The name of every organization that has been picked so far. A picked organization is not
  // necessarily part of the page that is shown after the search term changes, and its badge still
  // has to show its name.
  const organizationNames = useRef(new Map<string, string>());

  // Nothing is loaded before the user has touched the field, so opening the form costs no request.
  const [isSelectorUsed, setIsSelectorUsed] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');

  const filter = toSearchFilter(useDebounce(searchTerm, 300));

  const organizations = useInfiniteList(
    getOrganizationsInfiniteOptions({
      query: { limit: DROPDOWN_PAGE_SIZE, filter: filter },
    }),
    { enabled: isSelectorUsed }
  );

  const organizationOptions: Option[] = organizations.items.map(
    (organization) => ({
      label: organization.name,
      value: organization.id.toString(),
    })
  );

  const { mutateAsync: createUser, isPending: isCreateUserPending } =
    useMutation({
      ...postUserMutation(),
      onError(error: ApiError) {
        toastError(error.message, error);
      },
    });

  const {
    mutateAsync: addUserToReaders,
    isPending: isAddUserToReadersPending,
  } = useMutation({
    ...putOrganizationRoleToUserMutation(),
    onSuccess(_, variables) {
      const organizationName = organizationNames.current.get(
        variables.path.organizationId.toString()
      );

      toast.info('Add Access Rights', {
        description: `The "${variables.body?.username}" user was created and assigned the READER role for the "${organizationName}" organization.`,
      });
      navigate({
        to: '/admin/users',
      });
    },
    onError(error) {
      toastError(error.message, error);
    },
  });

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      username: '',
      temporary: true,
      organizations: [],
    },
  });

  async function onSubmit(values: z.infer<typeof formSchema>) {
    const username = values.username.toLowerCase();
    await createUser({
      body: {
        username,
        firstName: values.firstName,
        lastName: values.lastName,
        email: values.email,
        password: values.password,
        temporary: values.temporary,
      },
    });
    toast.info('Create User', {
      description: `User "${username}" created successfully.`,
    });
    // Add the READER role to the user for each selected organization.
    await Promise.all(
      values.organizations.map((orgId) =>
        addUserToReaders({
          path: { organizationId: Number.parseInt(orgId), role: 'READER' },
          body: {
            username,
          },
        })
      )
    );
  }

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
                    The username needs to be globally unique. Usernames are
                    case-insensitive and will be stored in lowercase.
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
                      loadingIndicator={
                        <p className='text-center text-lg leading-10 text-gray-600 dark:text-gray-400'>
                          Loading...
                        </p>
                      }
                      value={field.value.map((organizationId) => ({
                        label:
                          organizationNames.current.get(organizationId) ??
                          organizationId,
                        value: organizationId,
                      }))}
                      options={organizationOptions}
                      loading={organizations.isPending}
                      hasMore={organizations.hasNextPage}
                      isLoadingMore={organizations.isFetchingNextPage}
                      onLoadMore={organizations.fetchNextPage}
                      // The organizations are searched on the server, so the list must not be
                      // filtered again by what has been typed.
                      commandProps={{ shouldFilter: false }}
                      inputProps={{
                        onValueChange: setSearchTerm,
                        onFocus: () => setIsSelectorUsed(true),
                      }}
                      onChange={(selected) => {
                        selected.forEach((option) =>
                          organizationNames.current.set(
                            option.value,
                            option.label
                          )
                        );
                        field.onChange(selected.map((s) => s.value));
                      }}
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
