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
import { Loader2, PlusIcon, TrashIcon } from 'lucide-react';
import { useFieldArray, useForm } from 'react-hook-form';
import { z } from 'zod';

import { useRepositoriesServicePostOrtRun } from '@/api/queries';
import { ApiError, RepositoriesService } from '@/api/requests';
import { ToastError } from '@/components/toast-error';
import { Accordion } from '@/components/ui/accordion';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
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
import { useToast } from '@/components/ui/use-toast';
import { AdvisorFields } from './-components/advisor-fields';
import { AnalyzerFields } from './-components/analyzer-fields';
import { EvaluatorFields } from './-components/evaluator-fields';
import { ReporterFields } from './-components/reporter-fields';
import { ScannerFields } from './-components/scanner-fields';
import { packageManagers } from './-types';

const keyValueSchema = z.object({
  key: z.string().min(1),
  value: z.string(), // Allow empty values for now
});

const formSchema = z.object({
  revision: z.string(),
  path: z.string(),
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
      ruleSet: z.string().optional(),
      licenseClassificationsFile: z.string().optional(),
      copyrightGarbageFile: z.string().optional(),
      resolutionsFile: z.string().optional(),
    }),
    reporter: z.object({
      enabled: z.boolean(),
      formats: z.array(z.string()),
    }),
    parameters: z.array(keyValueSchema).optional(),
  }),
  labels: z.array(keyValueSchema).optional(),
  jobConfigContext: z.string().optional(),
});

export type CreateRunFormValues = ReturnType<typeof formSchema.parse>;

const CreateRunPage = () => {
  const navigate = useNavigate();
  const params = Route.useParams();
  const { toast } = useToast();
  const ortRun = Route.useLoaderData();

  const { mutateAsync, isPending } = useRepositoriesServicePostOrtRun({
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
    path: '',
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
    jobConfigContext: 'main',
  };

  // Default values for the form are either taken from "baseDefaults" or,
  // when a rerun action has been taken, fetched from the ORT Run that is
  // being rerun. Whenever a rerun job config parameter is missing, use the
  // default value.
  const defaultValues = ortRun
    ? {
        revision: ortRun.revision || baseDefaults.revision,
        path: ortRun.path || baseDefaults.path,
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
            ruleSet: ortRun.jobConfigs.evaluator?.ruleSet || undefined,
            licenseClassificationsFile:
              ortRun.jobConfigs.evaluator?.licenseClassificationsFile ||
              undefined,
            copyrightGarbageFile:
              ortRun.jobConfigs.evaluator?.copyrightGarbageFile || undefined,
            resolutionsFile:
              ortRun.jobConfigs.evaluator?.resolutionsFile || undefined,
          },
          reporter: {
            enabled:
              ortRun.jobConfigs.reporter !== undefined &&
              ortRun.jobConfigs.reporter !== null,
            formats:
              ortRun.jobConfigs.reporter?.formats ||
              baseDefaults.jobConfigs.reporter.formats,
          },
          // Convert the parameters object map coming from the back-end to an array of key-value pairs.
          // This needs to be done because the useFieldArray hook requires an array of objects.
          parameters: ortRun.jobConfigs.parameters
            ? Object.entries(ortRun.jobConfigs.parameters).map(([k, v]) => ({
                key: k,
                value: v,
              }))
            : [],
        },
        // Convert the labels object map coming from the back-end to an array of key-value pairs.
        labels: ortRun.labels
          ? Object.entries(ortRun.labels).map(([k, v]) => ({
              key: k,
              value: v,
            }))
          : [],
        jobConfigContext:
          ortRun.jobConfigContext || baseDefaults.jobConfigContext,
      }
    : baseDefaults;

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: defaultValues,
  });

  const {
    fields: parametersFields,
    append: parametersAppend,
    remove: parametersRemove,
  } = useFieldArray({
    name: 'jobConfigs.parameters',
    control: form.control,
  });

  const {
    fields: labelsFields,
    append: labelsAppend,
    remove: labelsRemove,
  } = useFieldArray({
    name: 'labels',
    control: form.control,
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
      ? {
          // Only include the config parameter structures if the corresponding form fields are not empty.
          // In case they are empty, the default path from the config file provider will be used to
          // resolve the corresponding files.
          ruleSet: values.jobConfigs.evaluator.ruleSet || undefined,
          licenseClassificationsFile:
            values.jobConfigs.evaluator.licenseClassificationsFile || undefined,
          copyrightGarbageFile:
            values.jobConfigs.evaluator.copyrightGarbageFile || undefined,
          resolutionsFile:
            values.jobConfigs.evaluator.resolutionsFile || undefined,
        }
      : undefined;
    const reporterConfig = values.jobConfigs.reporter.enabled
      ? {
          formats: values.jobConfigs.reporter.formats,
        }
      : undefined;

    // Convert the parameters and labels arrays back to objects, as expected by the back-end.
    const parameters = values.jobConfigs.parameters
      ? values.jobConfigs.parameters.reduce(
          (acc, param) => {
            acc[param.key] = param.value;
            return acc;
          },
          {} as { [key: string]: string }
        )
      : undefined;
    const labels = values.labels
      ? values.labels.reduce(
          (acc, label) => {
            acc[label.key] = label.value;
            return acc;
          },
          {} as { [key: string]: string }
        )
      : undefined;

    const requestBody = {
      revision: values.revision,
      path: values.path,
      jobConfigs: {
        analyzer: analyzerConfig,
        advisor: advisorConfig,
        scanner: scannerConfig,
        evaluator: evaluatorConfig,
        reporter: reporterConfig,
        parameters: parameters,
      },
      labels: labels,
      jobConfigContext: values.jobConfigContext,
    };

    await mutateAsync({
      repositoryId: Number.parseInt(params.repoId),
      requestBody: requestBody,
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
                  <FormDescription>
                    The repository revision used by this run
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='jobConfigContext'
              render={({ field }) => (
                <FormItem className='pt-4'>
                  <FormLabel>Job configuration context</FormLabel>
                  <FormControl>
                    <Input {...field} />
                  </FormControl>
                  <FormDescription>
                    An optional context to be used when obtaining configuration
                    for this run. The meaning of the context is up for
                    interpretation by the implementation of the configuration
                    provider.
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='path'
              render={({ field }) => (
                <FormItem className='pt-4'>
                  <FormLabel>Path</FormLabel>
                  <FormControl>
                    <Input {...field} />
                  </FormControl>
                  <FormDescription>
                    An optional subpath to limit the analysis to, for example,
                    'dir/subdir'.
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <h3 className='mt-4'>Parameters</h3>
            <div className='text-sm text-gray-500'>
              A map with custom parameters for the whole run.
            </div>
            {parametersFields.map((field, index) => (
              <div
                key={field.id}
                className='my-2 flex flex-row items-end space-x-2'
              >
                <div className='flex-auto'>
                  {index === 0 && <FormLabel>Key</FormLabel>}
                  <FormField
                    control={form.control}
                    name={`jobConfigs.parameters.${index}.key`}
                    render={({ field }) => (
                      <FormItem>
                        <FormControl>
                          <Input {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>
                <div className='flex-auto'>
                  {index === 0 && <FormLabel>Value</FormLabel>}
                  <FormField
                    control={form.control}
                    name={`jobConfigs.parameters.${index}.value`}
                    render={({ field }) => (
                      <FormItem>
                        <FormControl>
                          <Input {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>
                <Button
                  type='button'
                  variant='outline'
                  size='sm'
                  onClick={() => {
                    parametersRemove(index);
                  }}
                >
                  <TrashIcon className='h-4 w-4' />
                </Button>
              </div>
            ))}
            <Button
              size='sm'
              className='mt-2'
              variant='outline'
              type='button'
              onClick={() => {
                parametersAppend({ key: '', value: '' });
              }}
            >
              Add parameter
              <PlusIcon className='ml-1 h-4 w-4' />
            </Button>

            <h3 className='mt-4'>Labels</h3>
            <div className='text-sm text-gray-500'>The labels of this run.</div>
            {labelsFields.map((field, index) => (
              <div
                key={field.id}
                className='my-2 flex flex-row items-end space-x-2'
              >
                <div className='flex-auto'>
                  {index === 0 && <FormLabel>Key</FormLabel>}
                  <FormField
                    control={form.control}
                    name={`labels.${index}.key`}
                    render={({ field }) => (
                      <FormItem>
                        <FormControl>
                          <Input {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>
                <div className='flex-auto'>
                  {index === 0 && <FormLabel>Value</FormLabel>}
                  <FormField
                    control={form.control}
                    name={`labels.${index}.value`}
                    render={({ field }) => (
                      <FormItem>
                        <FormControl>
                          <Input {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>
                <Button
                  type='button'
                  variant='outline'
                  size='sm'
                  onClick={() => {
                    labelsRemove(index);
                  }}
                >
                  <TrashIcon className='h-4 w-4' />
                </Button>
              </div>
            ))}
            <Button
              size='sm'
              className='mt-2'
              variant='outline'
              type='button'
              onClick={() => {
                labelsAppend({ key: '', value: '' });
              }}
            >
              Add label
              <PlusIcon className='ml-1 h-4 w-4' />
            </Button>

            <h3 className='mt-4'>Enable and configure jobs</h3>
            <div className='text-sm text-gray-500'>
              Configure the jobs to be included in the ORT Run.
            </div>
            <div className='text-sm text-gray-500'>
              NOTE: Currently, the Analyzer needs to always run as part of an
              ORT Run.
            </div>
            <Accordion type='multiple'>
              <AnalyzerFields form={form} />
              <AdvisorFields form={form} />
              <ScannerFields form={form} />
              <EvaluatorFields form={form} />
              <ReporterFields form={form} />
            </Accordion>
          </CardContent>
          <CardFooter>
            <Button type='submit' disabled={isPending}>
              {isPending ? (
                <>
                  <span className='sr-only'>Creating run...</span>
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
