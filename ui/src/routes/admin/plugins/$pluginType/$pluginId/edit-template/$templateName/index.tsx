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
import { CheckedState } from '@radix-ui/react-checkbox';
import { useMutation, useSuspenseQuery } from '@tanstack/react-query';
import {
  createFileRoute,
  useLoaderData,
  useNavigate,
} from '@tanstack/react-router';
import { Loader2 } from 'lucide-react';
import { ChangeEvent } from 'react';
import { Resolver, useForm } from 'react-hook-form';
import { z, ZodType } from 'zod';

import { PluginOption, PluginOptionTemplate, PluginOptionType } from '@/api';
import {
  getPluginTemplateOptions,
  getPluginTemplateQueryKey,
  updatePluginTemplateOptionsMutation,
} from '@/api/@tanstack/react-query.gen';
import { OptionalInput } from '@/components/form/optional-input';
import { Badge } from '@/components/ui/badge.tsx';
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
import { Switch } from '@/components/ui/switch';
import { ApiError } from '@/lib/api-error';
import { queryClient } from '@/lib/query-client';
import { toast, toastError } from '@/lib/toast';
import { getPluginTypeLabel } from '@/lib/types';
import { Route as LayoutRoute } from '../../../../route.tsx';

function optionTypeToZodType(type: PluginOptionType): ZodType {
  switch (type) {
    case 'BOOLEAN':
      return z.boolean().default(false);
    case 'INTEGER':
      return z.coerce.number();
    case 'LONG':
      return z.coerce.bigint();
    case 'SECRET':
      return z.string();
    case 'STRING':
      return z.string();
    case 'STRING_LIST':
      return z.array(z.string());
    default:
      throw new Error(`Unsupported option type: ${type}`);
  }
}

function buildFormSchema(options: Array<PluginOption>) {
  const shape: Record<string, ZodType> = {};
  for (const opt of options) {
    let schema = optionTypeToZodType(opt.type);
    if (opt.isNullable) {
      schema = schema.nullable();
    }
    if (!opt.isRequired) {
      schema = schema.optional();
    }
    shape[opt.name] = schema;
    shape[`${opt.name}_isFinal`] = z.boolean().default(false);
    shape[`${opt.name}_isNotSet`] = z.boolean().default(false);
  }
  return z.object(shape);
}

function parseStoredValue(
  value: string | null | undefined,
  type: PluginOptionType
): unknown {
  if (value === null || value === undefined) return '';
  switch (type) {
    case 'BOOLEAN':
      return value === 'true';
    case 'INTEGER':
      return Number(value);
    case 'LONG':
      return value;
    case 'STRING_LIST':
      return value.split(',');
    default:
      return value;
  }
}

type FormValues = Record<string, unknown>;

const EditTemplate = () => {
  const navigate = useNavigate();
  const { plugins } = useLoaderData({ from: LayoutRoute.id });
  const params = Route.useParams();

  const plugin = plugins.find(
    (p) => p.type === params.pluginType && p.id === params.pluginId
  );

  const { data: template } = useSuspenseQuery({
    ...getPluginTemplateOptions({
      path: {
        pluginType: params.pluginType,
        pluginId: params.pluginId,
        templateName: params.templateName,
      },
    }),
  });

  const formSchema = plugin?.options
    ? buildFormSchema(plugin.options)
    : z.object({});

  const form = useForm<FormValues>({
    resolver: zodResolver(formSchema) as unknown as Resolver<FormValues>,
    values:
      plugin?.options?.reduce((acc, option) => {
        const templateOption = template.options.find(
          (o) => o.option === option.name
        );
        acc[option.name] = templateOption
          ? parseStoredValue(templateOption.value, option.type)
          : option.type === 'BOOLEAN'
            ? false
            : (option.defaultValue ?? '');
        acc[`${option.name}_isFinal`] = templateOption?.isFinal ?? false;
        acc[`${option.name}_isNotSet`] = !templateOption;
        return acc;
      }, {} as FormValues) ?? {},
  });

  function handleValueChange(optionName: string, value: string) {
    if (value !== '') {
      form.setValue(`${optionName}_isNotSet`, false);
    } else {
      form.setValue(`${optionName}_isNotSet`, true);
    }
  }

  const { mutateAsync: updateTemplate, isPending: isUpdateTemplatePending } =
    useMutation({
      ...updatePluginTemplateOptionsMutation(),
      onSuccess() {
        toast.info('Edit Template', {
          description: `Template updated successfully.`,
        });
        queryClient.invalidateQueries({
          queryKey: getPluginTemplateQueryKey({
            path: {
              pluginType: params.pluginType,
              pluginId: params.pluginId,
              templateName: params.templateName,
            },
          }),
        });
        navigate({
          to: '/admin/plugins/$pluginType/$pluginId',
          params: {
            pluginType: params.pluginType,
            pluginId: params.pluginId,
          },
        });
      },
      onError(error) {
        const apiError = error as ApiError;
        toastError(error.message, apiError);
      },
    });

  async function onSubmit(formValues: FormValues) {
    const requestBody: PluginOptionTemplate[] =
      plugin?.options
        ?.filter((option) => !formValues[`${option.name}_isNotSet`])
        ?.map((option) => {
          const value = formValues[option.name];
          const isFinal = Boolean(formValues[`${option.name}_isFinal`]);

          let stringValue: string | null;
          if (value === null || value === undefined) {
            stringValue = null;
          } else if (Array.isArray(value)) {
            stringValue = value.join(',');
          } else if (
            typeof value === 'bigint' ||
            typeof value === 'number' ||
            typeof value === 'boolean'
          ) {
            stringValue = value.toString();
          } else {
            stringValue = value as string;
          }

          return {
            option: option.name,
            type: option.type,
            value: stringValue,
            isFinal,
          };
        }) ?? [];

    await updateTemplate({
      path: {
        pluginType: params.pluginType,
        pluginId: params.pluginId,
        templateName: params.templateName,
      },
      body: requestBody,
    });
  }

  return (
    <Card className='col-span-2 w-full'>
      <CardHeader>
        <CardTitle>Edit Template</CardTitle>
        <CardDescription>
          Edit the plugin template for the {params.pluginId}{' '}
          {getPluginTypeLabel(params.pluginType)} plugin.
          <br />
          Options that are set to final can not be overwritten by the user.
          <br />
          Options that are set to undefined will not be set in the template.
        </CardDescription>
      </CardHeader>
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <CardContent className='space-y-4'>
            <FormItem>
              <FormLabel>Template Name</FormLabel>
              <Input value={params.templateName} disabled />
              <FormDescription>The name of the template.</FormDescription>
            </FormItem>
            {plugin?.options?.map((option) => {
              const isNotSet = form.watch(`${option.name}_isNotSet`);

              return (
                <FormItem key={option.name}>
                  <FormLabel>
                    {option.name}
                    <Badge className='ml-2 bg-blue-200 text-black'>
                      {option.type}
                    </Badge>
                  </FormLabel>
                  <div
                    style={{ display: 'flex', alignItems: 'center', gap: 8 }}
                  >
                    <FormField
                      control={form.control}
                      name={option.name}
                      render={({ field }) => (
                        <FormControl>
                          {option.type === 'BOOLEAN' ? (
                            <Switch
                              checked={field.value as boolean}
                              onCheckedChange={(checked) => {
                                field.onChange(checked);
                                form.setValue(`${option.name}_isNotSet`, false);
                              }}
                            />
                          ) : option.isRequired ? (
                            <Input
                              {...field}
                              type={
                                option.type === 'INTEGER' ||
                                option.type === 'LONG'
                                  ? 'number'
                                  : 'text'
                              }
                              value={
                                typeof field.value === 'string' ||
                                typeof field.value === 'number'
                                  ? field.value
                                  : ''
                              }
                              onChange={(e) => {
                                field.onChange(e);
                                handleValueChange(option.name, e.target.value);
                              }}
                            />
                          ) : (
                            <OptionalInput
                              {...field}
                              type={
                                option.type === 'INTEGER' ||
                                option.type === 'LONG'
                                  ? 'number'
                                  : 'text'
                              }
                              onChange={(e: ChangeEvent<HTMLInputElement>) => {
                                field.onChange(e);
                                handleValueChange(option.name, e.target.value);
                              }}
                            />
                          )}
                        </FormControl>
                      )}
                    />
                    <FormField
                      control={form.control}
                      name={`${option.name}_isFinal`}
                      render={({ field }) => (
                        <label
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: 4,
                          }}
                        >
                          <Checkbox
                            checked={field.value as CheckedState}
                            onCheckedChange={field.onChange}
                            disabled={Boolean(isNotSet)}
                          />
                          <p className='text-sm'>Final</p>
                        </label>
                      )}
                    />
                    <FormField
                      control={form.control}
                      name={`${option.name}_isNotSet`}
                      render={({ field }) => (
                        <label
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: 4,
                          }}
                        >
                          <Checkbox
                            checked={field.value as CheckedState}
                            onCheckedChange={(checked) => {
                              field.onChange(checked);
                              if (checked) {
                                form.setValue(
                                  option.name,
                                  option.type === 'BOOLEAN'
                                    ? false
                                    : (option.defaultValue ?? '')
                                );
                              }
                            }}
                          />
                          <p className='text-sm'>Undefined</p>
                        </label>
                      )}
                    />
                  </div>
                  <FormDescription>
                    {option.description}
                    {option.type === 'SECRET' && (
                      <>
                        <br />
                        <span className='text-red-500'>
                          Enter the name of the secret, not the value! A secret
                          with this name must be configured in the context of
                          the ORT run.
                        </span>
                      </>
                    )}
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              );
            })}
          </CardContent>
          <CardFooter className='mt-6 gap-4'>
            <Button type='submit' disabled={isUpdateTemplatePending}>
              {isUpdateTemplatePending ? (
                <>
                  <span className='sr-only'>Updating Template...</span>
                  <Loader2 size={16} className='mx-3 animate-spin' />
                </>
              ) : (
                'Update Template'
              )}
            </Button>
            <Button
              type='button'
              variant='outline'
              onClick={() =>
                navigate({
                  to: '/admin/plugins/$pluginType/$pluginId',
                  params: {
                    pluginType: params.pluginType,
                    pluginId: params.pluginId,
                  },
                })
              }
              disabled={isUpdateTemplatePending}
            >
              Cancel
            </Button>
          </CardFooter>
        </form>
      </Form>
    </Card>
  );
};

export const Route = createFileRoute(
  '/admin/plugins/$pluginType/$pluginId/edit-template/$templateName/'
)({
  loader: async ({ context: { queryClient }, params }) => {
    await queryClient.ensureQueryData({
      ...getPluginTemplateOptions({
        path: {
          pluginType: params.pluginType,
          pluginId: params.pluginId,
          templateName: params.templateName,
        },
      }),
    });
  },
  component: EditTemplate,
});
