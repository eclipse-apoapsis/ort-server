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

import { useState } from 'react';

import {
  isIssueItem,
  isRuleViolationItem,
  isVulnerabilityItem,
  ItemWithResolutions,
} from '@/helpers/resolutions';
import { IssueResolutions } from './issue-resolutions';
import { RuleViolationResolutions } from './rule-violation-resolutions';
import { ResolutionCard } from './shared';
import { getDisplayItems } from './utils';
import { VulnerabilityResolutions } from './vulnerability-resolutions';

export type ResolutionsProps = {
  item: ItemWithResolutions;
  repositoryId?: string;
  runId?: number;
};

export function Resolutions({ item, repositoryId, runId }: ResolutionsProps) {
  const [editingKey, setEditingKey] = useState<string | null>(null);
  const displayItems = getDisplayItems(item, false);

  if (isVulnerabilityItem(item) && repositoryId && runId !== undefined) {
    return (
      <VulnerabilityResolutions
        item={item}
        repositoryId={repositoryId}
        runId={runId}
      />
    );
  }

  if (isIssueItem(item) && repositoryId && runId !== undefined) {
    return (
      <IssueResolutions item={item} repositoryId={repositoryId} runId={runId} />
    );
  }

  if (isRuleViolationItem(item) && repositoryId && runId !== undefined) {
    return (
      <RuleViolationResolutions
        item={item}
        repositoryId={repositoryId}
        runId={runId}
      />
    );
  }

  return (
    <div className='flex flex-col gap-2'>
      {displayItems.map((displayItem) => (
        <ResolutionCard
          key={displayItem.key}
          displayItem={displayItem}
          editingKey={editingKey}
          setEditingKey={setEditingKey}
        />
      ))}
    </div>
  );
}
