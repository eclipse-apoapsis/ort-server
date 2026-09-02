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
import { useState } from 'react';
import { useForm } from 'react-hook-form';

import {
  getOrganizationsInfiniteOptions,
  getUsersQueryKey,
  postUserMutation,
  putOrganizationRoleToUserMutation,
} from '@/api/@tanstack/react-query.gen';
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
import { createUserFormSchema, type CreateUserFormValues } from '@/schemas';

interface CreateUserFormProps {
  isPending: boolean;
  onSubmit: (values: CreateUserFormValues) => Promise<void> | void;
}

export const CreateUserForm = ({
  isPending,
  onSubmit,
}: CreateUserFormProps) => {
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

  const form = useForm<CreateUserFormValues>({
    resolver: zodResolver(createUserFormSchema),
    defaultValues: {
      username: '',
      password: '',
      temporary: true,
      organizations: [],
    },
    mode: 'onChange',
  });

  const isSubmitting = isPending || form.formState.isSubmitting;

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
            <div className='grid items-center gap-4 md:grid-cols-[minmax(0,1fr)_auto]'>
              <FormField
                control={form.control}
                name='password'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Password</FormLabel>
                    <FormControl>
                      <PasswordInput {...field} />
                    </FormControl>
                    <FormDescription>
                      Share this initial password with the user over a secure
                      channel.
                    </FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name='temporary'
                render={({ field }) => (
                  <FormItem className='mb-2 flex h-9 flex-row items-center space-y-0 space-x-3 rounded-md border px-3'>
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
            </div>
            <div className='grid gap-4 md:grid-cols-2'>
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
            </div>
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
              name='organizations'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Organizations</FormLabel>
                  <FormControl>
                    <MultipleSelector
                      {...field}
                      placeholder='(optional) Start typing to find organizations...'
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
                    />
                  </FormControl>
                  <FormDescription>
                    Optionally grant the user the READER role for organizations.
                    To give access only to a product or repository, assign the
                    appropriate role in its "Users" section.
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
          </CardContent>
          <CardFooter>
            <Button
              type='submit'
              disabled={isSubmitting || !form.formState.isValid}
              className='mt-4'
            >
              {isSubmitting ? (
                <>
                  <span className='sr-only'>Creating user...</span>
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

export const CreateUserPage = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

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
  } = useMutation(putOrganizationRoleToUserMutation());

  async function onSubmit(values: CreateUserFormValues) {
    const username = values.username.toLowerCase();

    try {
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
    } catch {
      // The mutation's onError callback has already reported the error.
      return;
    }

    const assignments = await Promise.allSettled(
      values.organizations.map((organization) =>
        addUserToReaders({
          path: {
            organizationId: Number.parseInt(organization.value),
            role: 'READER',
          },
          body: {
            username,
          },
        })
      )
    );

    const failedAssignments = assignments.flatMap((result, index) =>
      result.status === 'rejected'
        ? [
            {
              organization: values.organizations[index]!,
              error: result.reason,
            },
          ]
        : []
    );

    if (failedAssignments.length > 0) {
      const organizationNames = failedAssignments
        .map(({ organization }) => `"${organization.label}"`)
        .join(', ');

      toastError(
        `User "${username}" was created, but the READER role could not be granted for ${organizationNames}. ` +
          `Assign the missing roles in each organization's "Users" section.`,
        failedAssignments[0]?.error
      );
    } else {
      const organizationNames = values.organizations
        .map((organization) => `"${organization.label}"`)
        .join(', ');

      toast.info('Create User', {
        description:
          values.organizations.length > 0
            ? `User "${username}" was created and granted the READER role for ${organizationNames}.`
            : `User "${username}" was created.`,
      });
    }

    queryClient.invalidateQueries({ queryKey: getUsersQueryKey() });

    navigate({
      to: '/admin/users',
    });
  }

  return (
    <CreateUserForm
      isPending={isCreateUserPending || isAddUserToReadersPending}
      onSubmit={onSubmit}
    />
  );
};

export const Route = createFileRoute('/admin/users/create-user/')({
  component: CreateUserPage,
});
