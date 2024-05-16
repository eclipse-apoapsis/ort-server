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

import { useRepositoriesServicePostOrtRun } from '@/api/queries';
import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
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
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { useToast } from "@/components/ui/use-toast";
import { ApiError } from '@/api/requests';
import { ToastError } from '@/components/toast-error';
import { Switch } from '@/components/ui/switch';
import { Checkbox } from '@/components/ui/checkbox';

const formSchema = z.object({
  revision: z.string(),
  jobConfigs: z.object({
    analyzer: z.object({
      allowDynamicVersions: z.boolean(),
      skipExcluded: z.boolean(),
    }),
    reporter: z.object({
      formats: z.array(z.string()),
    }),
  }),
});

const reportFormats = [
  {
    id: "AsciiDocTemplate",
    label: "AsciiDoc Template",
  },
  {
    id: "ortresult",
    label: "ORT Result",
  },
  {
    id: "PlainTextTemplate",
    label: "NOTICE file",
  },
  {
    id: "SpdxDocument",
    label: "SPDX Document",
  },
  {
    id: "WebApp",
    label: "Web App",
  },
] as const

const CreateRunPage = () => {
  const navigate = useNavigate();
  const params = Route.useParams();
  const { toast } = useToast();

  const { mutateAsync } = useRepositoriesServicePostOrtRun({
    onSuccess() {
      toast({
        title: 'Create ORT Run',
        description: 'New ORT run created successfully for this repository.',
      });
      navigate({
        to: '/organizations/$orgId/products/$productId/repositories/$repoId',
        params: {
          orgId: params.orgId,
          productId: params.productId,
          repoId: params.repoId,
        },
      });
    },
    onError(error: ApiError) {
      toast({
        title: error.message,
        description: <ToastError message={(error.body as any).message} cause={(error.body as any).cause} />,
        variant: 'destructive',
      });
    }
  });

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      revision: 'main',
      jobConfigs: {
        analyzer: {
          allowDynamicVersions: false,
          skipExcluded: false,
        },
        reporter: {
          formats: ["ortresult"],
        },
      },
    },
  });

  async function onSubmit(values: z.infer<typeof formSchema>) {
    await mutateAsync({
      repositoryId: Number.parseInt(params.repoId),
      requestBody: {
        revision: values.revision,
        jobConfigs: {
          analyzer: {
            allowDynamicVersions: values.jobConfigs.analyzer.allowDynamicVersions,
            skipExcluded: values.jobConfigs.analyzer.skipExcluded,
          },
          reporter: {
            formats: values.jobConfigs.reporter.formats,
          },
        },
      },
    });
  }

  return (
    <Card className="w-full max-w-4xl mx-auto">
      <CardHeader>
        <CardTitle>Create an ORT run</CardTitle>
      </CardHeader>
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-8">
          <CardContent>
            <FormField
              control={form.control}
              name="revision"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Revision</FormLabel>
                  <FormControl>
                    <Input {...field} />
                  </FormControl>
                  <FormDescription>Revision to run ORT on</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <h3 className="my-4 font-medium">Analyzer</h3>
            
            <FormField
              control={form.control}
              name="jobConfigs.analyzer.allowDynamicVersions"
              render={({ field }) => (
                <FormItem className="flex flex-row items-center justify-between p-4 mb-4 border rounded-lg">
                  <div className="space-y-0.5">
                    <FormLabel>
                      Allow dynamic versions
                    </FormLabel>
                    <FormDescription>
                      Enable the analysis of projects that use version ranges to declare their dependencies.
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

            <FormField
              control={form.control}
              name="jobConfigs.analyzer.skipExcluded"
              render={({ field }) => (
                <FormItem className="flex flex-row items-center justify-between p-4 mb-4 border rounded-lg">
                  <div className="space-y-0.5">
                    <FormLabel>
                      Skip excluded
                    </FormLabel>
                    <FormDescription>
                      A flag to control whether excluded scopes and paths should be skipped during the analysis.
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

            <h3 className="my-4 font-medium">Reporter</h3>

            <FormField
              control={form.control}
              name="jobConfigs.reporter.formats"
              render={() => (
                <FormItem className="flex flex-col justify-between p-4 mb-4 border rounded-lg">
                  <FormLabel>Report formats</FormLabel>
                  <FormDescription className="pb-4">Select the report formats to generate from the ORT Run</FormDescription>
                  {reportFormats.map((format) => (
                    <FormField
                      key={format.id}
                      control={form.control}
                      name="jobConfigs.reporter.formats"
                      render={({ field }) => {
                        return (
                          <FormItem
                            key={format.id}
                            className="flex flex-row items-start space-x-3 space-y-0"
                          >
                            <FormControl>
                              <Checkbox
                                checked={field.value?.includes(format.id)}
                                onCheckedChange={(checked) => {
                                  return checked
                                    ? field.onChange([...field.value, format.id])
                                    : field.onChange(
                                        field.value?.filter(
                                          (value) => value !== format.id
                                        )
                                      )
                                }}
                              />
                            </FormControl>
                            <FormLabel className="font-normal">
                              {format.label}
                            </FormLabel>
                          </FormItem>
                        )
                      }}
                    />
                  ))}  
                  <FormMessage />
                </FormItem>
              )}
            />
          </CardContent>
          <CardFooter>
            <Button type="submit">Create</Button>
          </CardFooter>
        </form>
      </Form>
    </Card>
  );
};

export const Route = createFileRoute(
  '/_layout/organizations/$orgId/products/$productId/repositories/$repoId/create-run'
)({
  component: CreateRunPage,
});
