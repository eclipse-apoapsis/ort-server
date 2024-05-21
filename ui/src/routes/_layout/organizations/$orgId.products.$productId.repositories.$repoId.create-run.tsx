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
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion';
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
import { useToast } from '@/components/ui/use-toast';
import { ApiError } from '@/api/requests';
import { ToastError } from '@/components/toast-error';
import { Switch } from '@/components/ui/switch';
import { Checkbox } from '@/components/ui/checkbox';

const formSchema = z.object({
  revision: z.string(),
  jobConfigs: z.object({
    analyzer: z.object({
      enabled: z.boolean(),
      allowDynamicVersions: z.boolean(),
      skipExcluded: z.boolean(),
      enabledPackageManagers: z.array(z.string()),
    }),
    scanner: z.object({
      enabled: z.boolean(),
      skipConcluded: z.boolean(),
      skipExcluded: z.boolean(),
    }),
    reporter: z.object({
      enabled: z.boolean(),
      formats: z.array(z.string()),
    }),
  }),
});

const packageManagers = [
  {
    id: 'Bundler',
    label: 'Bundler (Ruby)',
  },
  {
    id: 'Cargo',
    label: 'Cargo (Rust)',
  },
  {
    id: 'GoMod',
    label: 'GoMod (Go)',
  },
  {
    id: 'GradleInspector',
    label: 'Gradle (Java)',
  },
  {
    id: 'Maven',
    label: 'Maven (Java)',
  },
  {
    id: 'NPM',
    label: 'NPM (JavaScript / Node.js)',
  },
  {
    id: 'PIP',
    label: 'PIP (Python)',
  },
  {
    id: 'Pipenv',
    label: 'Pipenv (Python)',
  },
  {
    id: 'PNPM',
    label: 'PNPM (JavaScript / Node.js)',
  },
  {
    id: 'Poetry',
    label: 'Poetry (Python)',
  },
  {
    id: 'Yarn',
    label: 'Yarn 1 (JavaScript / Node.js)',
  },
  {
    id: 'Yarn2',
    label: 'Yarn 2+ (JavaScript / Node.js)',
  },
] as const;

const reportFormats = [
  {
    id: 'AsciiDocTemplate',
    label: 'AsciiDoc Template',
  },
  {
    id: 'ortresult',
    label: 'ORT Result',
  },
  {
    id: 'PlainTextTemplate',
    label: 'NOTICE file',
  },
  {
    id: 'SpdxDocument',
    label: 'SPDX Document',
  },
  {
    id: 'WebApp',
    label: 'Web App',
  },
] as const;

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
        description: <ToastError error={error} />,
        variant: 'destructive',
      });
    },
  });

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      revision: 'main',
      jobConfigs: {
        analyzer: {
          enabled: true,
          allowDynamicVersions: true,
          skipExcluded: true,
          enabledPackageManagers: packageManagers.map((pm) => pm.id),
        },
        scanner: {
          enabled: true,
          skipConcluded: true,
          skipExcluded: true,
        },
        reporter: {
          enabled: true,
          formats: ['ortresult', 'WebApp'],
        },
      },
    },
  });

  async function onSubmit(values: z.infer<typeof formSchema>) {
    const analyzerConfig = values.jobConfigs.analyzer.enabled
      ? {
          allowDynamicVersions: values.jobConfigs.analyzer.allowDynamicVersions,
          skipExcluded: values.jobConfigs.analyzer.skipExcluded,
          enabledPackageManagers:
            values.jobConfigs.analyzer.enabledPackageManagers,
        }
      : undefined;
    const scannerConfig = values.jobConfigs.scanner.enabled
      ? {
          createMissingArchives: true,
          skipConcluded: values.jobConfigs.scanner.skipConcluded,
          skipExcluded: values.jobConfigs.scanner.skipExcluded,
        }
      : undefined;
    const reporterConfig = values.jobConfigs.reporter.enabled
      ? {
          formats: values.jobConfigs.reporter.formats,
        }
      : undefined;

    await mutateAsync({
      repositoryId: Number.parseInt(params.repoId),
      requestBody: {
        revision: values.revision,
        jobConfigs: {
          analyzer: analyzerConfig,
          scanner: scannerConfig,
          reporter: reporterConfig,
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

            <h3 className="mt-4">Enable and configure jobs</h3>

            <Accordion type="multiple">
              {/* Analyzer job */}
              <div className="flex flex-row align-middle">
                <FormField
                  control={form.control}
                  name="jobConfigs.analyzer.enabled"
                  render={({ field }) => (
                    <FormControl>
                      <Switch
                        className="data-[state=checked]:bg-green-500 mr-4 my-4"
                        checked={field.value}
                        onCheckedChange={field.onChange}
                      />
                    </FormControl>
                  )}
                />
                <AccordionItem value="analyzer" className="flex-1">
                  <AccordionTrigger>Analyzer</AccordionTrigger>
                  <AccordionContent>
                    <FormField
                      control={form.control}
                      name="jobConfigs.analyzer.allowDynamicVersions"
                      render={({ field }) => (
                        <FormItem className="flex flex-row items-center justify-between p-4 mb-4 border rounded-lg">
                          <div className="space-y-0.5">
                            <FormLabel>Allow dynamic versions</FormLabel>
                            <FormDescription>
                              Enable the analysis of projects that use version
                              ranges to declare their dependencies.
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
                            <FormLabel>Skip excluded</FormLabel>
                            <FormDescription>
                              A flag to control whether excluded scopes and
                              paths should be skipped during the analysis.
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
                      name="jobConfigs.analyzer.enabledPackageManagers"
                      render={() => (
                        <FormItem className="flex flex-col justify-between p-4 mb-4 border rounded-lg">
                          <FormLabel>Enabled package managers</FormLabel>
                          <FormDescription className="pb-4">
                            Select the package managers enabled for this ORT
                            Run.
                          </FormDescription>
                          {packageManagers.map((pm) => (
                            <FormField
                              key={pm.id}
                              control={form.control}
                              name="jobConfigs.analyzer.enabledPackageManagers"
                              render={({ field }) => {
                                return (
                                  <FormItem
                                    key={pm.id}
                                    className="flex flex-row items-start space-x-3 space-y-0"
                                  >
                                    <FormControl>
                                      <Checkbox
                                        checked={field.value?.includes(pm.id)}
                                        onCheckedChange={(checked) => {
                                          return checked
                                            ? field.onChange([
                                                ...field.value,
                                                pm.id,
                                              ])
                                            : field.onChange(
                                                field.value?.filter(
                                                  (value) => value !== pm.id
                                                )
                                              );
                                        }}
                                      />
                                    </FormControl>
                                    <FormLabel className="font-normal">
                                      {pm.label}
                                    </FormLabel>
                                  </FormItem>
                                );
                              }}
                            />
                          ))}
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                  </AccordionContent>
                </AccordionItem>
              </div>

              {/* Scanner job */}
              <div className="flex flex-row align-middle">
                <FormField
                  control={form.control}
                  name="jobConfigs.scanner.enabled"
                  render={({ field }) => (
                    <FormControl>
                      <Switch
                        className="data-[state=checked]:bg-green-500 mr-4 my-4"
                        checked={field.value}
                        onCheckedChange={field.onChange}
                      />
                    </FormControl>
                  )}
                />
                <AccordionItem value="scanner" className="flex-1">
                  <AccordionTrigger>Scanner</AccordionTrigger>
                  <AccordionContent>
                    <FormField
                      control={form.control}
                      name="jobConfigs.scanner.skipConcluded"
                      render={({ field }) => (
                        <FormItem className="flex flex-row items-center justify-between p-4 mb-4 border rounded-lg">
                          <div className="space-y-0.5">
                            <FormLabel>Skip concluded</FormLabel>
                            <FormDescription>
                              A flag to indicate whether packages that have a
                              concluded license and authors set (to derive
                              copyrights from) should be skipped in the scan in
                              favor of only using the declared information.
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
                      name="jobConfigs.scanner.skipExcluded"
                      render={({ field }) => (
                        <FormItem className="flex flex-row items-center justify-between p-4 mb-4 border rounded-lg">
                          <div className="space-y-0.5">
                            <FormLabel>Skip excluded</FormLabel>
                            <FormDescription>
                              Do not scan excluded projects or packages.
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
                  </AccordionContent>
                </AccordionItem>
              </div>

              {/* Reporter job */}
              <div className="flex flex-row align-middle">
                <FormField
                  control={form.control}
                  name="jobConfigs.reporter.enabled"
                  render={({ field }) => (
                    <FormControl>
                      <Switch
                        className="data-[state=checked]:bg-green-500 mr-4 my-4"
                        checked={field.value}
                        onCheckedChange={field.onChange}
                      />
                    </FormControl>
                  )}
                />
                <AccordionItem value="reporter" className="flex-1">
                  <AccordionTrigger>Reporter</AccordionTrigger>
                  <AccordionContent>
                    <FormField
                      control={form.control}
                      name="jobConfigs.reporter.formats"
                      render={() => (
                        <FormItem className="flex flex-col justify-between p-4 mb-4 border rounded-lg">
                          <FormLabel>Report formats</FormLabel>
                          <FormDescription className="pb-4">
                            Select the report formats to generate from the ORT
                            Run.
                          </FormDescription>
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
                                        checked={field.value?.includes(
                                          format.id
                                        )}
                                        onCheckedChange={(checked) => {
                                          return checked
                                            ? field.onChange([
                                                ...field.value,
                                                format.id,
                                              ])
                                            : field.onChange(
                                                field.value?.filter(
                                                  (value) => value !== format.id
                                                )
                                              );
                                        }}
                                      />
                                    </FormControl>
                                    <FormLabel className="font-normal">
                                      {format.label}
                                    </FormLabel>
                                  </FormItem>
                                );
                              }}
                            />
                          ))}
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                  </AccordionContent>
                </AccordionItem>
              </div>
            </Accordion>
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
