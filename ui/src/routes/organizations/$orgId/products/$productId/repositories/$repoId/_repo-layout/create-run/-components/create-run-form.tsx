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
import { Loader2, PlusIcon, TrashIcon } from 'lucide-react';
import { useState } from 'react';
import { useFieldArray, useForm } from 'react-hook-form';
import { z } from 'zod';

import type {
  OrtRun,
  PostRepositoryRun,
  PreconfiguredPluginDescriptor,
  Secret,
} from '@/api';
import { CopyToClipboard } from '@/components/copy-to-clipboard';
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
import { Textarea } from '@/components/ui/textarea';
import type {
  OrganizationPermissions,
  ProductPermissions,
  RepositoryPermissions,
} from '@/lib/permissions';
import {
  AdvisorFields,
  AnalyzerFields,
  EvaluatorFields,
  NotifierFields,
  ReporterFields,
  ScannerFields,
} from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/-components';
import { defaultValues } from './default-values';
import { formValuesToPayload } from './payload';
import {
  createRunFormSchema,
  flattenErrors,
  type CreateRunFormValues,
} from './run-schema';

type AccordionSection =
  'analyzer' | 'advisor' | 'scanner' | 'evaluator' | 'reporter' | 'notifier';

type CreateRunPermissions = {
  organization: OrganizationPermissions | undefined;
  product: ProductPermissions | undefined;
  repository: RepositoryPermissions | undefined;
};

export type CreateRunFormProps = {
  isSubmitting: boolean;
  isSuperuser: boolean;
  onSubmit: (payload: PostRepositoryRun) => Promise<void> | void;
  permissions: CreateRunPermissions;
  plugins: PreconfiguredPluginDescriptor[];
  rerun: OrtRun | null;
  secrets: Secret[];
};

export const CreateRunForm = ({
  isSubmitting,
  isSuperuser,
  onSubmit,
  permissions,
  plugins,
  rerun,
  secrets,
}: CreateRunFormProps) => {
  const [isTest, setIsTest] = useState(false);
  const isRerun = rerun !== null;
  const [openAccordions, setOpenAccordions] = useState<AccordionSection[]>([]);

  const advisorPlugins = plugins.filter((plugin) => plugin.type === 'ADVISOR');
  const reporterPlugins = plugins.filter(
    (plugin) => plugin.type === 'REPORTER'
  );
  const scannerPlugins = plugins.filter((plugin) => plugin.type === 'SCANNER');
  const packageCurationProviderPlugins = plugins.filter(
    (plugin) => plugin.type === 'PACKAGE_CURATION_PROVIDER'
  );
  const packageConfigurationProviderPlugins = plugins.filter(
    (plugin) => plugin.type === 'PACKAGE_CONFIGURATION_PROVIDER'
  );

  // Manually toggle accordion open/close state
  const toggleAccordionOpen = (value: AccordionSection) => {
    setOpenAccordions(
      (prev) =>
        prev.includes(value)
          ? prev.filter((v) => v !== value) // Close accordion if open
          : [...prev, value] // Open accordion if closed
    );
  };

  const formSchema = createRunFormSchema(
    advisorPlugins,
    scannerPlugins,
    packageCurationProviderPlugins,
    packageConfigurationProviderPlugins
  );

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: defaultValues(
      rerun,
      advisorPlugins,
      scannerPlugins,
      isSuperuser,
      packageCurationProviderPlugins,
      packageConfigurationProviderPlugins
    ),
  });

  const watchedValues = form.watch();

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

  async function submitForm(values: CreateRunFormValues) {
    await onSubmit(formValuesToPayload(values));
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
        <CardTitle>Create a Run</CardTitle>
      </CardHeader>
      <Form {...form}>
        <form
          onSubmit={form.handleSubmit(submitForm, onValidationFailed)}
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
            <FormField
              control={form.control}
              name='environmentConfigPath'
              render={({ field }) => (
                <FormItem className='pt-4'>
                  <FormLabel>Environment configuration path</FormLabel>
                  <FormControl>
                    <Input {...field} placeholder='(optional)' />
                  </FormControl>
                  <FormDescription>
                    The optional path to an environment configuration file. If
                    this is not defined, the environment configuration is read
                    from the default location
                    <InlineCode>.ort.env.yml</InlineCode>.
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='jobConfigs.ruleSet'
              render={({ field }) => (
                <FormItem className='pt-4'>
                  <FormLabel>Rule set</FormLabel>
                  <FormControl>
                    <Input {...field} placeholder='(optional)' />
                  </FormControl>
                  <FormDescription>
                    The rule set to use for the run. This selects a set of
                    configuration files used by the Evaluator and the Reporter,
                    such as rules for the Evaluator or license classifications.
                    If left empty, the default rule set is used.
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
                  <FormField
                    control={form.control}
                    name={`jobConfigs.parameters.${index}.key`}
                    render={({ field }) => (
                      <FormItem>
                        {index === 0 && (
                          <FormLabel className='mb-2'>Key</FormLabel>
                        )}
                        <FormControl>
                          <Input {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>
                <div className='flex-auto'>
                  <FormField
                    control={form.control}
                    name={`jobConfigs.parameters.${index}.value`}
                    render={({ field }) => (
                      <FormItem>
                        {index === 0 && (
                          <FormLabel className='mb-2'>Value</FormLabel>
                        )}
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
                  <FormField
                    control={form.control}
                    name={`labels.${index}.key`}
                    render={({ field }) => (
                      <FormItem>
                        {index === 0 && (
                          <FormLabel className='mb-2'>Key</FormLabel>
                        )}
                        <FormControl>
                          <Input {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>
                <div className='flex-auto'>
                  <FormField
                    control={form.control}
                    name={`labels.${index}.value`}
                    render={({ field }) => (
                      <FormItem>
                        {index === 0 && (
                          <FormLabel className='mb-2'>Value</FormLabel>
                        )}
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
              Configure the jobs to be included in the run.
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
                isSuperuser={isSuperuser}
                packageCurationProviderPlugins={packageCurationProviderPlugins}
                pluginSecrets={secrets}
                isRerun={isRerun}
                permissions={permissions}
              />
              <AdvisorFields
                form={form}
                value='advisor'
                onToggle={() => toggleAccordionOpen('advisor')}
                advisorPlugins={advisorPlugins}
                secrets={secrets}
                isSuperuser={isSuperuser}
              />
              <ScannerFields
                form={form}
                value='scanner'
                onToggle={() => toggleAccordionOpen('scanner')}
                scannerPlugins={scannerPlugins}
                secrets={secrets}
                isSuperuser={isSuperuser}
              />
              <EvaluatorFields
                form={form}
                value='evaluator'
                onToggle={() => toggleAccordionOpen('evaluator')}
                isSuperuser={isSuperuser}
                packageConfigurationProviderPlugins={
                  packageConfigurationProviderPlugins
                }
                secrets={secrets}
                isRerun={isRerun}
              />
              <ReporterFields
                form={form}
                value='reporter'
                onToggle={() => toggleAccordionOpen('reporter')}
                reporterPlugins={reporterPlugins}
                isSuperuser={isSuperuser}
                packageConfigurationProviderPlugins={
                  packageConfigurationProviderPlugins
                }
                secrets={secrets}
                isRerun={isRerun}
              />
              <NotifierFields
                form={form}
                value='notifier'
                onToggle={() => toggleAccordionOpen('notifier')}
                isSuperuser={isSuperuser}
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
              <Button type='submit' disabled={isSubmitting}>
                {isSubmitting ? (
                  <>
                    <span className='sr-only'>Creating run...</span>
                    <Loader2 size={16} className='mx-3 animate-spin' />
                  </>
                ) : (
                  'Create'
                )}
              </Button>
              <div className='flex items-center space-x-2'>
                <Switch
                  id='test-form'
                  checked={isTest}
                  onCheckedChange={setIsTest}
                />
                <Label className='text-muted-foreground' htmlFor='test-form'>
                  Show payload
                </Label>
              </div>
            </div>
            {isTest && (
              <>
                <h3 className='mt-4'>Form payload</h3>
                <Label htmlFor='payload' className='text-muted-foreground'>
                  You can copy this payload to use it when triggering runs via
                  the API or CLI.
                </Label>
                <div className='relative w-full'>
                  <Textarea
                    id='payload'
                    className='h-96 pr-12 font-mono'
                    readOnly
                    value={JSON.stringify(
                      formValuesToPayload(watchedValues),
                      null,
                      2
                    )}
                  />
                  <div className='absolute top-2 right-2 z-10'>
                    <CopyToClipboard
                      copyText={JSON.stringify(
                        formValuesToPayload(watchedValues),
                        null,
                        2
                      )}
                    />
                  </div>
                </div>
              </>
            )}
          </CardFooter>
        </form>
      </Form>
    </Card>
  );
};
