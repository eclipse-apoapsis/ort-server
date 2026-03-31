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

import {
  AppliedIssueResolution,
  AppliedRuleViolationResolution,
  AppliedVulnerabilityResolution,
  IssueResolution,
  IssueResolutionReason,
  RuleViolationResolution,
  RuleViolationResolutionReason,
  VulnerabilityResolution,
  VulnerabilityResolutionReason,
} from '@/api/types.gen';
import {
  getAppliedIssueResolutions,
  getAppliedRuleViolationResolutions,
  getAppliedVulnerabilityResolutions,
  getUnappliedIssueResolutions,
  getUnappliedRuleViolationResolutions,
  getUnappliedVulnerabilityResolutions,
  isIssueItem,
  isRuleViolationItem,
  isVulnerabilityItem,
  ItemWithResolutions,
} from '@/helpers/resolutions';

export type ResolutionFormValues = {
  comment: string;
  reason:
    | IssueResolutionReason
    | RuleViolationResolutionReason
    | VulnerabilityResolutionReason;
};

export type ManagedResolutionContext = {
  itemType: 'issue' | 'rule-violation' | 'vulnerability';
  identifier: string;
  repositoryId: string;
  runId: number;
};

type ResolutionItem =
  | NonNullable<ItemWithResolutions['resolutions']>[number]
  | IssueResolution
  | RuleViolationResolution
  | VulnerabilityResolution;

export type ResolutionDisplayItem = {
  key: string;
  resolution: ResolutionItem;
  state: 'applied' | 'pending-create' | 'pending-update' | 'pending-delete';
  showActions: boolean;
};

export function getManagedResolutionContext(
  context: ManagedResolutionContext,
  displayItem: ResolutionDisplayItem
): ManagedResolutionContext | undefined {
  if (
    (context.itemType === 'issue' || context.itemType === 'rule-violation') &&
    'messageHash' in displayItem.resolution &&
    displayItem.resolution.source === 'SERVER' &&
    displayItem.resolution.messageHash
  ) {
    return {
      ...context,
      identifier: displayItem.resolution.messageHash,
    };
  }

  return context.itemType === 'vulnerability' ? context : undefined;
}

function getResolutionIdentityKey(
  resolution:
    | Pick<AppliedIssueResolution, 'message' | 'messageHash' | 'source'>
    | Pick<AppliedRuleViolationResolution, 'message' | 'messageHash' | 'source'>
    | Pick<AppliedVulnerabilityResolution, 'externalId' | 'source'>
    | Pick<IssueResolution, 'message' | 'messageHash' | 'source'>
    | Pick<RuleViolationResolution, 'message' | 'messageHash' | 'source'>
    | Pick<VulnerabilityResolution, 'externalId' | 'source'>
) {
  if ('message' in resolution) {
    return resolution.source === 'SERVER' && resolution.messageHash
      ? `${resolution.source}:${resolution.messageHash}`
      : `${resolution.source}:${resolution.message}`;
  }

  return `${resolution.source}:${resolution.externalId}`;
}

function getManagedDisplayItems(
  appliedResolutions:
    | AppliedIssueResolution[]
    | AppliedRuleViolationResolution[]
    | AppliedVulnerabilityResolution[],
  unappliedResolutions:
    | IssueResolution[]
    | RuleViolationResolution[]
    | VulnerabilityResolution[],
  canManage: boolean
): ResolutionDisplayItem[] {
  const unappliedBySource = new Map(
    unappliedResolutions.map((resolution) => [
      getResolutionIdentityKey(resolution),
      resolution,
    ])
  );

  const displayItems: ResolutionDisplayItem[] = [];

  for (const resolution of appliedResolutions) {
    const source = getResolutionIdentityKey(resolution);
    const unappliedResolution = unappliedBySource.get(source);

    displayItems.push({
      key: `${source}:applied`,
      resolution,
      state:
        resolution.isDeleted && !unappliedResolution
          ? 'pending-delete'
          : 'applied',
      showActions: !resolution.isDeleted && !unappliedResolution && canManage,
    });

    if (unappliedResolution) {
      displayItems.push({
        key: `${source}:pending-update`,
        resolution: unappliedResolution,
        state: 'pending-update',
        showActions: canManage,
      });
    }
  }

  for (const resolution of unappliedResolutions) {
    if (
      !appliedResolutions.some(
        (applied) =>
          getResolutionIdentityKey(applied) ===
          getResolutionIdentityKey(resolution)
      )
    ) {
      displayItems.push({
        key: `${getResolutionIdentityKey(resolution)}:pending-create`,
        resolution,
        state: 'pending-create',
        showActions: canManage,
      });
    }
  }

  return displayItems;
}

export function getDisplayItems(
  item: ItemWithResolutions,
  canManage: boolean
): ResolutionDisplayItem[] {
  if (isVulnerabilityItem(item)) {
    return getManagedDisplayItems(
      getAppliedVulnerabilityResolutions(item),
      getUnappliedVulnerabilityResolutions(item),
      canManage
    );
  }

  if (isIssueItem(item)) {
    return getManagedDisplayItems(
      getAppliedIssueResolutions(item),
      getUnappliedIssueResolutions(item),
      canManage
    );
  }

  if (isRuleViolationItem(item)) {
    return getManagedDisplayItems(
      getAppliedRuleViolationResolutions(item),
      getUnappliedRuleViolationResolutions(item),
      canManage
    );
  }

  return [];
}

// Unit tests.

if (import.meta.vitest) {
  const { describe, expect, it } = import.meta.vitest;

  describe('getDisplayItems', () => {
    it('match applied and unapplied server issue resolutions by message hash', () => {
      const unappliedResolution = {
        message: 'Very long issue message copied from the server',
        messageHash: 'sha1-message-hash',
        reason: 'BUILD_TOOL_ISSUE',
        comment: 'Updated comment',
        source: 'SERVER',
      };
      const appliedResolution = {
        ...unappliedResolution,
        message: 'Older persisted server message representation',
        isDeleted: false,
      };

      const item = {
        source: 'Analyzer',
        message: unappliedResolution.message,
        resolutions: [appliedResolution],
        unappliedResolutions: [unappliedResolution],
      } as unknown as ItemWithResolutions;

      expect(getDisplayItems(item, true)).toStrictEqual([
        {
          key: 'SERVER:sha1-message-hash:applied',
          resolution: appliedResolution,
          state: 'applied',
          showActions: false,
        },
        {
          key: 'SERVER:sha1-message-hash:pending-update',
          resolution: unappliedResolution,
          state: 'pending-update',
          showActions: true,
        },
      ]);
    });
  });
}
