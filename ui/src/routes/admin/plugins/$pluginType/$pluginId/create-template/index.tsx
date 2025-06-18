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
import {
  createFileRoute,
  useLoaderData,
  useNavigate,
} from '@tanstack/react-router';
import { Loader2 } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { z, ZodTypeAny } from 'zod';

import { usePluginsServicePostApiV1AdminPluginsByPluginTypeByPluginIdTemplatesByTemplateName } from '@/api/queries';
import {
  ApiError,
  PluginOption,
  PluginOptionTemplate,
  PluginOptionType,
} from '@/api/requests';
import { OptionalInput } from '@/components/form/optional-input';
import { ToastError } from '@/components/toast-error';
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
import { toast } from '@/lib/toast';
import { Route as LayoutRoute } from '../../../route.tsx';

function optionTypeToZodType(type: PluginOptionType): ZodTypeAny {
  switch (type) {
    case 'BOOLEAN':
      return z.boolean();
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

const templateName = 'Template Name';

function buildFormSchema(options: Array<PluginOption>) {
  const shape: Record<string, ZodTypeAny> = {};
  shape[templateName] = z.string().min(1);
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

type FormValues = {
  [templateName]: string;
  [key: string]: unknown;
};

const CreateTemplate = () => {
  const navigate = useNavigate();
  const { plugins } = useLoaderData({ from: LayoutRoute.id });
  const params = Route.useParams();

  const plugin = plugins.find(
    (p) => p.type === params.pluginType && p.id === params.pluginId
  );

  const formSchema = plugin?.options
    ? buildFormSchema(plugin.options)
    : z.object({ [templateName]: z.string().min(1) });

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      [templateName]: '',
      ...(plugin?.options?.reduce(
        (acc, option) => {
          acc[option.name] = option.defaultValue ?? '';
          acc[`${option.name}_isFinal`] = false;
          acc[`${option.name}_isNotSet`] = false;
          return acc;
        },
        {} as Record<string, unknown>
      ) || {}),
    },
  });

  const { mutateAsync: createTemplate, isPending: isCreateTemplatePending } =
    usePluginsServicePostApiV1AdminPluginsByPluginTypeByPluginIdTemplatesByTemplateName(
      {
        onSuccess() {
          toast.info('Create Template', {
            description: `Template created successfully.`,
          });
          navigate({
            to: '/admin/plugins/$pluginType/$pluginId',
            params: {
              pluginType: params.pluginType,
              pluginId: params.pluginId,
            },
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

  async function onSubmit(values: z.infer<typeof formSchema>) {
    const formValues = values as FormValues;
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

    await createTemplate({
      pluginType: params.pluginType,
      pluginId: params.pluginId,
      templateName: formValues[templateName],
      requestBody: requestBody,
    });
  }

  if (plugin === undefined) {
    toast.error('Unable to find plugin', {
      description: <ToastError error='Plugin not found.' />,
      duration: Infinity,
      cancel: {
        label: 'Dismiss',
        onClick: () => {},
      },
    });
    return;
  }

  return (
    <Card className='col-span-2 w-full'>
      <CardHeader>
        <CardTitle>Create Template</CardTitle>
        <CardDescription>
          Create a new plugin template for the {params.pluginId}{' '}
          {params.pluginType} plugin.
          <br />
          Options that are set to final can not be overwritten by the user.
          <br />
          Options that are set to undefined will not be set in the template.
        </CardDescription>
      </CardHeader>
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <CardContent className='space-y-4'>
            <FormField
              key={templateName}
              control={form.control}
              name={templateName}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Template Name</FormLabel>
                  <FormControl>
                    <Input {...field} />
                  </FormControl>
                  <FormDescription>
                    The name of the template to create.
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            {plugin.options?.map((option) => {
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
                            <Checkbox {...field} />
                          ) : option.isRequired ? (
                            <Input
                              {...field}
                              type={
                                option.type === 'INTEGER' ||
                                option.type === 'LONG'
                                  ? 'number'
                                  : 'text'
                              }
                              disabled={isNotSet}
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
                              disabled={isNotSet}
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
                            checked={field.value}
                            onCheckedChange={field.onChange}
                            disabled={isNotSet}
                          />
                          isFinal
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
                            checked={field.value}
                            onCheckedChange={field.onChange}
                          />
                          undefined
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
          <CardFooter>
            <Button
              type='submit'
              disabled={isCreateTemplatePending}
              className='mt-4'
            >
              {isCreateTemplatePending ? (
                <>
                  <span className='sr-only'>Creating Template...</span>
                  <Loader2 size={16} className='mx-3 animate-spin' />
                </>
              ) : (
                'Create Template'
              )}
            </Button>
          </CardFooter>
        </form>
      </Form>
    </Card>
  );
};

export const Route = createFileRoute(
  '/admin/plugins/$pluginType/$pluginId/create-template/'
)({
  component: CreateTemplate,
});
