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
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { useSecretsServicePostSecretForOrganization } from '@/api/queries';
import { ApiError } from '@/api/requests';
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Switch } from '@/components/ui/switch';
import { useToast } from '@/components/ui/use-toast';
import {
  decodePropertyPath,
  encodePropertyPath,
  getPropertyPaths,
} from '@/helpers/defaults-helpers';
import { formSchema as runFormSchema } from '@/schemas';

// First implementation only supports primitive types (string, number, boolean)
// for the default run properties.
const paths = getPropertyPaths(runFormSchema, [
  'labels',
  'jobConfigs.parameters',
  'jobConfigs.analyzer.enabled',
]).filter((path) => path.type !== 'array<string>');

const formSchema = z.object({
  property: z.string().min(1),
  value: z.string().min(1).or(z.number()).or(z.boolean()),
  locked: z.boolean(),
});

const CreateOrganizationDefaultPage = () => {
  const navigate = useNavigate();
  const params = Route.useParams();
  const { toast } = useToast();
  const [selectionType, setSelectionType] = useState<string | undefined>();

  const { mutateAsync, isPending } = useSecretsServicePostSecretForOrganization(
    {
      onSuccess(data) {
        toast({
          title: 'Create default run property',
          description: `New organization default run property "${decodePropertyPath(data.name).property}" created successfully.`,
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
    }
  );

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      property: '',
      value: true,
      locked: false,
    },
  });

  async function onSubmit(values: z.infer<typeof formSchema>) {
    await mutateAsync({
      organizationId: Number.parseInt(params.orgId),
      requestBody: {
        name: encodePropertyPath(values.property, values.locked),
        value: 'xyx',
        description: values.value.toString(),
      },
    });
  }

  return (
    <Card className='mx-auto w-full max-w-4xl'>
      <CardHeader>Create Default Run Property</CardHeader>
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className='space-y-8'>
          <CardContent className='space-y-4'>
            <FormField
              control={form.control}
              name='property'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Run property</FormLabel>
                  <Select
                    onValueChange={(value) => {
                      field.onChange;
                      form.setValue('property', value);
                      setSelectionType(
                        paths.find((path) => path.path === value)?.type
                      );
                    }}
                    defaultValue={field.value}
                  >
                    <FormControl>
                      <SelectTrigger>
                        <SelectValue placeholder='Select a run property' />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {Object.values(paths).map((path) => (
                        <SelectItem key={path.path} value={path.path}>
                          {path.path}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <FormDescription>
                    Name and path of the run property you want to default
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            {selectionType && (
              <FormField
                control={form.control}
                name='value'
                render={({ field }) =>
                  selectionType === 'string' ||
                  selectionType === 'string | null' ? (
                    <FormItem>
                      <FormLabel>Value (text)</FormLabel>
                      <FormControl>
                        <Input
                          {...field}
                          type='text'
                          placeholder='Input text'
                          value={field.value.toString()}
                        />
                      </FormControl>
                      <FormDescription>Value of the property</FormDescription>
                      <FormMessage />
                    </FormItem>
                  ) : selectionType === 'number' ? (
                    <FormItem>
                      <FormLabel>Value (number)</FormLabel>
                      <FormControl>
                        <Input
                          {...field}
                          type='number'
                          placeholder='Input number'
                          value={Number(field.value)}
                        />
                      </FormControl>
                      <FormDescription>Value of the property</FormDescription>
                      <FormMessage />
                    </FormItem>
                  ) : selectionType === 'boolean' ? (
                    <FormItem className='mb-4 flex flex-row items-center justify-between rounded-lg border p-4'>
                      <div className='space-y-0.5'>
                        <FormLabel>True/false</FormLabel>
                        <FormDescription>Boolean value</FormDescription>
                      </div>
                      <FormControl>
                        <Switch
                          checked={field.value === true}
                          onCheckedChange={(checked) =>
                            field.onChange(checked ? true : false)
                          }
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  ) : (
                    <></>
                  )
                }
              />
            )}
            <FormField
              control={form.control}
              name='locked'
              render={({ field }) => (
                <FormItem className='mb-4 flex flex-row items-center justify-between rounded-lg border p-4'>
                  <div className='space-y-0.5'>
                    <FormLabel>Lock the value</FormLabel>
                    <FormDescription>
                      Lock the value of the property to prevent changing it in
                      ORT run creation form
                    </FormDescription>
                  </div>
                  <FormControl>
                    <Switch
                      checked={field.value}
                      onCheckedChange={field.onChange}
                    />
                  </FormControl>
                </FormItem>
              )}
            />
          </CardContent>
          <CardFooter>
            <Button type='submit' disabled={isPending}>
              {isPending ? (
                <>
                  <span className='sr-only'>
                    Creating default run property...
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
  '/_layout/organizations/$orgId/defaults/create-default'
)({
  component: CreateOrganizationDefaultPage,
});