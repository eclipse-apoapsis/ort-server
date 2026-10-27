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

import { TrashIcon } from 'lucide-react';
import { useFieldArray, UseFormReturn, useWatch } from 'react-hook-form';

import { InlineCode } from '@/components/typography.tsx';
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion';
import { Button } from '@/components/ui/button';
import {
  FormControl,
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
import {
  formatReason,
  getReasonOptions,
  guessReason,
  type IncludeExcludeDirection,
  type IncludeExcludeKind,
} from './includes-excludes';
import { PatternCombobox } from './pattern-combobox';
import type { CreateRunFormValues } from './run-schema';

type IncludesExcludesSectionProps = {
  form: UseFormReturn<CreateRunFormValues>;
  /** The open accordion items of the form; the items of this section are "scopes" and "paths". */
  openItems: string[];
  onOpenItemsChange: (openItems: string[]) => void;
};

/** The section of the create run form for the path and scope includes and excludes of a run. */
export const IncludesExcludesSection = ({
  form,
  openItems,
  onOpenItemsChange,
}: IncludesExcludesSectionProps) => (
  <>
    <h3 className='mt-4'>Includes and excludes</h3>
    <div className='text-sm text-gray-500'>
      Exclude scopes and paths from this run, or include only some of them.
      These apply in addition to the excludes and includes in the repository's{' '}
      <InlineCode>.ort.yml</InlineCode>.
    </div>
    <Accordion
      type='multiple'
      className='ml-4'
      value={openItems}
      onValueChange={onOpenItemsChange}
    >
      <IncludesExcludesItem form={form} kind='scopes' />
      <IncludesExcludesItem form={form} kind='paths' />
    </Accordion>
  </>
);

const texts = {
  scopes: {
    title: 'Scopes',
    description: (
      <>
        Scope patterns are regular expressions that must match the whole scope
        name, for example <InlineCode>test.*</InlineCode>.
      </>
    ),
    includeNote: 'Only scopes matching an include are analyzed.',
  },
  paths: {
    title: 'Paths',
    description: (
      <>
        Path patterns are globs relative to the repository root, for example{' '}
        <InlineCode>docs/**</InlineCode>.
      </>
    ),
    includeNote: 'Only paths matching an include are analyzed.',
  },
} as const;

// The widths of the columns, shared by the header and the rows so that they line up.
const columns = {
  direction: 'w-28 shrink-0',
  pattern: 'min-w-48 flex-1',
  reason: 'w-60 shrink-0',
  comment: 'min-w-40 flex-1',
  // The width of the remove button.
  actions: 'w-9 shrink-0',
};

// When the section is narrow, the fields of a row wrap, so each field shows its own label instead
// of the header.
const fieldLabelClass = '@[50rem]:sr-only';

const directionLabels: Record<IncludeExcludeDirection, string> = {
  exclude: 'Exclude',
  include: 'Include',
};

type IncludesExcludesItemProps = {
  form: UseFormReturn<CreateRunFormValues>;
  kind: IncludeExcludeKind;
};

const IncludesExcludesItem = ({ form, kind }: IncludesExcludesItemProps) => {
  const { fields, append, remove } = useFieldArray({
    name: kind,
    control: form.control,
  });
  const entries = useWatch({ control: form.control, name: kind });

  const excluded = entries.filter((e) => e.direction === 'exclude').length;
  const included = entries.length - excluded;
  const summary =
    entries.length === 0
      ? 'None'
      : [
          excluded > 0 ? `${excluded} excluded` : undefined,
          included > 0 ? `${included} included` : undefined,
        ]
          .filter(Boolean)
          .join(', ');

  const changeDirection = (
    index: number,
    direction: IncludeExcludeDirection
  ) => {
    const entry = form.getValues(`${kind}.${index}`);
    form.setValue(`${kind}.${index}.direction`, direction, {
      shouldDirty: true,
    });
    // The reasons differ between includes and excludes.
    if (!getReasonOptions(kind, direction).includes(entry.reason)) {
      form.setValue(
        `${kind}.${index}.reason`,
        guessReason(kind, direction, entry.pattern),
        { shouldDirty: true }
      );
    }
  };

  return (
    <AccordionItem value={kind}>
      <AccordionTrigger>
        <span>
          {texts[kind].title}
          <span className='text-muted-foreground font-normal'>
            {' · '}
            {summary}
          </span>
        </span>
      </AccordionTrigger>
      <AccordionContent className='@container flex flex-col gap-3'>
        <div className='text-sm text-gray-500'>{texts[kind].description}</div>
        {fields.length > 0 && (
          <div
            aria-hidden
            className='-mb-1 hidden gap-2 text-sm font-medium @[50rem]:flex'
          >
            <div className={columns.direction}>Direction</div>
            <div className={columns.pattern}>Pattern</div>
            <div className={columns.reason}>Reason</div>
            <div className={columns.comment}>Comment</div>
            <div className={columns.actions} />
          </div>
        )}
        {fields.map((field, index) => {
          const direction = entries[index]?.direction ?? field.direction;

          return (
            <div
              key={field.id}
              className='flex flex-wrap items-start gap-2 @[50rem]:flex-nowrap'
            >
              <FormField
                control={form.control}
                name={`${kind}.${index}.direction`}
                render={({ field }) => (
                  <FormItem className={columns.direction}>
                    <FormLabel className={fieldLabelClass}>Direction</FormLabel>
                    <Select
                      value={field.value}
                      onValueChange={(value) =>
                        changeDirection(index, value as IncludeExcludeDirection)
                      }
                    >
                      <FormControl>
                        <SelectTrigger className='w-full'>
                          <SelectValue />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {(['exclude', 'include'] as const).map((value) => (
                          <SelectItem key={value} value={value}>
                            {directionLabels[value]}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name={`${kind}.${index}.pattern`}
                render={({ field }) => (
                  <FormItem className={columns.pattern}>
                    <FormLabel className={fieldLabelClass}>Pattern</FormLabel>
                    <FormControl>
                      <Input {...field} className='font-mono' />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name={`${kind}.${index}.reason`}
                render={({ field }) => (
                  <FormItem className={columns.reason}>
                    <FormLabel className={fieldLabelClass}>Reason</FormLabel>
                    <Select value={field.value} onValueChange={field.onChange}>
                      <FormControl>
                        <SelectTrigger className='w-full'>
                          <SelectValue placeholder='Choose a reason' />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {getReasonOptions(kind, direction).map((reason) => (
                          <SelectItem key={reason} value={reason}>
                            {formatReason(reason)}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name={`${kind}.${index}.comment`}
                render={({ field }) => (
                  <FormItem className={columns.comment}>
                    <FormLabel className={fieldLabelClass}>Comment</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder='(optional)' />
                    </FormControl>
                  </FormItem>
                )}
              />
              <Button
                type='button'
                variant='outline'
                size='icon'
                aria-label='Remove pattern'
                className={columns.actions}
                onClick={() => remove(index)}
              >
                <TrashIcon className='h-4 w-4' />
              </Button>
            </div>
          );
        })}
        {included > 0 && (
          <div className='text-sm text-gray-500'>{texts[kind].includeNote}</div>
        )}
        <PatternCombobox
          kind={kind}
          entries={entries}
          // Keep the focus in the list of suggestions, so that it stays open.
          onAdd={(entry) => append(entry, { shouldFocus: false })}
          onRemove={remove}
        />
      </AccordionContent>
    </AccordionItem>
  );
};
