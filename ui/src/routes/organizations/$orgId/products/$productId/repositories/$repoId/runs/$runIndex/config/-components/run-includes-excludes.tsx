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

import type { PathIncludesExcludes, ScopeIncludesExcludes } from '@/api';
import { Label } from '@/components/ui/label';
import { capitalize } from '@/helpers/capitalize';
import {
  formatReason,
  includesExcludesToFormEntries,
} from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/create-run/-components/includes-excludes';

type RunIncludesExcludesProps = {
  scopes: ScopeIncludesExcludes | null | undefined;
  paths: PathIncludesExcludes | null | undefined;
};

/**
 * Show the path and scope includes and excludes that were set when a run was created. Renders
 * nothing for runs without them.
 */
export const RunIncludesExcludes = ({
  scopes,
  paths,
}: RunIncludesExcludesProps) => {
  const groups = [
    { title: 'Scopes', rows: includesExcludesToFormEntries(scopes) },
    { title: 'Paths', rows: includesExcludesToFormEntries(paths) },
  ].filter((group) => group.rows.length > 0);

  if (groups.length === 0) return null;

  return (
    <div className='text-sm'>
      <Label className='font-semibold'>Includes and excludes:</Label>
      <div className='text-muted-foreground ml-4 text-xs'>
        Set when the run was created, in addition to those in the repository's{' '}
        <code className='font-mono'>.ort.yml</code>.
      </div>
      {groups.map((group) => (
        <div key={group.title} className='mt-1 ml-4 text-xs'>
          <div className='font-medium'>{group.title}</div>
          {group.rows.map((row) => (
            <div
              key={`${row.direction}:${row.pattern}`}
              className='ml-4 flex flex-wrap items-baseline gap-x-3'
            >
              <span className='w-12 shrink-0'>{capitalize(row.direction)}</span>
              <span className='font-mono break-all'>{row.pattern}</span>
              <span>{formatReason(row.reason)}</span>
              {row.comment && (
                <span className='text-muted-foreground break-all'>
                  {row.comment}
                </span>
              )}
            </div>
          ))}
        </div>
      ))}
    </div>
  );
};
