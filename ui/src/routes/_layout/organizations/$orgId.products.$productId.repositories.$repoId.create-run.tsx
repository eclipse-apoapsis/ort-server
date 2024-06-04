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
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { useRepositoriesServicePostOrtRun } from '@/api/queries';
import { ApiError, RepositoriesService } from '@/api/requests';
import { ToastError } from '@/components/toast-error';
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
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
import { Label } from '@/components/ui/label';
import { Separator } from '@/components/ui/separator';
import { Switch } from '@/components/ui/switch';
import { useToast } from '@/components/ui/use-toast';

const formSchema = z.object({
  revision: z.string(),
  jobConfigs: z.object({
    analyzer: z.object({
      enabled: z.boolean(),
      allowDynamicVersions: z.boolean(),
      skipExcluded: z.boolean(),
      enabledPackageManagers: z.array(z.string()),
    }),
    advisor: z.object({
      enabled: z.boolean(),
      skipExcluded: z.boolean(),
      advisors: z.array(z.string()),
    }),
    scanner: z.object({
      enabled: z.boolean(),
      skipConcluded: z.boolean(),
      skipExcluded: z.boolean(),
    }),
    evaluator: z.object({
      enabled: z.boolean(),
    }),
    reporter: z.object({
      enabled: z.boolean(),
      formats: z.array(z.string()),
    }),
  }),
});

const advisors = [
  {
    id: 'OssIndex',
    label: 'OSS Index',
  },
  {
    id: 'OSV',
    label: 'OSV',
  },
  {
    id: 'VulnerableCode',
    label: 'VulnerableCode',
  },
] as const;

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
  const ortRun = Route.useLoaderData();

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

  // Default values for the form: edit only these, not the defaultValues object.
  const baseDefaults = {
    revision: 'main',
    jobConfigs: {
      analyzer: {
        enabled: true,
        allowDynamicVersions: true,
        skipExcluded: true,
        enabledPackageManagers: packageManagers.map((pm) => pm.id),
      },
      advisor: {
        enabled: true,
        skipExcluded: true,
        advisors: ['OSV', 'VulnerableCode'],
      },
      scanner: {
        enabled: true,
        skipConcluded: true,
        skipExcluded: true,
      },
      evaluator: {
        enabled: true,
      },
      reporter: {
        enabled: true,
        formats: ['ortresult', 'WebApp'],
      },
    },
  };

  // Default values for the form are either taken from "baseDefaults" or,
  // when a rerun action has been taken, fetched from the ORT Run that is
  // being rerun. Whenever a rerun job config parameter is missing, use the
  // default value.
  const defaultValues = ortRun
    ? {
        revision: ortRun.revision || baseDefaults.revision,
        jobConfigs: {
          analyzer: {
            enabled: baseDefaults.jobConfigs.analyzer.enabled,
            allowDynamicVersions:
              ortRun.jobConfigs.analyzer?.allowDynamicVersions ||
              baseDefaults.jobConfigs.analyzer.allowDynamicVersions,
            skipExcluded:
              ortRun.jobConfigs.analyzer?.skipExcluded ||
              baseDefaults.jobConfigs.analyzer.skipExcluded,
            enabledPackageManagers:
              ortRun.jobConfigs.analyzer?.enabledPackageManagers ||
              baseDefaults.jobConfigs.analyzer.enabledPackageManagers,
          },
          advisor: {
            enabled:
              ortRun.jobConfigs.advisor !== undefined &&
              ortRun.jobConfigs.advisor !== null,
            skipExcluded:
              ortRun.jobConfigs.advisor?.skipExcluded ||
              baseDefaults.jobConfigs.advisor.skipExcluded,
            advisors:
              ortRun.jobConfigs.advisor?.advisors ||
              baseDefaults.jobConfigs.advisor.advisors,
          },
          scanner: {
            enabled:
              ortRun.jobConfigs.scanner !== undefined &&
              ortRun.jobConfigs.scanner !== null,
            skipConcluded:
              ortRun.jobConfigs.scanner?.skipConcluded ||
              baseDefaults.jobConfigs.scanner.skipConcluded,
            skipExcluded:
              ortRun.jobConfigs.scanner?.skipExcluded ||
              baseDefaults.jobConfigs.scanner.skipExcluded,
          },
          evaluator: {
            enabled:
              ortRun.jobConfigs.evaluator !== undefined &&
              ortRun.jobConfigs.evaluator !== null,
          },
          reporter: {
            enabled:
              ortRun.jobConfigs.reporter !== undefined &&
              ortRun.jobConfigs.reporter !== null,
            formats:
              ortRun.jobConfigs.reporter?.formats ||
              baseDefaults.jobConfigs.reporter.formats,
          },
        },
      }
    : baseDefaults;

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: defaultValues,
  });

  async function onSubmit(values: z.infer<typeof formSchema>) {
    // In ORT Server, running or not running a job for and ORT Run is decided
    // based on the presence or absence of the corresponding job configuration
    // in the request body. If a job is disabled in the UI, we pass "undefined"
    // as the configuration for that job in the request body, in effect leaving
    // it empty, and thus disabling the job.
    const analyzerConfig = values.jobConfigs.analyzer.enabled
      ? {
          allowDynamicVersions: values.jobConfigs.analyzer.allowDynamicVersions,
          skipExcluded: values.jobConfigs.analyzer.skipExcluded,
          enabledPackageManagers:
            values.jobConfigs.analyzer.enabledPackageManagers,
        }
      : undefined;
    const advisorConfig = values.jobConfigs.advisor.enabled
      ? {
          skipExcluded: values.jobConfigs.advisor.skipExcluded,
          advisors: values.jobConfigs.advisor.advisors,
        }
      : undefined;
    const scannerConfig = values.jobConfigs.scanner.enabled
      ? {
          createMissingArchives: true,
          skipConcluded: values.jobConfigs.scanner.skipConcluded,
          skipExcluded: values.jobConfigs.scanner.skipExcluded,
        }
      : undefined;
    const evaluatorConfig = values.jobConfigs.evaluator.enabled
      ? {}
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
          advisor: advisorConfig,
          scanner: scannerConfig,
          evaluator: evaluatorConfig,
          reporter: reporterConfig,
        },
      },
    });
  }

  return (
    <Card className='mx-auto w-full max-w-4xl'>
      <CardHeader>
        <CardTitle>Create an ORT run</CardTitle>
      </CardHeader>
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className='space-y-8'>
          <CardContent>
            <FormField
              control={form.control}
              name='revision'
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

            <h3 className='mt-4'>Enable and configure jobs</h3>
            <div className='text-sm text-gray-500'>
              Configure the jobs to be included in the ORT Run.{' '}
            </div>
            <div className='text-sm text-gray-500'>
              NOTE: Currently, the Analyzer needs to always run as part of an
              ORT Run.
            </div>

            <Accordion type='multiple'>
              {/* Analyzer job */}
              <div className='flex flex-row align-middle'>
                <FormField
                  control={form.control}
                  name='jobConfigs.analyzer.enabled'
                  render={({ field }) => (
                    <FormControl>
                      <Switch
                        className='my-4 mr-4 data-[state=checked]:bg-green-500'
                        checked={field.value}
                        disabled
                        onCheckedChange={field.onChange}
                      />
                    </FormControl>
                  )}
                />
                <AccordionItem value='analyzer' className='flex-1'>
                  <AccordionTrigger>Analyzer</AccordionTrigger>
                  <AccordionContent>
                    <FormField
                      control={form.control}
                      name='jobConfigs.analyzer.allowDynamicVersions'
                      render={({ field }) => (
                        <FormItem className='mb-4 flex flex-row items-center justify-between rounded-lg border p-4'>
                          <div className='space-y-0.5'>
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
                      name='jobConfigs.analyzer.skipExcluded'
                      render={({ field }) => (
                        <FormItem className='mb-4 flex flex-row items-center justify-between rounded-lg border p-4'>
                          <div className='space-y-0.5'>
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
                      name='jobConfigs.analyzer.enabledPackageManagers'
                      render={() => (
                        <FormItem className='mb-4 flex flex-col justify-between rounded-lg border p-4'>
                          <FormLabel>Enabled package managers</FormLabel>
                          <FormDescription className='pb-4'>
                            Select the package managers enabled for this ORT
                            Run.
                          </FormDescription>
                          <div className='flex items-center space-x-3'>
                            <Checkbox
                              id='check-all-pms'
                              checked={
                                packageManagers.every((pm) =>
                                  form
                                    .getValues(
                                      'jobConfigs.analyzer.enabledPackageManagers'
                                    )
                                    .includes(pm.id)
                                )
                                  ? true
                                  : packageManagers.some((pm) =>
                                        form
                                          .getValues(
                                            'jobConfigs.analyzer.enabledPackageManagers'
                                          )
                                          .includes(pm.id)
                                      )
                                    ? 'indeterminate'
                                    : false
                              }
                              onCheckedChange={(checked) => {
                                const enabledPackageManagers = checked
                                  ? packageManagers.map((pm) => pm.id)
                                  : [];
                                form.setValue(
                                  'jobConfigs.analyzer.enabledPackageManagers',
                                  enabledPackageManagers
                                );
                              }}
                            />
                            <Label
                              htmlFor='check-all-pms'
                              className='font-bold'
                            >
                              Enable/disable all
                            </Label>
                          </div>
                          <Separator />
                          {packageManagers.map((pm) => (
                            <FormField
                              key={pm.id}
                              control={form.control}
                              name='jobConfigs.analyzer.enabledPackageManagers'
                              render={({ field }) => {
                                return (
                                  <FormItem
                                    key={pm.id}
                                    className='flex flex-row items-start space-x-3 space-y-0'
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
                                    <FormLabel className='font-normal'>
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

              {/* Advisor job */}
              <div className='flex flex-row align-middle'>
                <FormField
                  control={form.control}
                  name='jobConfigs.advisor.enabled'
                  render={({ field }) => (
                    <FormControl>
                      <Switch
                        className='my-4 mr-4 data-[state=checked]:bg-green-500'
                        checked={field.value}
                        onCheckedChange={field.onChange}
                      />
                    </FormControl>
                  )}
                />
                <AccordionItem value='advisor' className='flex-1'>
                  <AccordionTrigger>Advisor</AccordionTrigger>
                  <AccordionContent>
                    <FormField
                      control={form.control}
                      name='jobConfigs.advisor.skipExcluded'
                      render={({ field }) => (
                        <FormItem className='mb-4 flex flex-row items-center justify-between rounded-lg border p-4'>
                          <div className='space-y-0.5'>
                            <FormLabel>Skip excluded</FormLabel>
                            <FormDescription>
                              A flag to control whether excluded scopes and
                              paths should be skipped from the advisor.
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
                      name='jobConfigs.advisor.advisors'
                      render={() => (
                        <FormItem className='mb-4 flex flex-col justify-between rounded-lg border p-4'>
                          <FormLabel>Enabled advisors</FormLabel>
                          <FormDescription className='pb-4'>
                            Select the advisors enabled for this ORT Run.
                          </FormDescription>
                          <div className='flex items-center space-x-3'>
                            <Checkbox
                              id='check-all-ads'
                              checked={
                                advisors.every((ad) =>
                                  form
                                    .getValues('jobConfigs.advisor.advisors')
                                    .includes(ad.id)
                                )
                                  ? true
                                  : advisors.some((ad) =>
                                        form
                                          .getValues(
                                            'jobConfigs.advisor.advisors'
                                          )
                                          .includes(ad.id)
                                      )
                                    ? 'indeterminate'
                                    : false
                              }
                              onCheckedChange={(checked) => {
                                const enabledAdvisors = checked
                                  ? advisors.map((ad) => ad.id)
                                  : [];
                                form.setValue(
                                  'jobConfigs.advisor.advisors',
                                  enabledAdvisors
                                );
                              }}
                            />
                            <Label
                              htmlFor='check-all-ads'
                              className='font-bold'
                            >
                              Enable/disable all
                            </Label>
                          </div>
                          <Separator />
                          {advisors.map((ad) => (
                            <FormField
                              key={ad.id}
                              control={form.control}
                              name='jobConfigs.advisor.advisors'
                              render={({ field }) => {
                                return (
                                  <FormItem
                                    key={ad.id}
                                    className='flex flex-row items-start space-x-3 space-y-0'
                                  >
                                    <FormControl>
                                      <Checkbox
                                        checked={field.value?.includes(ad.id)}
                                        onCheckedChange={(checked) => {
                                          return checked
                                            ? field.onChange([
                                                ...field.value,
                                                ad.id,
                                              ])
                                            : field.onChange(
                                                field.value?.filter(
                                                  (value) => value !== ad.id
                                                )
                                              );
                                        }}
                                      />
                                    </FormControl>
                                    <FormLabel className='font-normal'>
                                      {ad.label}
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
              <div className='flex flex-row align-middle'>
                <FormField
                  control={form.control}
                  name='jobConfigs.scanner.enabled'
                  render={({ field }) => (
                    <FormControl>
                      <Switch
                        className='my-4 mr-4 data-[state=checked]:bg-green-500'
                        checked={field.value}
                        onCheckedChange={field.onChange}
                      />
                    </FormControl>
                  )}
                />
                <AccordionItem value='scanner' className='flex-1'>
                  <AccordionTrigger>Scanner</AccordionTrigger>
                  <AccordionContent>
                    <FormField
                      control={form.control}
                      name='jobConfigs.scanner.skipConcluded'
                      render={({ field }) => (
                        <FormItem className='mb-4 flex flex-row items-center justify-between rounded-lg border p-4'>
                          <div className='space-y-0.5'>
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
                      name='jobConfigs.scanner.skipExcluded'
                      render={({ field }) => (
                        <FormItem className='mb-4 flex flex-row items-center justify-between rounded-lg border p-4'>
                          <div className='space-y-0.5'>
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

              {/* Evaluator job */}
              <div className='flex flex-row align-middle'>
                <FormField
                  control={form.control}
                  name='jobConfigs.evaluator.enabled'
                  render={({ field }) => (
                    <FormControl>
                      <Switch
                        className='my-4 mr-4 data-[state=checked]:bg-green-500'
                        checked={field.value}
                        onCheckedChange={field.onChange}
                      />
                    </FormControl>
                  )}
                />
                <AccordionItem value='evaluator' className='flex-1'>
                  <AccordionTrigger>Evaluator</AccordionTrigger>
                  <AccordionContent>
                    No job configurations yet implemented for Evaluator.
                  </AccordionContent>
                </AccordionItem>
              </div>

              {/* Reporter job */}
              <div className='flex flex-row align-middle'>
                <FormField
                  control={form.control}
                  name='jobConfigs.reporter.enabled'
                  render={({ field }) => (
                    <FormControl>
                      <Switch
                        className='my-4 mr-4 data-[state=checked]:bg-green-500'
                        checked={field.value}
                        onCheckedChange={field.onChange}
                      />
                    </FormControl>
                  )}
                />
                <AccordionItem value='reporter' className='flex-1'>
                  <AccordionTrigger>Reporter</AccordionTrigger>
                  <AccordionContent>
                    <FormField
                      control={form.control}
                      name='jobConfigs.reporter.formats'
                      render={() => (
                        <FormItem className='mb-4 flex flex-col justify-between rounded-lg border p-4'>
                          <FormLabel>Report formats</FormLabel>
                          <FormDescription className='pb-4'>
                            Select the report formats to generate from the ORT
                            Run.
                          </FormDescription>
                          <div className='flex items-center space-x-3'>
                            <Checkbox
                              id='check-all-formats'
                              checked={
                                reportFormats.every((format) =>
                                  form
                                    .getValues('jobConfigs.reporter.formats')
                                    .includes(format.id)
                                )
                                  ? true
                                  : reportFormats.some((format) =>
                                        form
                                          .getValues(
                                            'jobConfigs.reporter.formats'
                                          )
                                          .includes(format.id)
                                      )
                                    ? 'indeterminate'
                                    : false
                              }
                              onCheckedChange={(checked) => {
                                const enabledReportFormats = checked
                                  ? reportFormats.map((format) => format.id)
                                  : [];
                                form.setValue(
                                  'jobConfigs.reporter.formats',
                                  enabledReportFormats
                                );
                              }}
                            />
                            <Label
                              htmlFor='check-all-formats'
                              className='font-bold'
                            >
                              Enable/disable all
                            </Label>
                          </div>
                          <Separator />
                          {reportFormats.map((format) => (
                            <FormField
                              key={format.id}
                              control={form.control}
                              name='jobConfigs.reporter.formats'
                              render={({ field }) => {
                                return (
                                  <FormItem
                                    key={format.id}
                                    className='flex flex-row items-start space-x-3 space-y-0'
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
                                    <FormLabel className='font-normal'>
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
            <Button type='submit'>Create</Button>
          </CardFooter>
        </form>
      </Form>
    </Card>
  );
};

const rerunIndexSchema = z.object({
  rerunIndex: z.number().optional(),
});

export const Route = createFileRoute(
  '/_layout/organizations/$orgId/products/$productId/repositories/$repoId/create-run'
)({
  // This is used to access the search params in the loader.
  // As search params we use the index of the ORT run on which this run will be based.
  loaderDeps: ({ search: { rerunIndex } }) => ({ rerunIndex }),
  // The loader fetches the ORT Run that is being rerun.
  // It is important to notice that if no rerunIndex is provided to this route,
  // the query will not be run. This corresponds to the "New run" case, where a new
  // ORT Run is created from scratch, using all defaults.
  loader: async ({ params, deps: { rerunIndex } }) => {
    if (rerunIndex === undefined) {
      return null;
    }
    const ortRun = await RepositoriesService.getOrtRunByIndex({
      repositoryId: Number.parseInt(params.repoId),
      ortRunIndex: rerunIndex,
    });
    return ortRun;
  },
  component: CreateRunPage,
  validateSearch: rerunIndexSchema,
});
