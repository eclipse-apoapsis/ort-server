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

import { X } from 'lucide-react';
import { useMemo, useState } from 'react';

import type { DependencyGraph } from '@/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { TabsContent } from '@/components/ui/tabs';
import { useDebounce } from '@/hooks/use-debounce';
import { useUserSettingsStore } from '@/store/user-settings.store';
import {
  buildAdjacencyMap,
  createNodeSubtreeMatcher,
  matchesSearch,
  normalizeSearchTerm,
  scopeHasSearchMatch,
} from './dependency-graph-utils';
import { DependencyTree } from './dependency-tree';

export const ManagerDependenciesTab = ({
  graph,
  managerName,
}: {
  graph: DependencyGraph;
  managerName: string;
}) => {
  'use memo';

  const [searchValue, setSearchValue] = useState('');
  const packageIdType = useUserSettingsStore((state) => state.packageIdType);
  const debouncedSearchValue = useDebounce(searchValue);
  const searchTerm = normalizeSearchTerm(debouncedSearchValue);

  // Every keystroke renders this component, but the search term only changes
  // after the debounce delay. Keep the graph preparation and the matcher
  // unchanged in between, so that the memoized tree is not rendered again.
  const adjacency = useMemo(() => buildAdjacencyMap(graph), [graph]);
  const matchesNodeSubtree = useMemo(
    () => createNodeSubtreeMatcher(graph, adjacency, searchTerm, packageIdType),
    [graph, adjacency, searchTerm, packageIdType]
  );

  const hasMatches = useMemo(
    () =>
      !searchTerm ||
      graph.projectGroups.some(
        ({ projectLabel, scopes }) =>
          matchesSearch(projectLabel, searchTerm) ||
          scopes.some((scope) =>
            scopeHasSearchMatch(scope, searchTerm, matchesNodeSubtree)
          )
      ),
    [graph, searchTerm, matchesNodeSubtree]
  );

  return (
    <TabsContent value={managerName} className='space-y-4'>
      <div className='flex items-center gap-2'>
        <Input
          value={searchValue}
          onChange={(event) => setSearchValue(event.target.value)}
          placeholder='Search package ID or PURL...'
        />
        {searchValue && (
          <Button
            type='button'
            variant='secondary'
            size='icon'
            className='shrink-0'
            onClick={() => setSearchValue('')}
            aria-label='Clear search'
          >
            <X className='size-4' />
          </Button>
        )}
      </div>

      {searchTerm && !hasMatches && (
        <div className='text-muted-foreground text-sm'>
          No packages match your search. Showing the full graph.
        </div>
      )}

      <DependencyTree
        adjacency={adjacency}
        graph={graph}
        matchesNodeSubtree={matchesNodeSubtree}
        packageIdType={packageIdType}
        searchTerm={searchTerm}
      />
    </TabsContent>
  );
};
