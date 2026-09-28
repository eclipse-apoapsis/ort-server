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
import {
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion';
import { Checkbox } from '@/components/ui/checkbox';
import { cn } from '@/lib/utils';

type PluginListItemProps = {
  plugin: PreconfiguredPluginDescriptor;
  selected: boolean;
  onSelectedChange: (selected: boolean) => void;
  /** The handle for dragging the plugin to another position, if the list is sortable. */
  dragHandle?: ReactNode;
  /**
   * The plugin's settings, shown in a section that the plugin's name expands. The item must
   * be rendered inside an `Accordion`.
   */
  settings?: ReactNode;
  /** Markers shown next to the plugin's name. */
  markers?: ReactNode;
};

export const PluginListItem = ({
  plugin,
  selected,
  onSelectedChange,
  dragHandle,
  settings,
  markers,
}: PluginListItemProps) => {
  // The name is not the checkbox's label, because clicking it expands the settings.
  const checkbox = (
    <Checkbox
      aria-label={`Enable ${plugin.displayName}`}
      checked={selected}
      onCheckedChange={(checked) => onSelectedChange(checked === true)}
    />
  );
  // The name's row is as high as the markers, whether there are any or not, and the drag handle
  // and the checkbox are centered on it.
  const controls = (
    <div className='flex h-5.5 items-center space-x-3'>
      {dragHandle}
      {checkbox}
    </div>
  );
  const summary = (
    <MarkdownRenderer
      markdown={plugin.summary}
      className='text-muted-foreground max-w-none pb-1 [&_p]:my-0'
    />
  );

  // Without settings there is nothing to expand, so the name is plain text.
  if (!settings) {
    return (
      <div className='flex flex-row items-start space-x-3'>
        {controls}
        <div className='flex flex-col'>
          <span className='flex h-5.5 items-center text-sm leading-none'>
            {plugin.displayName}
          </span>
          {summary}
        </div>
      </div>
    );
  }

  // The drag handle and the checkbox sit beside the trigger, never inside it, and so does
  // the summary, which can contain links.
  return (
    <AccordionItem
      value={plugin.id}
      className={cn(
        'flex flex-row items-start space-x-3 border-b-0',
        // The content clips its children for the collapse animation, which would also cut off
        // the drop-down list of a multi-select near its bottom. So an open section does not
        // clip, and it opens without animation, which would otherwise draw the content over
        // the plugins below while the section grows.
        '[&_[data-slot=accordion-content][data-state=open]]:animate-none [&_[data-slot=accordion-content][data-state=open]]:overflow-visible'
      )}
    >
      {controls}
      <div className='flex flex-1 flex-col'>
        <AccordionTrigger className='items-center py-0 font-normal hover:no-underline'>
          <span className='flex min-h-5.5 flex-wrap items-center gap-2 leading-none'>
            {plugin.displayName}
            {markers}
          </span>
        </AccordionTrigger>
        {summary}
        <AccordionContent className='pb-0'>{settings}</AccordionContent>
      </div>
    </AccordionItem>
  );
};
