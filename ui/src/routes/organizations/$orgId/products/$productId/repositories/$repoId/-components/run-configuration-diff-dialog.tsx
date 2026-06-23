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

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Separator } from '@/components/ui/separator';
import type {
  RunConfigurationDiff,
  RunConfigurationDiffEntry,
  RunConfigurationDiffStatus,
} from '@/helpers/config-diff';
import { cn } from '@/lib/utils';

type RunConfigurationDiffDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  baseRunIndex: number;
  comparedRunIndex: number;
  diff?: RunConfigurationDiff;
  isLoading?: boolean;
  isError?: boolean;
  isConfigurationMissing?: boolean;
};

type RunConfigurationDiffSectionProps = {
  title: string;
  status: RunConfigurationDiffStatus;
  entries: RunConfigurationDiffEntry[];
};

const maxValueLength = 280;

function formatRunConfigurationDiffValue(value: unknown): string {
  const formattedValue = formatValue(value);

  if (formattedValue.length <= maxValueLength) {
    return formattedValue;
  }

  return `${formattedValue.slice(0, maxValueLength - 1)}…`;
}

function formatRunConfigurationDiffSummary(diff: RunConfigurationDiff): string {
  return `${diff.counts.added} added, ${diff.counts.removed} removed, ${diff.counts.modified} modified`;
}

const formatValue = (value: unknown): string => {
  if (value === undefined) {
    return 'undefined';
  }

  if (typeof value === 'string') {
    return JSON.stringify(value);
  }

  if (
    value === null ||
    typeof value === 'boolean' ||
    typeof value === 'number'
  ) {
    return String(value);
  }

  const jsonValue = JSON.stringify(value);

  return jsonValue ?? String(value);
};

const RunConfigurationDiffEntryRow = ({
  entry,
}: {
  entry: RunConfigurationDiffEntry;
}) => {
  if (entry.status === 'added') {
    return (
      <div className='leading-5 break-words whitespace-pre-wrap'>
        <span className='text-green-400'>+ </span>
        <code className='font-semibold'>{entry.path}</code>
        <span className='text-muted-foreground'>: </span>
        <span className='text-muted-foreground'>
          {formatRunConfigurationDiffValue(entry.newValue)}
        </span>
      </div>
    );
  }

  if (entry.status === 'removed') {
    return (
      <div className='leading-5 break-words whitespace-pre-wrap'>
        <span className='text-red-400'>- </span>
        <code className='font-semibold'>{entry.path}</code>
        <span className='text-muted-foreground'>: </span>
        <span className='text-muted-foreground'>
          {formatRunConfigurationDiffValue(entry.oldValue)}
        </span>
      </div>
    );
  }

  return (
    <div className='space-y-0 leading-5 break-words whitespace-pre-wrap'>
      <div>
        <span className='text-yellow-400'>~ </span>
        <code className='font-semibold'>{entry.path}</code>
      </div>
      <div className='pl-5'>
        <span className='text-red-400'>- </span>
        <span className='text-muted-foreground'>
          {formatRunConfigurationDiffValue(entry.oldValue)}
        </span>
      </div>
      <div className='pl-5'>
        <span className='text-green-400'>+ </span>
        <span className='text-muted-foreground'>
          {formatRunConfigurationDiffValue(entry.newValue)}
        </span>
      </div>
    </div>
  );
};

const RunConfigurationDiffSection = ({
  title,
  status,
  entries,
}: RunConfigurationDiffSectionProps) => {
  if (entries.length === 0) {
    return null;
  }

  return (
    <section className='space-y-1'>
      <h3
        className={cn(
          'text-sm font-semibold',
          status === 'added' && 'text-green-400',
          status === 'removed' && 'text-red-400',
          status === 'modified' && 'text-yellow-400'
        )}
      >
        {title}
      </h3>
      <div
        className={cn(
          'space-y-2 rounded-md border p-3 font-mono text-xs',
          status === 'added' && 'border-green-700/70 bg-green-950/30',
          status === 'removed' && 'border-red-700/70 bg-red-950/30',
          status === 'modified' && 'border-yellow-700/70 bg-yellow-950/30'
        )}
      >
        {entries.map((entry) => (
          <RunConfigurationDiffEntryRow
            key={`${entry.status}:${entry.path}`}
            entry={entry}
          />
        ))}
      </div>
    </section>
  );
};

const RunConfigurationDiffContent = ({
  diff,
  isLoading,
  isError,
  isConfigurationMissing,
}: Pick<
  RunConfigurationDiffDialogProps,
  'diff' | 'isLoading' | 'isError' | 'isConfigurationMissing'
>) => {
  if (isLoading) {
    return (
      <p className='text-muted-foreground text-sm'>
        Loading run configurations...
      </p>
    );
  }

  if (isError) {
    return (
      <p className='text-destructive text-sm'>
        Unable to load one or both selected runs.
      </p>
    );
  }

  if (isConfigurationMissing) {
    return (
      <p className='text-muted-foreground text-sm'>
        Resolved job configuration is not available for one of the selected
        runs.
      </p>
    );
  }

  if (!diff) {
    return (
      <p className='text-muted-foreground text-sm'>
        Select two runs to compare.
      </p>
    );
  }

  if (diff.counts.total === 0) {
    return (
      <p className='text-muted-foreground text-sm'>
        The resolved job configurations are identical.
      </p>
    );
  }

  return (
    <div className='space-y-5'>
      <RunConfigurationDiffSection
        title={`Added (${diff.counts.added})`}
        status='added'
        entries={diff.added}
      />
      <RunConfigurationDiffSection
        title={`Removed (${diff.counts.removed})`}
        status='removed'
        entries={diff.removed}
      />
      <RunConfigurationDiffSection
        title={`Modified (${diff.counts.modified})`}
        status='modified'
        entries={diff.modified}
      />
    </div>
  );
};

export function RunConfigurationDiffDialog({
  open,
  onOpenChange,
  baseRunIndex,
  comparedRunIndex,
  diff,
  isLoading,
  isError,
  isConfigurationMissing,
}: RunConfigurationDiffDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className='grid max-h-[min(calc(100vh-4rem),48rem)] grid-rows-[auto_auto_minmax(0,1fr)_auto] overflow-hidden sm:max-w-4xl'>
        <DialogHeader>
          <DialogTitle>
            Compare run #{baseRunIndex} (before) with run #{comparedRunIndex}{' '}
            (after)
          </DialogTitle>
          <DialogDescription>
            Changes in the resolved job configurations for the selected runs.
          </DialogDescription>
          {diff && !isLoading && !isError && !isConfigurationMissing && (
            <div className='flex flex-wrap gap-2 text-sm'>
              <Badge variant='secondary'>
                {formatRunConfigurationDiffSummary(diff)}
              </Badge>
            </div>
          )}
        </DialogHeader>

        <Separator />

        <div className='min-h-0 overflow-y-auto overscroll-contain pr-2'>
          <RunConfigurationDiffContent
            diff={diff}
            isLoading={isLoading}
            isError={isError}
            isConfigurationMissing={isConfigurationMissing}
          />
        </div>

        <DialogFooter>
          <DialogClose asChild>
            <Button type='button' variant='outline'>
              Close
            </Button>
          </DialogClose>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

if (import.meta.vitest) {
  const { describe, expect, it } = import.meta.vitest;

  describe('formatRunConfigurationDiffValue', () => {
    it('quotes strings', () => {
      expect(formatRunConfigurationDiffValue('ScanCode')).toBe('"ScanCode"');
    });

    it('formats primitive values compactly', () => {
      expect(formatRunConfigurationDiffValue(true)).toBe('true');
      expect(formatRunConfigurationDiffValue(1)).toBe('1');
      expect(formatRunConfigurationDiffValue(null)).toBe('null');
    });

    it('formats objects and arrays as compact JSON', () => {
      expect(formatRunConfigurationDiffValue({ enabled: true })).toBe(
        '{"enabled":true}'
      );
      expect(formatRunConfigurationDiffValue(['ScanCode', 'FossID'])).toBe(
        '["ScanCode","FossID"]'
      );
    });

    it('truncates long values', () => {
      expect(formatRunConfigurationDiffValue('a'.repeat(400))).toHaveLength(
        maxValueLength
      );
      expect(formatRunConfigurationDiffValue('a'.repeat(400))).toMatch(/…$/u);
    });
  });

  describe('formatRunConfigurationDiffSummary', () => {
    it('formats added, removed, and modified counts', () => {
      expect(
        formatRunConfigurationDiffSummary({
          added: [],
          removed: [],
          modified: [],
          counts: {
            added: 3,
            removed: 1,
            modified: 2,
            total: 6,
          },
        })
      ).toBe('3 added, 1 removed, 2 modified');
    });
  });
}
