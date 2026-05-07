/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import * as AccordionPrimitive from '@radix-ui/react-accordion';
import { ChevronDownIcon } from 'lucide-react';
import { useRef } from 'react';
import { UseFormReturn } from 'react-hook-form';

import { Accordion, AccordionContent } from '@/components/ui/accordion';
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '@/components/ui/collapsible';
import {
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
import { capitalize } from '@/helpers/capitalize';
import { InfrastructureServiceWithHierarchy } from '@/hooks/use-infrastructure-services';
import {
  conanEnvironmentDefinitions,
  EnvironmentDefinitionEntry,
  gradleEnvironmentDefinitions,
  mavenEnvironmentDefinitions,
  NpmAuthMode,
  npmAuthModes,
  npmEnvironmentDefinitions,
  NuGetAuthMode,
  nugetAuthModes,
  nugetEnvironmentDefinitions,
  YarnAuthMode,
  yarnAuthModes,
  yarnEnvironmentDefinitions,
} from '@/lib/types';
import { CreateRunFormValues } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/create-run/-components';

type EnvironmentDefinitionsFieldsProps = {
  form: UseFormReturn<CreateRunFormValues>;
  infrastructureServices: InfrastructureServiceWithHierarchy[];
};

function cloneEntries(
  entries: EnvironmentDefinitionEntry[] | undefined
): EnvironmentDefinitionEntry[] | undefined {
  return entries?.map((entry) => ({ ...entry }));
}

export const EnvironmentDefinitionsFields = ({
  form,
  infrastructureServices,
}: EnvironmentDefinitionsFieldsProps) => {
  const definitionBackups = useRef<
    Record<string, EnvironmentDefinitionEntry[]>
  >({});

  const enabledDefinitions =
    form.watch('jobConfigs.analyzer.environmentDefinitionsEnabled') ?? {};

  function setEnabled(packageManager: string, value: boolean) {
    form.setValue(
      'jobConfigs.analyzer.environmentDefinitionsEnabled',
      { ...enabledDefinitions, [packageManager]: value },
      { shouldDirty: true }
    );
  }

  function setEntries(
    packageManager: string,
    entries: EnvironmentDefinitionEntry[] | undefined
  ) {
    const current =
      form.getValues('jobConfigs.analyzer.environmentDefinitions') ?? {};
    const updated = entries
      ? { ...current, [packageManager]: entries }
      : Object.fromEntries(
          Object.entries(current).filter(([k]) => k !== packageManager)
        );
    form.setValue(
      'jobConfigs.analyzer.environmentDefinitions',
      Object.keys(updated).length > 0 ? updated : undefined,
      { shouldDirty: true }
    );
  }

  function onToggle(
    packageManager: string,
    checked: boolean,
    defaultEntries: EnvironmentDefinitionEntry[]
  ) {
    setEnabled(packageManager, checked);
    if (checked) {
      setEntries(
        packageManager,
        cloneEntries(definitionBackups.current[packageManager]) ??
          cloneEntries(defaultEntries)
      );
    } else {
      definitionBackups.current[packageManager] =
        cloneEntries(
          form.getValues('jobConfigs.analyzer.environmentDefinitions')?.[
            packageManager
          ]
        ) ?? [];
      setEntries(packageManager, undefined);
    }
  }

  return (
    <Collapsible className='flex flex-col gap-2'>
      <CollapsibleTrigger className='flex w-full items-start justify-between gap-4 text-left [&[data-state=open]>svg]:rotate-180'>
        <div>
          <h3>Environment configuration</h3>
          <p className='mt-1 text-sm text-gray-500'>
            Configure the credentials for different package managers to access
            private artifact repositories.
          </p>
        </div>
        <ChevronDownIcon className='text-muted-foreground mt-1 size-4 shrink-0 transition-transform duration-200' />
      </CollapsibleTrigger>
      <CollapsibleContent>
        <Accordion type='multiple' className='ml-4 flex flex-col gap-2'>
          {/* Conan */}
          <AccordionPrimitive.Item value='conan' className='rounded-lg border'>
            <AccordionPrimitive.Header className='flex flex-row items-center'>
              <AccordionPrimitive.Trigger className='focus-visible:border-ring focus-visible:ring-ring/50 flex flex-1 items-center gap-2 px-4 py-4 text-left text-sm font-medium transition-all outline-none hover:underline focus-visible:ring-[3px] [&[data-state=open]>svg]:rotate-180'>
                Conan
                <ChevronDownIcon className='text-muted-foreground size-4 shrink-0 translate-y-0.5 transition-transform duration-200' />
              </AccordionPrimitive.Trigger>
              <div className='pr-4'>
                <Switch
                  checked={enabledDefinitions['conan'] ?? false}
                  onCheckedChange={(checked) =>
                    onToggle(
                      'conan',
                      checked,
                      conanEnvironmentDefinitions['conan'] ?? []
                    )
                  }
                />
              </div>
            </AccordionPrimitive.Header>
            <AccordionContent>
              <div className='flex flex-col gap-4 px-4 pb-4'>
                <FormField
                  control={form.control}
                  name='jobConfigs.analyzer.environmentDefinitions.conan.0.service'
                  render={({ field }) => {
                    const selectedValue =
                      typeof field.value === 'string' && field.value.length > 0
                        ? field.value
                        : undefined;
                    return (
                      <FormItem>
                        <FormLabel>Service</FormLabel>
                        <Select
                          value={selectedValue}
                          onValueChange={(value) => field.onChange(value)}
                        >
                          <FormControl>
                            <SelectTrigger className='w-full'>
                              <SelectValue placeholder='Select an infrastructure service' />
                            </SelectTrigger>
                          </FormControl>
                          <SelectContent>
                            {infrastructureServices.map((service) => (
                              <SelectItem
                                key={`${service.hierarchy}:${service.name}`}
                                value={service.name}
                              >
                                {`${service.name} (${capitalize(service.hierarchy)})`}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                        <FormDescription>
                          Select the infrastructure service from a chosen
                          hierarchy level.
                        </FormDescription>
                        <FormMessage />
                      </FormItem>
                    );
                  }}
                />
                <FormField
                  control={form.control}
                  name='jobConfigs.analyzer.environmentDefinitions.conan.0.name'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Remote name</FormLabel>
                      <FormControl>
                        <Input {...field} />
                      </FormControl>
                      <FormDescription>
                        Name of the Conan remote, used in commands like{' '}
                        <code>conan list</code>.
                      </FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='jobConfigs.analyzer.environmentDefinitions.conan.0.url'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>URL</FormLabel>
                      <FormControl>
                        <Input {...field} placeholder='(optional)' />
                      </FormControl>
                      <FormDescription>
                        URL for Conan to search for recipes and binaries. Falls
                        back to the infrastructure service URL if not set.
                      </FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='jobConfigs.analyzer.environmentDefinitions.conan.0.verifySsl'
                  render={({ field }) => (
                    <FormItem className='flex flex-row items-center justify-between rounded-lg border p-4'>
                      <div className='space-y-0.5'>
                        <FormLabel>Verify SSL</FormLabel>
                        <FormDescription>
                          Verify the SSL certificate of the remote URL.
                        </FormDescription>
                      </div>
                      <FormControl>
                        <Switch
                          checked={(field.value ?? 'true') === 'true'}
                          onCheckedChange={(checked) =>
                            field.onChange(checked ? 'true' : 'false')
                          }
                        />
                      </FormControl>
                    </FormItem>
                  )}
                />
              </div>
            </AccordionContent>
          </AccordionPrimitive.Item>
          {/* Gradle */}
          <AccordionPrimitive.Item value='gradle' className='rounded-lg border'>
            <AccordionPrimitive.Header className='flex flex-row items-center'>
              <AccordionPrimitive.Trigger className='focus-visible:border-ring focus-visible:ring-ring/50 flex flex-1 items-center gap-2 px-4 py-4 text-left text-sm font-medium transition-all outline-none hover:underline focus-visible:ring-[3px] [&[data-state=open]>svg]:rotate-180'>
                Gradle
                <ChevronDownIcon className='text-muted-foreground size-4 shrink-0 translate-y-0.5 transition-transform duration-200' />
              </AccordionPrimitive.Trigger>
              <div className='pr-4'>
                <Switch
                  checked={enabledDefinitions['gradle'] ?? false}
                  onCheckedChange={(checked) =>
                    onToggle(
                      'gradle',
                      checked,
                      gradleEnvironmentDefinitions['gradle'] ?? []
                    )
                  }
                />
              </div>
            </AccordionPrimitive.Header>
            <AccordionContent>
              <div className='flex flex-col gap-4 px-4 pb-4'>
                <FormField
                  control={form.control}
                  name='jobConfigs.analyzer.environmentDefinitions.gradle.0.service'
                  render={({ field }) => {
                    const selectedValue =
                      typeof field.value === 'string' && field.value.length > 0
                        ? field.value
                        : undefined;
                    return (
                      <FormItem>
                        <FormLabel>Service</FormLabel>
                        <Select
                          value={selectedValue}
                          onValueChange={(value) => field.onChange(value)}
                        >
                          <FormControl>
                            <SelectTrigger className='w-full'>
                              <SelectValue placeholder='Select an infrastructure service' />
                            </SelectTrigger>
                          </FormControl>
                          <SelectContent>
                            {infrastructureServices.map((service) => (
                              <SelectItem
                                key={`${service.hierarchy}:${service.name}`}
                                value={service.name}
                              >
                                {`${service.name} (${capitalize(service.hierarchy)})`}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                        <FormDescription>
                          Select the infrastructure service from a chosen
                          hierarchy level.
                        </FormDescription>
                        <FormMessage />
                      </FormItem>
                    );
                  }}
                />
              </div>
            </AccordionContent>
          </AccordionPrimitive.Item>
          {/* Maven */}
          <AccordionPrimitive.Item value='maven' className='rounded-lg border'>
            <AccordionPrimitive.Header className='flex flex-row items-center'>
              <AccordionPrimitive.Trigger className='focus-visible:border-ring focus-visible:ring-ring/50 flex flex-1 items-center gap-2 px-4 py-4 text-left text-sm font-medium transition-all outline-none hover:underline focus-visible:ring-[3px] [&[data-state=open]>svg]:rotate-180'>
                Maven
                <ChevronDownIcon className='text-muted-foreground size-4 shrink-0 translate-y-0.5 transition-transform duration-200' />
              </AccordionPrimitive.Trigger>
              <div className='pr-4'>
                <Switch
                  checked={enabledDefinitions['maven'] ?? false}
                  onCheckedChange={(checked) =>
                    onToggle(
                      'maven',
                      checked,
                      mavenEnvironmentDefinitions['maven'] ?? []
                    )
                  }
                />
              </div>
            </AccordionPrimitive.Header>
            <AccordionContent>
              <div className='flex flex-col gap-4 px-4 pb-4'>
                <FormField
                  control={form.control}
                  name='jobConfigs.analyzer.environmentDefinitions.maven.0.service'
                  render={({ field }) => {
                    const selectedValue =
                      typeof field.value === 'string' && field.value.length > 0
                        ? field.value
                        : undefined;
                    return (
                      <FormItem>
                        <FormLabel>Service</FormLabel>
                        <Select
                          value={selectedValue}
                          onValueChange={(value) => field.onChange(value)}
                        >
                          <FormControl>
                            <SelectTrigger className='w-full'>
                              <SelectValue placeholder='Select an infrastructure service' />
                            </SelectTrigger>
                          </FormControl>
                          <SelectContent>
                            {infrastructureServices.map((service) => (
                              <SelectItem
                                key={`${service.hierarchy}:${service.name}`}
                                value={service.name}
                              >
                                {`${service.name} (${capitalize(service.hierarchy)})`}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                        <FormDescription>
                          Select the infrastructure service from a chosen
                          hierarchy level.
                        </FormDescription>
                        <FormMessage />
                      </FormItem>
                    );
                  }}
                />
                <FormField
                  control={form.control}
                  name='jobConfigs.analyzer.environmentDefinitions.maven.0.id'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>ID</FormLabel>
                      <FormControl>
                        <Input {...field} />
                      </FormControl>
                      <FormDescription>
                        Repository ID referenced in <code>pom.xml</code> files.
                        Appears as the <code>&lt;id&gt;</code> of the
                        corresponding server in Maven&apos;s{' '}
                        <code>settings.xml</code>.
                      </FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='jobConfigs.analyzer.environmentDefinitions.maven.0.mirrorOf'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Mirror of</FormLabel>
                      <FormControl>
                        <Input {...field} placeholder='(optional)' />
                      </FormControl>
                      <FormDescription>
                        If set, adds this entry to the{' '}
                        <code>&lt;mirrors&gt;</code> section of{' '}
                        <code>settings.xml</code>. Value is the repository ID to
                        mirror (e.g. <code>central</code>, or <code>*</code> for
                        all repositories).
                      </FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>
            </AccordionContent>
          </AccordionPrimitive.Item>
          {/* NPM */}
          <AccordionPrimitive.Item value='npm' className='rounded-lg border'>
            <AccordionPrimitive.Header className='flex flex-row items-center'>
              <AccordionPrimitive.Trigger className='focus-visible:border-ring focus-visible:ring-ring/50 flex flex-1 items-center gap-2 px-4 py-4 text-left text-sm font-medium transition-all outline-none hover:underline focus-visible:ring-[3px] [&[data-state=open]>svg]:rotate-180'>
                NPM
                <ChevronDownIcon className='text-muted-foreground size-4 shrink-0 translate-y-0.5 transition-transform duration-200' />
              </AccordionPrimitive.Trigger>
              <div className='pr-4'>
                <Switch
                  checked={enabledDefinitions['npm'] ?? false}
                  onCheckedChange={(checked) =>
                    onToggle(
                      'npm',
                      checked,
                      npmEnvironmentDefinitions.npm ?? []
                    )
                  }
                />
              </div>
            </AccordionPrimitive.Header>
            <AccordionContent>
              <div className='flex flex-col gap-4 px-4 pb-4'>
                <FormField
                  control={form.control}
                  name='jobConfigs.analyzer.environmentDefinitions.npm.0.service'
                  render={({ field }) => {
                    const selectedValue =
                      typeof field.value === 'string' && field.value.length > 0
                        ? field.value
                        : undefined;
                    return (
                      <FormItem>
                        <FormLabel>Service</FormLabel>
                        <Select
                          value={selectedValue}
                          onValueChange={(value) => field.onChange(value)}
                        >
                          <FormControl>
                            <SelectTrigger className='w-full'>
                              <SelectValue placeholder='Select an infrastructure service' />
                            </SelectTrigger>
                          </FormControl>
                          <SelectContent>
                            {infrastructureServices.map((service) => (
                              <SelectItem
                                key={`${service.hierarchy}:${service.name}`}
                                value={service.name}
                              >
                                {`${service.name} (${capitalize(service.hierarchy)})`}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                        <FormDescription>
                          Select the infrastructure service from a chosen
                          hierarchy level.
                        </FormDescription>
                        <FormMessage />
                      </FormItem>
                    );
                  }}
                />
                <FormField
                  control={form.control}
                  name='jobConfigs.analyzer.environmentDefinitions.npm.0.scope'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Scope</FormLabel>
                      <FormControl>
                        <Input {...field} placeholder='(optional)' />
                      </FormControl>
                      <FormDescription>
                        Optional NPM scope that this configuration applies to.
                      </FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='jobConfigs.analyzer.environmentDefinitions.npm.0.email'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Email</FormLabel>
                      <FormControl>
                        <Input {...field} placeholder='(optional)' />
                      </FormControl>
                      <FormDescription>
                        Optional email address used by the NPM registry.
                      </FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='jobConfigs.analyzer.environmentDefinitions.npm.0.authMode'
                  render={({ field }) => {
                    const selectedValue =
                      typeof field.value === 'string' && field.value.length > 0
                        ? (field.value as NpmAuthMode)
                        : undefined;
                    return (
                      <FormItem>
                        <FormLabel>Authorization mode</FormLabel>
                        <Select
                          value={selectedValue}
                          onValueChange={(value) => field.onChange(value)}
                        >
                          <FormControl>
                            <SelectTrigger className='w-full'>
                              <SelectValue placeholder='Select the authorization mode' />
                            </SelectTrigger>
                          </FormControl>
                          <SelectContent>
                            {npmAuthModes.map((mode) => (
                              <SelectItem key={mode} value={mode}>
                                {mode.replaceAll('_', ' ')}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                        <FormDescription>
                          Pick how the NPM registry authenticates requests.
                        </FormDescription>
                        <FormMessage />
                      </FormItem>
                    );
                  }}
                />
              </div>
            </AccordionContent>
          </AccordionPrimitive.Item>
          {/* NuGet */}
          <AccordionPrimitive.Item value='nuget' className='rounded-lg border'>
            <AccordionPrimitive.Header className='flex flex-row items-center'>
              <AccordionPrimitive.Trigger className='focus-visible:border-ring focus-visible:ring-ring/50 flex flex-1 items-center gap-2 px-4 py-4 text-left text-sm font-medium transition-all outline-none hover:underline focus-visible:ring-[3px] [&[data-state=open]>svg]:rotate-180'>
                NuGet
                <ChevronDownIcon className='text-muted-foreground size-4 shrink-0 translate-y-0.5 transition-transform duration-200' />
              </AccordionPrimitive.Trigger>
              <div className='pr-4'>
                <Switch
                  checked={enabledDefinitions['nuget'] ?? false}
                  onCheckedChange={(checked) =>
                    onToggle(
                      'nuget',
                      checked,
                      nugetEnvironmentDefinitions['nuget'] ?? []
                    )
                  }
                />
              </div>
            </AccordionPrimitive.Header>
            <AccordionContent>
              <div className='flex flex-col gap-4 px-4 pb-4'>
                <FormField
                  control={form.control}
                  name='jobConfigs.analyzer.environmentDefinitions.nuget.0.service'
                  render={({ field }) => {
                    const selectedValue =
                      typeof field.value === 'string' && field.value.length > 0
                        ? field.value
                        : undefined;
                    return (
                      <FormItem>
                        <FormLabel>Service</FormLabel>
                        <Select
                          value={selectedValue}
                          onValueChange={(value) => field.onChange(value)}
                        >
                          <FormControl>
                            <SelectTrigger className='w-full'>
                              <SelectValue placeholder='Select an infrastructure service' />
                            </SelectTrigger>
                          </FormControl>
                          <SelectContent>
                            {infrastructureServices.map((service) => (
                              <SelectItem
                                key={`${service.hierarchy}:${service.name}`}
                                value={service.name}
                              >
                                {`${service.name} (${capitalize(service.hierarchy)})`}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                        <FormDescription>
                          Select the infrastructure service from a chosen
                          hierarchy level.
                        </FormDescription>
                        <FormMessage />
                      </FormItem>
                    );
                  }}
                />
                <FormField
                  control={form.control}
                  name='jobConfigs.analyzer.environmentDefinitions.nuget.0.sourceName'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Source name</FormLabel>
                      <FormControl>
                        <Input {...field} />
                      </FormControl>
                      <FormDescription>
                        The name to assign to the package source.
                      </FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='jobConfigs.analyzer.environmentDefinitions.nuget.0.sourcePath'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Source path</FormLabel>
                      <FormControl>
                        <Input {...field} />
                      </FormControl>
                      <FormDescription>
                        The path or URL of the package source.
                      </FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='jobConfigs.analyzer.environmentDefinitions.nuget.0.sourceProtocolVersion'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Source protocol version</FormLabel>
                      <FormControl>
                        <Input {...field} placeholder='(optional)' />
                      </FormControl>
                      <FormDescription>
                        NuGet server protocol version (e.g. 3). Defaults to 2
                        for non-JSON source URLs. Requires NuGet 3.0+.
                      </FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='jobConfigs.analyzer.environmentDefinitions.nuget.0.authMode'
                  render={({ field }) => {
                    const selectedValue =
                      typeof field.value === 'string' && field.value.length > 0
                        ? (field.value as NuGetAuthMode)
                        : undefined;
                    return (
                      <FormItem>
                        <FormLabel>Authorization mode</FormLabel>
                        <Select
                          value={selectedValue}
                          onValueChange={(value) => field.onChange(value)}
                        >
                          <FormControl>
                            <SelectTrigger className='w-full'>
                              <SelectValue placeholder='Select the authorization mode' />
                            </SelectTrigger>
                          </FormControl>
                          <SelectContent>
                            {nugetAuthModes.map((mode) => (
                              <SelectItem key={mode} value={mode}>
                                {mode.replaceAll('_', ' ')}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                        <FormDescription>
                          Authentication type for this package source.
                        </FormDescription>
                        <FormMessage />
                      </FormItem>
                    );
                  }}
                />
              </div>
            </AccordionContent>
          </AccordionPrimitive.Item>
          {/* Yarn */}
          <AccordionPrimitive.Item value='yarn' className='rounded-lg border'>
            <AccordionPrimitive.Header className='flex flex-row items-center'>
              <AccordionPrimitive.Trigger className='focus-visible:border-ring focus-visible:ring-ring/50 flex flex-1 items-center gap-2 px-4 py-4 text-left text-sm font-medium transition-all outline-none hover:underline focus-visible:ring-[3px] [&[data-state=open]>svg]:rotate-180'>
                Yarn
                <ChevronDownIcon className='text-muted-foreground size-4 shrink-0 translate-y-0.5 transition-transform duration-200' />
              </AccordionPrimitive.Trigger>
              <div className='pr-4'>
                <Switch
                  checked={enabledDefinitions['yarn'] ?? false}
                  onCheckedChange={(checked) =>
                    onToggle(
                      'yarn',
                      checked,
                      yarnEnvironmentDefinitions['yarn'] ?? []
                    )
                  }
                />
              </div>
            </AccordionPrimitive.Header>
            <AccordionContent>
              <div className='flex flex-col gap-4 px-4 pb-4'>
                <FormField
                  control={form.control}
                  name='jobConfigs.analyzer.environmentDefinitions.yarn.0.service'
                  render={({ field }) => {
                    const selectedValue =
                      typeof field.value === 'string' && field.value.length > 0
                        ? field.value
                        : undefined;
                    return (
                      <FormItem>
                        <FormLabel>Service</FormLabel>
                        <Select
                          value={selectedValue}
                          onValueChange={(value) => field.onChange(value)}
                        >
                          <FormControl>
                            <SelectTrigger className='w-full'>
                              <SelectValue placeholder='Select an infrastructure service' />
                            </SelectTrigger>
                          </FormControl>
                          <SelectContent>
                            {infrastructureServices.map((service) => (
                              <SelectItem
                                key={`${service.hierarchy}:${service.name}`}
                                value={service.name}
                              >
                                {`${service.name} (${capitalize(service.hierarchy)})`}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                        <FormDescription>
                          Select the infrastructure service from a chosen
                          hierarchy level.
                        </FormDescription>
                        <FormMessage />
                      </FormItem>
                    );
                  }}
                />
                <FormField
                  control={form.control}
                  name='jobConfigs.analyzer.environmentDefinitions.yarn.0.authMode'
                  render={({ field }) => {
                    const selectedValue =
                      typeof field.value === 'string' && field.value.length > 0
                        ? (field.value as YarnAuthMode)
                        : undefined;
                    return (
                      <FormItem>
                        <FormLabel>Authorization mode</FormLabel>
                        <Select
                          value={selectedValue}
                          onValueChange={(value) => field.onChange(value)}
                        >
                          <FormControl>
                            <SelectTrigger className='w-full'>
                              <SelectValue placeholder='Select the authorization mode' />
                            </SelectTrigger>
                          </FormControl>
                          <SelectContent>
                            {yarnAuthModes.map((mode) => (
                              <SelectItem key={mode} value={mode}>
                                {mode.replaceAll('_', ' ')}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                        <FormDescription>
                          Authentication method for this private registry.
                        </FormDescription>
                        <FormMessage />
                      </FormItem>
                    );
                  }}
                />
                <FormField
                  control={form.control}
                  name='jobConfigs.analyzer.environmentDefinitions.yarn.0.alwaysAuth'
                  render={({ field }) => (
                    <FormItem className='flex flex-row items-center justify-between rounded-lg border p-4'>
                      <div className='space-y-0.5'>
                        <FormLabel>Always authenticate</FormLabel>
                        <FormDescription>
                          Always send authentication information to the registry
                          via the <code>npmAlwaysAuth</code> property.
                        </FormDescription>
                      </div>
                      <FormControl>
                        <Switch
                          checked={(field.value ?? 'true') === 'true'}
                          onCheckedChange={(checked) =>
                            field.onChange(checked ? 'true' : 'false')
                          }
                        />
                      </FormControl>
                    </FormItem>
                  )}
                />
              </div>
            </AccordionContent>
          </AccordionPrimitive.Item>
        </Accordion>
      </CollapsibleContent>
    </Collapsible>
  );
};
