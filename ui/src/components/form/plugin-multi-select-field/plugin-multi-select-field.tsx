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
import { MarkdownRenderer } from '@/components/markdown-renderer';
import { Checkbox } from '@/components/ui/checkbox';
import {
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Label } from '@/components/ui/label';
import { Separator } from '@/components/ui/separator';
import { cn } from '@/lib/utils';
import { PluginSettings } from './plugin-settings';
import { getPluginsInDisplayOrder, moveItem } from './utils';

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
  /**
   * Optional field path for a `Record<string, string[]>` value. When provided, a
   * "Must run after" multi select is shown for each enabled plugin, offering the
   * IDs of all other available plugins.
   */
  mustRunAfterName?: TName;
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
  mustRunAfterName,
  label,
  description,
  plugins,
  secrets,
  enableReordering = false,
  showSelectedPluginsFirst = false,
  className,
}: PluginMultiSelectFieldProps<TFieldValues, TName>) => {
  const [pluginOrder, setPluginOrder] = React.useState<string[]>();
  const selectAllId = React.useId();

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
                      if (mustRunAfterName) {
                        form.setValue(
                          `${mustRunAfterName}.${plugin.id}` as Path<TFieldValues>,
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
                <MarkdownRenderer
                  markdown={plugin.summary}
                  className='text-muted-foreground max-w-none pb-1 [&_p]:my-0'
                />
                {isSelected && (
                  <PluginSettings
                    control={form.control}
                    plugin={plugin}
                    pluginIds={plugins.map((otherPlugin) => otherPlugin.id)}
                    secrets={secrets}
                    configName={configName}
                    scannerScopeName={scannerScopeName}
                    mustRunAfterName={mustRunAfterName}
                  />
                )}
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
                id={selectAllId}
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
                  if (mustRunAfterName && checked !== true) {
                    plugins.forEach((plugin) => {
                      form.setValue(
                        `${mustRunAfterName}.${plugin.id}` as Path<TFieldValues>,
                        undefined as FieldPathValue<
                          TFieldValues,
                          Path<TFieldValues>
                        >
                      );
                    });
                  }
                }}
              />
              <Label htmlFor={selectAllId} className='font-bold'>
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
