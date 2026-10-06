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

import { SquareMinus, SquarePlus } from 'lucide-react';

import type { DependencyGraph } from '@/api';
import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/utils';
import type { PackageIdType } from '@/schemas';
import { formatDependencyGraphPackageLabel } from './dependency-graph-utils';
import {
  ancestorLastFlags,
  type ExpandableRow,
  type DependencyTreeRow as Row,
} from './dependency-tree-model';
import { HighlightedMatch } from './highlighted-match';
import { PackageCountBadge } from './package-count-badge';

/** The horizontal distance between two levels of the tree, in pixels. */
const INDENT_PX = 24;

/** Where the connector lines of a level start, relative to its parent level. */
const CONNECTOR_OFFSET_PX = 8;

/** The vertical position of the horizontal connector lines, in pixels. */
const CONNECTOR_TOP_PX = 11;

const connectorLeft = (level: number) =>
  (level - 1) * INDENT_PX + CONNECTOR_OFFSET_PX;

/**
 * Draws the lines that connect a row to its parent and continue the lines of
 * its ancestors past it. Each row draws its lines over its whole height,
 * including the space to the next row, so the lines of adjacent rows join up
 * even if the ancestor they belong to is not mounted.
 */
const Connectors = ({ row }: { row: Row }) => {
  if (row.depth === 0) return null;

  // The flags of the ancestors from the project down; the project itself has
  // no connector, so its flag is not used.
  const ancestorsLast = ancestorLastFlags(row);
  const ownLeft = connectorLeft(row.depth);

  return (
    <>
      {ancestorsLast.map((isLast, level) =>
        level === 0 || isLast ? null : (
          <span
            key={level}
            aria-hidden
            className='bg-border absolute top-0 bottom-0 w-px'
            style={{ left: connectorLeft(level) }}
          />
        )
      )}
      <span
        aria-hidden
        className={cn(
          'bg-border absolute top-0 w-px',
          !row.isLast && 'bottom-0'
        )}
        style={{
          left: ownLeft,
          height: row.isLast ? CONNECTOR_TOP_PX : undefined,
        }}
      />
      <span
        aria-hidden
        className='bg-border absolute h-px w-4'
        style={{ left: ownLeft, top: CONNECTOR_TOP_PX }}
      />
    </>
  );
};

/** Shows whether an expandable row is expanded or collapsed. */
const ToggleIcon = ({ expanded }: { expanded: boolean }) => {
  const Icon = expanded ? SquareMinus : SquarePlus;

  return (
    <Icon
      aria-hidden
      className='text-muted-foreground mt-[3px] size-4 shrink-0'
    />
  );
};

const RowLabel = ({
  row,
  graph,
  packageIdType,
  searchTerm,
}: {
  row: Row;
  graph: DependencyGraph;
  packageIdType: PackageIdType;
  searchTerm: string;
}) => {
  switch (row.kind) {
    case 'project': {
      const projectLabel =
        graph.projectGroups[row.projectIndex]?.projectLabel ?? '';

      return (
        <div className='flex min-w-0 flex-wrap items-center gap-2'>
          <span className='block min-w-0 text-sm font-semibold break-all'>
            <HighlightedMatch searchTerm={searchTerm} text={projectLabel} />
          </span>
          <PackageCountBadge count={row.packageCount} />
        </div>
      );
    }

    case 'scope': {
      const scopeLabel =
        graph.projectGroups[row.projectIndex]?.scopes[row.scopeIndex]
          ?.scopeLabel;

      return (
        <div className='flex min-w-0 flex-wrap items-center gap-2'>
          {scopeLabel && (
            <Badge variant='outline'>
              <HighlightedMatch searchTerm={searchTerm} text={scopeLabel} />
            </Badge>
          )}
          <PackageCountBadge count={row.packageCount} />
        </div>
      );
    }

    case 'package':
      return (
        <div className='flex min-w-0 flex-1 flex-wrap items-center gap-2'>
          <span className='min-w-0 text-sm font-medium break-all'>
            <HighlightedMatch
              searchTerm={searchTerm}
              text={formatDependencyGraphPackageLabel(
                graph,
                row.packageIndex,
                packageIdType
              )}
            />
          </span>
          <Badge variant='outline'>{row.linkage}</Badge>
          {row.expandable && <PackageCountBadge count={row.packageCount} />}
        </div>
      );

    case 'cycle':
      return (
        <div className='flex flex-wrap items-center gap-2'>
          <span className='min-w-0 text-sm font-medium break-all'>
            <HighlightedMatch
              searchTerm={searchTerm}
              text={formatDependencyGraphPackageLabel(
                graph,
                row.packageIndex,
                packageIdType
              )}
            />
          </span>
          <Badge variant='outline'>cycle</Badge>
        </div>
      );
  }
};

type DependencyTreeRowProps = {
  row: Row;
  /** The position of the row in the list, used to measure its height. */
  index: number;
  /** The distance of the row from the top of the list, in pixels. */
  top: number;
  graph: DependencyGraph;
  packageIdType: PackageIdType;
  searchTerm: string;
  measureRef: (element: HTMLElement | null) => void;
  onToggle: (row: ExpandableRow) => void;
};

/** A single row of the dependency tree, positioned absolutely in the list. */
export const DependencyTreeRow = ({
  row,
  index,
  top,
  graph,
  packageIdType,
  searchTerm,
  measureRef,
  onToggle,
}: DependencyTreeRowProps) => {
  const label = (
    <RowLabel
      row={row}
      graph={graph}
      packageIdType={packageIdType}
      searchTerm={searchTerm}
    />
  );

  return (
    <div
      ref={measureRef}
      data-index={index}
      data-dependency-tree-row=''
      className='absolute top-0 left-0 w-full pb-2'
      style={{
        transform: `translateY(${top}px)`,
        paddingLeft: row.depth * INDENT_PX,
      }}
    >
      <Connectors row={row} />
      {row.kind !== 'cycle' && row.expandable ? (
        <button
          type='button'
          aria-expanded={row.expanded}
          className='flex w-full items-start gap-2 rounded-sm text-left'
          onClick={() => onToggle(row)}
        >
          <ToggleIcon expanded={row.expanded} />
          <div className='min-w-0 flex-1'>{label}</div>
        </button>
      ) : row.kind === 'cycle' ? (
        label
      ) : (
        <div className='flex items-start gap-2'>
          <div className='mt-[3px] size-4 shrink-0' />
          {label}
        </div>
      )}
    </div>
  );
};
