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

import type { ReactNode } from 'react';

import type { PreconfiguredPluginDescriptor } from '@/api';
import { MarkdownRenderer } from '@/components/markdown-renderer';
import { Checkbox } from '@/components/ui/checkbox';
import { FormControl, FormItem, FormLabel } from '@/components/ui/form';

type PluginListItemProps = {
  plugin: PreconfiguredPluginDescriptor;
  selected: boolean;
  onSelectedChange: (selected: boolean) => void;
  /** The handle for dragging the plugin to another position, if the list is sortable. */
  dragHandle?: ReactNode;
  /** The plugin's settings, shown below its summary. */
  settings?: ReactNode;
};

/**
 * One plugin in a plugin list: its enable checkbox, name and summary, followed by its
 * settings. The name is the label of the checkbox.
 */
export const PluginListItem = ({
  plugin,
  selected,
  onSelectedChange,
  dragHandle,
  settings,
}: PluginListItemProps) => (
  <FormItem className='flex flex-row items-start space-y-0 space-x-3'>
    {dragHandle}
    <FormControl>
      <Checkbox
        checked={selected}
        onCheckedChange={(checked) => onSelectedChange(checked === true)}
      />
    </FormControl>
    <div className='flex flex-col'>
      <FormLabel className='font-normal'>{plugin.displayName}</FormLabel>
      <MarkdownRenderer
        markdown={plugin.summary}
        className='text-muted-foreground max-w-none pb-1 [&_p]:my-0'
      />
      {settings}
    </div>
  </FormItem>
);
