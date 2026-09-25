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

import { DragDropProvider } from '@dnd-kit/react';
import { isSortable, useSortable } from '@dnd-kit/react/sortable';
import { GripVerticalIcon } from 'lucide-react';
import type { ReactNode } from 'react';

import type { PreconfiguredPluginDescriptor } from '@/api';

type SortablePluginListProps = {
  plugins: readonly PreconfiguredPluginDescriptor[];
  renderItem: (
    plugin: PreconfiguredPluginDescriptor,
    dragHandle: ReactNode
  ) => ReactNode;
  onReorder: (fromIndex: number, toIndex: number) => void;
};

/** A plugin list whose entries can be reordered by drag and drop. */
export const SortablePluginList = ({
  plugins,
  renderItem,
  onReorder,
}: SortablePluginListProps) => (
  <DragDropProvider
    onDragEnd={(event) => {
      if (event.canceled) return;

      const { source } = event.operation;

      if (isSortable(source)) {
        const { initialIndex, index } = source;

        if (initialIndex !== index) {
          onReorder(initialIndex, index);
        }
      }
    }}
  >
    <div className='flex flex-col gap-2'>
      {plugins.map((plugin, index) => (
        <SortableEntry key={plugin.id} id={plugin.id} index={index}>
          {(dragHandle) => renderItem(plugin, dragHandle)}
        </SortableEntry>
      ))}
    </div>
  </DragDropProvider>
);

type SortableEntryProps = {
  id: string;
  index: number;
  children: (dragHandle: ReactNode) => ReactNode;
};

/**
 * Make one entry draggable. The entry's own content is wrapped, so that it does not need to
 * know that it is sortable.
 */
const SortableEntry = ({ id, index, children }: SortableEntryProps) => {
  const { ref, handleRef, isDragging } = useSortable({
    id,
    index,
    type: 'plugin',
    accept: 'plugin',
  });

  return (
    <div ref={ref} className={isDragging ? 'opacity-60' : undefined}>
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
    </div>
  );
};
