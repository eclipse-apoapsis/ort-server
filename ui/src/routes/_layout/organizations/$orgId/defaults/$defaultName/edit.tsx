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
  useSecretsServiceGetSecretByOrganizationIdAndNameKey,
  useSecretsServicePatchSecretByOrganizationIdAndName,
} from '@/api/queries';
import { ApiError, SecretsService } from '@/api/requests';
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
import { Switch } from '@/components/ui/switch';
import { useToast } from '@/components/ui/use-toast';
import { decodePropertyPath } from '@/helpers/defaults-helpers';

const editDefaultFormSchema = z.object({
  property: z.string().optional(),
  value: z.string().optional(),
  locked: z.boolean(),
});

export type EditDefaultFormValues = z.infer<typeof editDefaultFormSchema>;

const EditOrganizationDefaultPage = () => {
  const params = Route.useParams();
  const navigate = useNavigate();
  const { toast } = useToast();

  const { data: secret } = useSuspenseQuery({
    queryKey: [
      useSecretsServiceGetSecretByOrganizationIdAndNameKey,
      params.orgId,
      params.defaultName,
    ],
    queryFn: () =>
      SecretsService.getSecretByOrganizationIdAndName({
        organizationId: Number.parseInt(params.orgId),
        secretName: params.defaultName,
      }),
  });

  const form = useForm<EditDefaultFormValues>({
    resolver: zodResolver(editDefaultFormSchema),
    values: {
      property: decodePropertyPath(secret.name).property,
      value: secret.description,
      locked: decodePropertyPath(secret.name).locked,
    },
  });

  const { mutateAsync: editSecret, isPending } =
    useSecretsServicePatchSecretByOrganizationIdAndName({
      onSuccess(data) {
        toast({
          title: 'Edit organization default property',
          description: `Default property "${data.name}" updated successfully.`,
        });
        navigate({
          to: '/organizations/$orgId/defaults',
          params: { orgId: params.orgId },
        });
      },
      onError(error: ApiError) {
        toast({
          title: error.message,
          description: <ToastError error={error} />,
          variant: 'destructive',
        });
      },
    });

  const onSubmit = (values: EditDefaultFormValues) => {
    editSecret({
      organizationId: Number.parseInt(params.orgId),
      secretName: secret.name,
      requestBody: {
        name: values.property,
        value: 'xyz',
        description: values.value,
      },
    });
  };

  return (
    <Card className='mx-auto w-full max-w-4xl'>
      <CardHeader>Edit Default Run Property</CardHeader>
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className='space-y-8'>
          <CardContent className='space-y-4'>
            <FormField
              control={form.control}
              name='property'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Run Property</FormLabel>
                  <FormControl>
                    <Input {...field} />
                  </FormControl>
                  <FormDescription>
                    The name of the run property (cannot be changed)
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
                <FormItem>
                  <FormLabel>Value</FormLabel>
                  <FormControl>
                    <Input {...field} />
                  </FormControl>
                  <FormDescription>
                    Value of the default property
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='locked'
              render={({ field }) => (
                <FormItem className='mb-4 flex flex-row items-center justify-between rounded-lg border p-4'>
                  <div className='space-y-0.5'>
                    <FormLabel>Lock the value</FormLabel>
                    <FormDescription>
                      Lock the value of the property to prevent changing it in
                      ORT run creation form (cannot be changed)
                    </FormDescription>
                  </div>
                  <FormControl>
                    <Switch
                      checked={field.value}
                      onCheckedChange={field.onChange}
                      disabled
                    />
                  </FormControl>
                </FormItem>
              )}
            />
          </CardContent>
          <CardFooter>
            <Button
              className='m-1'
              variant='outline'
              onClick={() =>
                navigate({
                  to: '/organizations/$orgId/defaults',
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
                  <span className='sr-only'>Editing default property...</span>
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
  '/_layout/organizations/$orgId/defaults/$defaultName/edit'
)({
  loader: async ({ context, params }) => {
    await Promise.allSettled([
      context.queryClient.ensureQueryData({
        queryKey: [
          useSecretsServiceGetSecretByOrganizationIdAndNameKey,
          params.orgId,
          params.defaultName,
        ],
        queryFn: () =>
          SecretsService.getSecretByOrganizationIdAndName({
            organizationId: Number.parseInt(params.orgId),
            secretName: params.defaultName,
          }),
      }),
    ]);
  },
  component: EditOrganizationDefaultPage,
  pendingComponent: LoadingIndicator,
});
