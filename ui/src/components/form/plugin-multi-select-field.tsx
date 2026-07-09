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

import { DragDropProvider } from '@dnd-kit/react';
import { isSortable, useSortable } from '@dnd-kit/react/sortable';
import { CheckedState } from '@radix-ui/react-checkbox';
import { GripVerticalIcon } from 'lucide-react';
import React from 'react';
import {
  FieldPathByValue,
  FieldPathValue,
  FieldValues,
  Path,
  UseFormReturn,
} from 'react-hook-form';

import { PreconfiguredPluginDescriptor, Secret } from '@/api';
import { OptionalInput } from '@/components/form/optional-input.tsx';
import {
  getEnumSelectDisplayValue,
  getPluginsInDisplayOrder,
  getSecretSelectDisplayValue,
  mapEnumSelectValue,
  mapSecretSelectValue,
  moveItem,
  UNDEFINED_ENUM_VALUE,
  UNDEFINED_SECRET_VALUE,
} from '@/components/form/plugin-multi-select-field-utils';
import { MarkdownRenderer } from '@/components/markdown-renderer';
import { Badge } from '@/components/ui/badge.tsx';
import { Checkbox } from '@/components/ui/checkbox';
import {
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Input } from '@/components/ui/input.tsx';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select.tsx';
import { Separator } from '@/components/ui/separator';
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group.tsx';
import { cn } from '@/lib/utils';

export type ScannerScope = 'both' | 'packages' | 'projects';

type SortablePluginListItemProps = {
  id: string;
  index: number;
  children: (dragHandle: React.ReactNode) => React.ReactNode;
};

function SortablePluginListItem({
  id,
  index,
  children,
}: SortablePluginListItemProps) {
  const { ref, handleRef, isDragging } = useSortable({
    id,
    index,
    type: 'plugin',
    accept: 'plugin',
  });

  return (
    <FormItem
      ref={ref}
      className={cn(
        'flex flex-row items-start space-y-0 space-x-3',
        isDragging && 'opacity-60'
      )}
    >
      {children(
        <button
          ref={handleRef}
          type='button'
          aria-label={`Reorder ${id}`}
          className='text-muted-foreground flex h-4 w-4 cursor-grab items-center justify-center rounded-sm hover:text-black focus-visible:ring-2 focus-visible:outline-none active:cursor-grabbing'
        >
          <GripVerticalIcon className='h-4 w-4' />
        </button>
      )}
    </FormItem>
  );
}

type PluginMultiSelectFieldProps<
  TFieldValues extends FieldValues,
  TName extends FieldPathByValue<TFieldValues, Array<string>>,
> = {
  form: UseFormReturn<TFieldValues, TName>;
  name: TName;
  configName: TName;
  /**
   * Optional field path for a `Record<string, ScannerScope>` value. When provided,
   * a scope toggle ("Both" / "Packages only" / "Projects only") is shown next to
   * each enabled plugin so the user can control whether the scanner runs on packages,
   * projects, or both.
   */
  scannerScopeName?: TName;
  label?: string;
  description?: React.ReactNode;
  plugins: readonly PreconfiguredPluginDescriptor[];
  secrets: readonly Secret[];
  /**
   * Enable drag-and-drop reordering for plugins. In this mode, all plugins are
   * displayed in a sortable list and selected plugins are stored in the same
   * order in which they appear in the list.
   */
  enableReordering?: boolean;
  /**
   * Show selected plugins first in their form value order before the user has
   * reordered the list. This is intended for reruns, where an existing payload
   * order should be reflected in the UI.
   */
  showSelectedPluginsFirst?: boolean;
  className?: string;
};

export const PluginMultiSelectField = <
  TFieldValues extends FieldValues,
  TName extends FieldPathByValue<TFieldValues, Array<string>>,
>({
  form,
  name,
  configName,
  scannerScopeName,
  label,
  description,
  plugins,
  secrets,
  enableReordering = false,
  showSelectedPluginsFirst = false,
  className,
}: PluginMultiSelectFieldProps<TFieldValues, TName>) => {
  const [pluginOrder, setPluginOrder] = React.useState<string[]>();

  return (
    <FormField
      control={form.control}
      name={name}
      render={({ field }) => {
        const selectedPluginIds = (field.value ?? []) as string[];
        const selectedPluginIdSet = new Set(selectedPluginIds);
        const pluginsInDisplayOrder = getPluginsInDisplayOrder(
          plugins,
          selectedPluginIds,
          showSelectedPluginsFirst,
          pluginOrder
        );

        const selectedPluginIdsInOrder = (
          orderedPlugins: readonly PreconfiguredPluginDescriptor[],
          selectedIds: ReadonlySet<string>
        ) =>
          orderedPlugins
            .map((plugin) => plugin.id)
            .filter((pluginId) => selectedIds.has(pluginId));

        const renderPluginItemContent = (
          plugin: PreconfiguredPluginDescriptor,
          dragHandle?: React.ReactNode
        ) => {
          const isSelected = selectedPluginIdSet.has(plugin.id);

          return (
            <>
              {dragHandle}
              <FormControl>
                <Checkbox
                  checked={isSelected}
                  onCheckedChange={(checked) => {
                    if (checked === true) {
                      field.onChange(
                        enableReordering
                          ? selectedPluginIdsInOrder(
                              pluginsInDisplayOrder,
                              new Set([...selectedPluginIds, plugin.id])
                            )
                          : [...selectedPluginIds, plugin.id]
                      );
                      if (scannerScopeName) {
                        const scopePath =
                          `${scannerScopeName}.${plugin.id}` as Path<TFieldValues>;
                        if (!form.getValues(scopePath)) {
                          form.setValue(
                            scopePath,
                            'both' as FieldPathValue<
                              TFieldValues,
                              Path<TFieldValues>
                            >
                          );
                        }
                      }
                    } else {
                      const nextSelectedPluginIds = selectedPluginIds.filter(
                        (value: string) => value !== plugin.id
                      );

                      field.onChange(
                        enableReordering
                          ? selectedPluginIdsInOrder(
                              pluginsInDisplayOrder,
                              new Set(nextSelectedPluginIds)
                            )
                          : nextSelectedPluginIds
                      );
                      if (scannerScopeName) {
                        form.setValue(
                          `${scannerScopeName}.${plugin.id}` as Path<TFieldValues>,
                          undefined as FieldPathValue<
                            TFieldValues,
                            Path<TFieldValues>
                          >
                        );
                      }
                    }
                  }}
                />
              </FormControl>
              <div className='flex flex-col'>
                <FormLabel className='font-normal'>
                  {plugin.displayName}
                </FormLabel>
                {plugin.summary != null && (
                  <MarkdownRenderer
                    markdown={plugin.summary}
                    className='text-muted-foreground max-w-none pb-1 [&_p]:my-0'
                  />
                )}
                {scannerScopeName && isSelected && (
                  <FormField
                    control={form.control}
                    name={
                      `${scannerScopeName}.${plugin.id}` as Path<TFieldValues>
                    }
                    render={({ field: scopeField }) => (
                      <FormItem className='mb-2 flex flex-col space-y-1'>
                        <FormControl>
                          <ToggleGroup
                            type='single'
                            variant='outline'
                            value={(scopeField.value as ScannerScope) ?? 'both'}
                            onValueChange={(value) => {
                              if (value)
                                scopeField.onChange(value as ScannerScope);
                            }}
                            className='gap-0 self-start'
                          >
                            <ToggleGroupItem
                              value='both'
                              className='rounded-r-none text-xs data-[state=on]:bg-blue-500 data-[state=on]:text-white'
                            >
                              Both
                            </ToggleGroupItem>
                            <ToggleGroupItem
                              value='packages'
                              className='-ml-px rounded-none text-xs data-[state=on]:bg-blue-500 data-[state=on]:text-white'
                            >
                              Packages only
                            </ToggleGroupItem>
                            <ToggleGroupItem
                              value='projects'
                              className='-ml-px rounded-l-none text-xs data-[state=on]:bg-blue-500 data-[state=on]:text-white'
                            >
                              Projects only
                            </ToggleGroupItem>
                          </ToggleGroup>
                        </FormControl>
                      </FormItem>
                    )}
                  />
                )}
                {isSelected &&
                  plugin.options.map((option) => (
                    <FormField
                      control={form.control}
                      key={option.name}
                      name={
                        `${configName}.${plugin.id}.${option.type === 'SECRET' ? 'secrets' : 'options'}.${option.name}` as Path<TFieldValues>
                      }
                      render={({ field }) => (
                        <FormItem className='ml-4 flex flex-col pb-4'>
                          <FormLabel>
                            {option.name}
                            <Badge
                              variant='small'
                              className='bg-blue-200 text-black'
                            >
                              {option.type}
                            </Badge>
                          </FormLabel>
                          <FormControl>
                            {option.type === 'BOOLEAN' ? (
                              <Checkbox
                                checked={field.value as CheckedState}
                                onCheckedChange={field.onChange}
                                disabled={option.isFixed}
                              />
                            ) : option.type == 'SECRET' ? (
                              secrets.length === 0 ? (
                                <FormMessage className='font-semibold text-red-600'>
                                  No secrets available. Create a new secret to
                                  be able to use this option.
                                </FormMessage>
                              ) : (
                                <Select
                                  onValueChange={(value) => {
                                    field.onChange(mapSecretSelectValue(value));
                                  }}
                                  defaultValue={undefined}
                                  value={getSecretSelectDisplayValue(
                                    field.value,
                                    option.isRequired
                                  )}
                                  disabled={option.isFixed}
                                >
                                  <SelectTrigger>
                                    <SelectValue placeholder='Select a secret' />
                                  </SelectTrigger>
                                  <SelectContent>
                                    {!option.isRequired && (
                                      <SelectItem
                                        value={UNDEFINED_SECRET_VALUE}
                                      >
                                        Not defined
                                      </SelectItem>
                                    )}
                                    {secrets.map((secret) => (
                                      <SelectItem
                                        key={secret.name}
                                        value={secret.name}
                                      >
                                        {secret.name}
                                      </SelectItem>
                                    ))}
                                  </SelectContent>
                                  {field.value &&
                                    !secrets.some(
                                      (secret) => secret.name === field.value
                                    ) && (
                                      <FormMessage className='font-semibold text-red-600'>
                                        The selected secret '{field.value}' does
                                        not exist. The value could come from a
                                        previous run or could be a default value
                                        set by an administrator. Select a valid
                                        secret or create a new secret with this
                                        name.
                                      </FormMessage>
                                    )}
                                </Select>
                              )
                            ) : option.type === 'ENUM' &&
                              option.enumEntries &&
                              option.enumEntries.length > 0 ? (
                              <Select
                                onValueChange={(value) => {
                                  field.onChange(mapEnumSelectValue(value));
                                }}
                                value={getEnumSelectDisplayValue(
                                  typeof field.value === 'string'
                                    ? field.value
                                    : undefined,
                                  option.isRequired
                                )}
                                disabled={option.isFixed}
                              >
                                <SelectTrigger className='w-[280px]'>
                                  <SelectValue placeholder='Select a value' />
                                </SelectTrigger>
                                <SelectContent>
                                  {!option.isRequired && (
                                    <SelectItem value={UNDEFINED_ENUM_VALUE}>
                                      {option.defaultValue != null &&
                                      option.defaultValue !== ''
                                        ? 'Reset to default'
                                        : 'Not defined'}
                                    </SelectItem>
                                  )}
                                  {option.enumEntries.map((entry) => (
                                    <SelectItem key={entry} value={entry}>
                                      {entry}
                                    </SelectItem>
                                  ))}
                                </SelectContent>
                              </Select>
                            ) : option.isRequired ? (
                              <Input
                                {...field}
                                type={
                                  option.type === 'INTEGER' ||
                                  option.type === 'LONG'
                                    ? 'number'
                                    : 'text'
                                }
                                value={field.value}
                                disabled={option.isFixed}
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
                                value={field.value}
                                disabled={option.isFixed}
                              />
                            )}
                          </FormControl>
                          <FormDescription>
                            {option.description}
                          </FormDescription>
                          {option.isFixed && (
                            <FormDescription className='font-semibold text-yellow-700'>
                              This option is set by an administrator and cannot
                              be changed.
                            </FormDescription>
                          )}
                        </FormItem>
                      )}
                    />
                  ))}
              </div>
            </>
          );
        };

        const renderStaticPluginItem = (
          plugin: PreconfiguredPluginDescriptor
        ) => (
          <FormItem
            key={plugin.id}
            className='flex flex-row items-start space-y-0 space-x-3'
          >
            {renderPluginItemContent(plugin)}
          </FormItem>
        );

        return (
          <FormItem
            className={cn(
              'flex flex-col justify-between rounded-lg border p-4',
              className
            )}
          >
            <FormLabel>{label}</FormLabel>
            <FormDescription className='pb-4'>{description}</FormDescription>
            <div className='flex items-center space-x-3'>
              <Checkbox
                id='check-all-items'
                checked={
                  plugins.every((plugin) =>
                    form.getValues(name).includes(plugin.id)
                  )
                    ? true
                    : plugins.some((plugin) =>
                          form.getValues(name).includes(plugin.id)
                        )
                      ? 'indeterminate'
                      : false
                }
                onCheckedChange={(checked) => {
                  const enabledItems =
                    checked === true
                      ? enableReordering
                        ? pluginsInDisplayOrder.map((plugin) => plugin.id)
                        : plugins.map((plugin) => plugin.id)
                      : [];

                  form.setValue(
                    name,
                    // TypeScript doesn't get this, but TName extends FieldPathByValue<TFieldValues, Array<string>>,
                    // so the field behind TName is always an Array<string>
                    // and options.map((option) => option.id) is also Array<string>,
                    // so this type cast is safe.
                    enabledItems as FieldPathValue<TFieldValues, TName>
                  );
                  if (scannerScopeName) {
                    if (checked === true) {
                      plugins.forEach((plugin) => {
                        const scopePath =
                          `${scannerScopeName}.${plugin.id}` as Path<TFieldValues>;
                        if (!form.getValues(scopePath)) {
                          form.setValue(
                            scopePath,
                            'both' as FieldPathValue<
                              TFieldValues,
                              Path<TFieldValues>
                            >
                          );
                        }
                      });
                    } else {
                      plugins.forEach((plugin) => {
                        form.setValue(
                          `${scannerScopeName}.${plugin.id}` as Path<TFieldValues>,
                          undefined as FieldPathValue<
                            TFieldValues,
                            Path<TFieldValues>
                          >
                        );
                      });
                    }
                  }
                }}
              />
              <Label htmlFor='check-all-items' className='font-bold'>
                Enable/disable all
              </Label>
            </div>
            <Separator className='my-2' />
            {enableReordering ? (
              <DragDropProvider
                onDragEnd={(event) => {
                  if (event.canceled) return;

                  const { source } = event.operation;

                  if (isSortable(source)) {
                    const { initialIndex, index } = source;

                    if (initialIndex !== index) {
                      const nextPluginOrder = moveItem(
                        pluginsInDisplayOrder.map((plugin) => plugin.id),
                        initialIndex,
                        index
                      );

                      setPluginOrder(nextPluginOrder);
                      field.onChange(
                        nextPluginOrder.filter((pluginId) =>
                          selectedPluginIdSet.has(pluginId)
                        )
                      );
                    }
                  }
                }}
              >
                <div className='flex flex-col gap-2'>
                  {pluginsInDisplayOrder.map((plugin, index) => (
                    <SortablePluginListItem
                      key={plugin.id}
                      id={plugin.id}
                      index={index}
                    >
                      {(dragHandle) =>
                        renderPluginItemContent(plugin, dragHandle)
                      }
                    </SortablePluginListItem>
                  ))}
                </div>
              </DragDropProvider>
            ) : (
              plugins.map(renderStaticPluginItem)
            )}
            <FormMessage />
          </FormItem>
        );
      }}
    />
  );
};
