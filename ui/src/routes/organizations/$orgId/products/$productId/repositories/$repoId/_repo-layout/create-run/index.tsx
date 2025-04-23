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

import { useRepositoriesServicePostApiV1RepositoriesByRepositoryIdRuns } from '@/api/queries';
import { ApiError, RepositoriesService } from '@/api/requests';
import { ToastError } from '@/components/toast-error';
import { InlineCode } from '@/components/typography.tsx';
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
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { useUser } from '@/hooks/use-user';
import { toast } from '@/lib/toast';
import { AdvisorFields } from '../../-components/advisor-fields';
import { AnalyzerFields } from '../../-components/analyzer-fields';
import { EvaluatorFields } from '../../-components/evaluator-fields';
import { NotifierFields } from '../../-components/notifier-fields';
import { ReporterFields } from '../../-components/reporter-fields';
import { ScannerFields } from '../../-components/scanner-fields';
import {
  createRunFormSchema,
  CreateRunFormValues,
  defaultValues,
  flattenErrors,
  formValuesToPayload,
} from './-create-run-utils';

const CreateRunPage = () => {
  const navigate = useNavigate();
  const params = Route.useParams();
  const ortRun = Route.useLoaderData();
  const user = useUser();
  const [isTest, setIsTest] = useState(false);

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

  const { mutateAsync, isPending } =
    useRepositoriesServicePostApiV1RepositoriesByRepositoryIdRuns({
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

  const form = useForm({
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
    if (isTest) {
      return;
    }
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
        <CardTitle>Create an ORT Run</CardTitle>
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
                    The repository revision to use. Can be a branch, tag, or
                    commit. Uses the default branch if left empty.
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
                    The path (relative to the repository root) to limit the
                    analysis to, for example{' '}
                    <InlineCode>path/to/source</InlineCode>.
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
                  <FormLabel>Configuration context</FormLabel>
                  <FormControl>
                    <Input {...field} placeholder='(optional)' />
                  </FormControl>
                  <FormDescription>
                    The context to pass to the configuration provider in use.
                    The configuration provider is responsible for obtaining
                    configuration for this run and uses the context in an
                    implementation-specific way. For example, if the{' '}
                    <InlineCode>GitHubConfigFileProvider</InlineCode> is in use,
                    the context defines the Git revision of the configuration to
                    check out.
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <h3 className='mt-4'>Configuration parameters</h3>
            <div className='text-sm text-gray-500'>
              A map of key-value pairs that serve as input parameters for the
              configuration's validation script. The script can use these
              parameters to alter specific job configurations.
            </div>
            {parametersFields.map((field, index) => (
              <div
                key={field.id}
                className='my-2 flex flex-row items-end space-x-2'
              >
                <div className='flex-auto'>
                  {index === 0 && <FormLabel className='mb-2'>Key</FormLabel>}
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
                  {index === 0 && <FormLabel className='mb-2'>Value</FormLabel>}
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

            <h3 className='mt-4'>ORT labels</h3>
            <div className='text-sm text-gray-500'>
              A map of key-value pairs to store as labels in ORT results. ORT
              does not interpret labels by itself, but leaves interpretation to
              custom configuration, like evaluator rules.
            </div>
            {labelsFields.map((field, index) => (
              <div
                key={field.id}
                className='my-2 flex flex-row items-end space-x-2'
              >
                <div className='flex-auto'>
                  {index === 0 && <FormLabel className='mb-2'>Key</FormLabel>}
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
                  {index === 0 && <FormLabel className='mb-2'>Value</FormLabel>}
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
          <CardFooter className='flex flex-col items-start gap-4'>
            {Object.keys(form.formState.errors).length > 0 && (
              <p className='text-destructive text-[0.8rem] font-medium'>
                {flattenErrors(form.formState.errors).map(
                  ({ path, message }) => (
                    <div key={path}>
                      <strong>{path}:</strong> {message}
                    </div>
                  )
                )}
              </p>
            )}
            <div className='flex w-full items-center justify-between'>
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
              {user.hasRole(['superuser']) && (
                <div className='flex items-center space-x-2'>
                  <Switch
                    id='test-form'
                    checked={isTest}
                    onCheckedChange={setIsTest}
                  />
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <Label
                        className='text-muted-foreground'
                        htmlFor='test-form'
                      >
                        Test the form
                      </Label>
                    </TooltipTrigger>
                    <TooltipContent>
                      <div>Only for superusers: when enabled, pressing</div>
                      <div>
                        "Create" will not create a run but it instead shows
                      </div>
                      <div>
                        the form payload that would be sent to the server.
                      </div>
                    </TooltipContent>
                  </Tooltip>
                </div>
              )}
            </div>
            {user.hasRole(['superuser']) && isTest && (
              <>
                <Label className='mt-4'>Form payload:</Label>
                <pre className='w-full rounded-lg p-4 text-xs'>
                  {JSON.stringify(
                    formValuesToPayload(form.getValues()),
                    null,
                    2
                  )}
                </pre>
              </>
            )}
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
  '/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/create-run/'
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
    const ortRun =
      await RepositoriesService.getApiV1RepositoriesByRepositoryIdRunsByOrtRunIndex(
        {
          repositoryId: Number.parseInt(params.repoId),
          ortRunIndex: rerunIndex,
        }
      );
    return ortRun;
  },
  component: CreateRunPage,
  validateSearch: rerunIndexSchema,
});
