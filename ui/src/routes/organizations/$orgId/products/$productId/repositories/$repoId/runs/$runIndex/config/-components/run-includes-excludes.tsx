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
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
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
 * Show the path and scope includes and excludes that were set when a run was created, one
 * table each. Renders nothing for runs without them.
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
    <div className='flex flex-col gap-4'>
      {groups.map((group) => (
        <section key={group.title}>
          <h4 className='mb-1 text-sm font-semibold'>{group.title}</h4>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Direction</TableHead>
                <TableHead>Pattern</TableHead>
                <TableHead>Reason</TableHead>
                <TableHead>Comment</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {group.rows.map((row) => (
                <TableRow key={`${row.type}:${row.pattern}`}>
                  <TableCell>{capitalize(row.type)}</TableCell>
                  <TableCell className='font-mono break-words whitespace-normal'>
                    {row.pattern}
                  </TableCell>
                  <TableCell>{formatReason(row.reason)}</TableCell>
                  <TableCell className='text-muted-foreground break-words whitespace-normal'>
                    {row.comment}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </section>
      ))}
    </div>
  );
};
