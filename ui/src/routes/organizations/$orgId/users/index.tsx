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
import { useQueryClient } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';
import { Eye, Loader2, Pen, Shield } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import {
  useOrganizationsServiceDeleteApiV1OrganizationsByOrganizationIdGroupsByGroupId,
  useOrganizationsServiceGetApiV1OrganizationsByOrganizationIdUsersKey,
  useOrganizationsServicePutApiV1OrganizationsByOrganizationIdGroupsByGroupId,
} from '@/api/queries';
import { useOrganizationsServiceGetApiV1OrganizationsByOrganizationIdSuspense } from '@/api/queries/suspense';
import { ApiError } from '@/api/requests';
import { ToastError } from '@/components/toast-error';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
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
import { groupsSchema } from '@/schemas';
import { OrganizationUsersTable } from './-components/organization-users-table';

const formSchema = z.object({
  username: z.string().min(1),
  groupId: groupsSchema,
});

const ManageUsers = () => {
  const params = Route.useParams();

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      username: '',
      groupId: 'readers',
    },
  });

  const { data: organization } =
    useOrganizationsServiceGetApiV1OrganizationsByOrganizationIdSuspense({
      organizationId: Number.parseInt(params.orgId),
    });

  const queryClient = useQueryClient();

  const { mutateAsync: addUser, isPending: isAddUserPending } =
    useOrganizationsServicePutApiV1OrganizationsByOrganizationIdGroupsByGroupId(
      {
        onSuccess() {
          queryClient.invalidateQueries({
            queryKey: [
              useOrganizationsServiceGetApiV1OrganizationsByOrganizationIdUsersKey,
            ],
          });
          toast.info('Add User', {
            description: `User "${form.getValues().username}" added successfully to group "${form.getValues().groupId.toUpperCase()}".`,
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

  const { mutateAsync: leaveGroup, isPending: isLeaveGroupPending } =
    useOrganizationsServiceDeleteApiV1OrganizationsByOrganizationIdGroupsByGroupId(
      {
        onError(error: ApiError) {
          // There is no error when trying to remove a user from a group he actually is not a member of.
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

  // The user might accidentally try to add a user to a group, although the user already has a group assignment.
  // In this case, leave any potential other groups before adding the user to the new group.
  async function leaveOtherGroups(
    joinGroupId: string,
    username: string,
    organizationId: number
  ) {
    const groups = ['admins', 'writers', 'readers'];
    const otherGroups = groups.filter((group) => group !== joinGroupId);

    for (const group of otherGroups) {
      await leaveGroup({
        organizationId: organizationId,
        groupId: group,
        username: username,
      });
    }
  }

  async function onAddUser(values: z.infer<typeof formSchema>) {
    await leaveOtherGroups(
      values.groupId,
      values.username,
      Number.parseInt(params.orgId)
    );
    await addUser({
      organizationId: Number.parseInt(params.orgId),
      groupId: values.groupId,
      requestBody: {
        username: values.username,
      },
    });

    form.setValue('username', '');
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Manage Users</CardTitle>
        <CardDescription className='flex flex-col gap-2'>
          <span>
            Assign or remove users to different groups in organization:{' '}
            <span className='font-semibold'>{organization.name}</span>
          </span>
          <div className='grid grid-cols-[auto_1fr] items-center gap-x-1 gap-y-1'>
            <Eye size={16} />
            <span>
              READERS can view the organization and its projects and
              repositories.
            </span>
            <Pen size={16} />
            <span>
              WRITERS can view and edit the organization and its projects and
              repositories.
            </span>
            <Shield size={16} />
            <span>
              ADMINS can view, edit, and delete the organization and its
              projects and repositories.
            </span>
          </div>
          <span>NOTE: The username must already exist in Keycloak.</span>
        </CardDescription>
      </CardHeader>
      <CardContent className='space-y-4'>
        <Form {...form}>
          <form className='space-y-8'>
            <FormField
              control={form.control}
              name='username'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Username</FormLabel>
                  <FormControl autoFocus>
                    <Input {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='groupId'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Group</FormLabel>
                  <Select
                    onValueChange={field.onChange}
                    defaultValue={field.value}
                  >
                    <FormControl>
                      <SelectTrigger>
                        <SelectValue placeholder='Select a group' />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {groupsSchema.options.map((group) => (
                        <SelectItem key={group} value={group}>
                          <div className='flex items-center gap-2'>
                            {group === 'admins' && <Shield size={16} />}
                            {group === 'writers' && <Pen size={16} />}
                            {group === 'readers' && <Eye size={16} />}
                            {group.toUpperCase()}
                          </div>
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />
            <Button
              size='sm'
              className='ml-auto gap-1'
              type='submit'
              disabled={isAddUserPending}
              onClick={form.handleSubmit(onAddUser)}
            >
              {isAddUserPending || isLeaveGroupPending ? (
                <>
                  <span className='sr-only'>Adding user...</span>
                  <Loader2 size={16} className='mx-3 animate-spin' />
                </>
              ) : (
                'Add User to Group'
              )}
            </Button>
          </form>
        </Form>
        <OrganizationUsersTable />
      </CardContent>
    </Card>
  );
};

export const Route = createFileRoute('/organizations/$orgId/users/')({
  component: ManageUsers,
});
