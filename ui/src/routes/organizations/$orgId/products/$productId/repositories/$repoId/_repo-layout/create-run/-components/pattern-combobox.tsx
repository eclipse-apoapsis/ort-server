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

import { Check, Loader2, PlusIcon } from 'lucide-react';
import { useState } from 'react';

import { Button } from '@/components/ui/button';
import {
  Command,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/ui/command';
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover';
import { cn } from '@/lib/utils';
import type { IncludeExcludePreset } from './include-exclude-presets';
import {
  guessReason,
  type IncludeExcludeDirection,
  type IncludeExcludeEntry,
  type IncludeExcludeKind,
} from './includes-excludes';
import {
  describeEffect,
  getSuggestionGroups,
  type ProjectSuggestions,
} from './project-suggestions';

type PatternComboboxProps = {
  kind: IncludeExcludeKind;
  /** The entries in the list, to mark the presets that are already in it. */
  entries: IncludeExcludeEntry[];
  onAdd: (entry: IncludeExcludeEntry) => void;
  onRemove: (index: number) => void;
  /** The suggestions from the projects of the last analyzed run, null if there is no such run. */
  projectSuggestions?: ProjectSuggestions | null;
  projectSuggestionsLoading?: boolean;
};

const texts = {
  scopes: {
    button: 'Add scope pattern',
    placeholder: 'Search suggestions or type a regular expression',
    exclude: 'Exclude scopes matching',
    include: 'Include only scopes matching',
  },
  paths: {
    button: 'Add path pattern',
    placeholder: 'Search suggestions or type a glob pattern',
    exclude: 'Exclude paths matching',
    include: 'Include only paths matching',
  },
} as const;

/**
 * A button that opens a list for adding includes and excludes, either from suggestions or from a
 * typed pattern. The suggestions for the package managers and the scopes or directories found in
 * the last analyzed run come first. Picking a suggestion adds it, or removes it again if it
 * is already in the list, and keeps the list open, so that several can be picked in a row.
 */
export const PatternCombobox = ({
  kind,
  entries,
  onAdd,
  onRemove,
  projectSuggestions,
  projectSuggestionsLoading = false,
}: PatternComboboxProps) => {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState('');
  const pattern = search.trim();
  const query = pattern.toLowerCase();

  const findEntry = (direction: IncludeExcludeDirection, value: string) =>
    entries.findIndex(
      (entry) => entry.direction === direction && entry.pattern.trim() === value
    );

  const allGroups = getSuggestionGroups(kind, projectSuggestions);
  const groups = allGroups
    .map((group) => ({
      ...group,
      presets: group.presets.filter((preset) =>
        [group.heading, preset.label, preset.pattern].some((text) =>
          text.toLowerCase().includes(query)
        )
      ),
    }))
    .filter((group) => group.presets.length > 0);

  // A typed pattern is offered in each direction that no suggestion has it in.
  const customDirections = (['exclude', 'include'] as const).filter(
    (direction) =>
      !allGroups.some((group) =>
        group.presets.some(
          (preset) =>
            preset.direction === direction && preset.pattern === pattern
        )
      )
  );

  const togglePreset = (preset: IncludeExcludePreset) => {
    const index = findEntry(preset.direction, preset.pattern);
    if (index >= 0) {
      onRemove(index);
    } else {
      onAdd({
        direction: preset.direction,
        pattern: preset.pattern,
        reason: preset.reason,
        comment: '',
      });
    }
  };

  const addCustom = (direction: IncludeExcludeDirection) => {
    onAdd({
      direction,
      pattern,
      reason: guessReason(kind, direction, pattern),
      comment: '',
    });
    setSearch('');
    setOpen(false);
  };

  return (
    <Popover
      open={open}
      onOpenChange={(isOpen) => {
        setOpen(isOpen);
        if (!isOpen) setSearch('');
      }}
    >
      <PopoverTrigger asChild>
        <Button size='sm' variant='outline' type='button' className='w-fit'>
          {texts[kind].button}
          <PlusIcon className='ml-1 h-4 w-4' />
        </Button>
      </PopoverTrigger>
      <PopoverContent
        className='w-[460px] max-w-[calc(100vw-2rem)] p-0'
        align='start'
      >
        <Command shouldFilter={false}>
          <CommandInput
            placeholder={texts[kind].placeholder}
            value={search}
            onValueChange={setSearch}
          />
          <CommandList className='max-h-[360px]'>
            {projectSuggestionsLoading && (
              <div className='text-muted-foreground flex items-center gap-2 px-3 py-2 text-sm'>
                <Loader2 size={16} className='animate-spin' />
                Reading the projects of the last run...
              </div>
            )}
            {groups.map((group) => (
              <CommandGroup key={group.heading} heading={group.heading}>
                {group.presets.map((preset) => {
                  const selected =
                    findEntry(preset.direction, preset.pattern) >= 0;
                  const effect =
                    projectSuggestions && !group.hideEffects
                      ? describeEffect(
                          kind,
                          preset.direction,
                          preset.pattern,
                          projectSuggestions
                        )
                      : undefined;

                  return (
                    <CommandItem
                      key={`${preset.direction}:${preset.pattern}`}
                      value={`${group.heading}:${preset.direction}:${preset.pattern}`}
                      onSelect={() => togglePreset(preset)}
                      className='items-start'
                    >
                      <Check
                        className={cn(
                          'mt-0.5 h-4 w-4 shrink-0',
                          selected ? 'opacity-100' : 'opacity-0'
                        )}
                      />
                      <span className='flex min-w-0 flex-col'>
                        <span className='break-all'>{preset.label}</span>
                        {/* A scope found in a run is labelled with its name, which is its pattern. */}
                        {preset.pattern !== preset.label && (
                          <code className='text-muted-foreground font-mono text-xs break-all'>
                            {preset.pattern}
                          </code>
                        )}
                        {effect && (
                          <span className='text-muted-foreground text-xs'>
                            {effect.text}
                          </span>
                        )}
                      </span>
                    </CommandItem>
                  );
                })}
              </CommandGroup>
            ))}
            {pattern !== '' && customDirections.length > 0 && (
              <CommandGroup heading='Custom pattern'>
                {customDirections.map((direction) => (
                  <CommandItem
                    key={direction}
                    value={`custom:${direction}`}
                    onSelect={() => addCustom(direction)}
                  >
                    <span className='min-w-0 break-all'>
                      {texts[kind][direction]}{' '}
                      <code className='font-mono'>{pattern}</code>
                    </span>
                  </CommandItem>
                ))}
              </CommandGroup>
            )}
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
};
