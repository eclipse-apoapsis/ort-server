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
import { useState } from 'react';
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
import { toast } from '@/lib/toast';
import { AdvisorFields } from './-components/advisor-fields';
import { AnalyzerFields } from './-components/analyzer-fields';
import { EvaluatorFields } from './-components/evaluator-fields';
import { NotifierFields } from './-components/notifier-fields';
import { ReporterFields } from './-components/reporter-fields';
import { ScannerFields } from './-components/scanner-fields';
import {
  createRunFormSchema,
  CreateRunFormValues,
  defaultValues,
  formValuesToPayload,
} from './-create-run-utils';

const CreateRunPage = () => {
  const navigate = useNavigate();
  const params = Route.useParams();
  const ortRun = Route.useLoaderData();

  type AccordionSection =
    | 'analyzer'
    | 'advisor'
    | 'scanner'
    | 'evaluator'
    | 'reporter'
    | 'notifier';

  const [openAccordions, setOpenAccordions] = useState<AccordionSection[]>([]);

  // Manually toggle accordion open/close state
  const toggleAccordionOpen = (value: AccordionSection) => {
    setOpenAccordions(
      (prev) =>
        prev.includes(value)
          ? prev.filter((v) => v !== value) // Close accordion if open
          : [...prev, value] // Open accordion if closed
    );
  };

  const { mutateAsync, isPending } = useRepositoriesServicePostOrtRun({
    onSuccess() {
      toast.info('Create ORT Run', {
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
      toast.error(error.message, {
        description: <ToastError error={error} />,
        duration: Infinity,
        cancel: {
          label: 'Dismiss',
          onClick: () => {},
        },
      });
    },
  });

  const form = useForm<CreateRunFormValues>({
    resolver: zodResolver(createRunFormSchema),
    defaultValues: defaultValues(ortRun),
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

  async function onSubmit(values: CreateRunFormValues) {
    await mutateAsync({
      repositoryId: Number.parseInt(params.repoId),
      requestBody: formValuesToPayload(values),
    });
  }

  const onValidationFailed = (errors: typeof form.formState.errors) => {
    // Determine which accordions contain errors
    const accordionsWithErrors: AccordionSection[] = [];

    if (errors.jobConfigs?.analyzer) {
      accordionsWithErrors.push('analyzer');
    }
    if (errors.jobConfigs?.advisor) {
      accordionsWithErrors.push('advisor');
    }
    if (errors.jobConfigs?.scanner) {
      accordionsWithErrors.push('scanner');
    }
    if (errors.jobConfigs?.evaluator) {
      accordionsWithErrors.push('evaluator');
    }
    if (errors.jobConfigs?.reporter) {
      accordionsWithErrors.push('reporter');
    }
    if (errors.jobConfigs?.notifier) {
      accordionsWithErrors.push('notifier');
    }

    // Open the accordions with errors
    setOpenAccordions(accordionsWithErrors);
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>Create an ORT run</CardTitle>
      </CardHeader>
      <Form {...form}>
        <form
          onSubmit={form.handleSubmit(onSubmit, onValidationFailed)}
          className='space-y-8'
        >
          <CardContent>
            <FormField
              control={form.control}
              name='revision'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Revision</FormLabel>
                  <FormControl autoFocus>
                    <Input {...field} />
                  </FormControl>
                  <FormDescription>
                    The repository revision to use.
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
                    <Input {...field} placeholder='(optional)' />
                  </FormControl>
                  <FormDescription>
                    The path to limit the analysis to, for example
                    'path/to/source'.
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
                    <Input {...field} placeholder='(optional)' />
                  </FormControl>
                  <FormDescription>
                    The context to use when obtaining configuration for this
                    run. The meaning of the context is up for interpretation by
                    the implementation of the configuration provider.
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
            <Accordion
              type='multiple'
              value={openAccordions}
              onValueChange={(value) =>
                setOpenAccordions(value as AccordionSection[])
              }
            >
              <AnalyzerFields
                form={form}
                value='analyzer'
                onToggle={() => toggleAccordionOpen('analyzer')}
              />
              <AdvisorFields
                form={form}
                value='advisor'
                onToggle={() => toggleAccordionOpen('advisor')}
              />
              <ScannerFields
                form={form}
                value='scanner'
                onToggle={() => toggleAccordionOpen('scanner')}
              />
              <EvaluatorFields
                form={form}
                value='evaluator'
                onToggle={() => toggleAccordionOpen('evaluator')}
              />
              <ReporterFields
                form={form}
                value='reporter'
                onToggle={() => toggleAccordionOpen('reporter')}
              />
              <NotifierFields
                form={form}
                value='notifier'
                onToggle={() => toggleAccordionOpen('notifier')}
              />
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
