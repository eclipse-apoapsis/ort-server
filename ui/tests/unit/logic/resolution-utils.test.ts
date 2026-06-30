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

import { describe, expect, it } from 'vitest';

import type { AppliedIssueResolution, IssueResolution } from '@/api';
import { getDisplayItems } from '@/components/resolutions/utils';
import { createIssue } from '../fixtures/resolutions';

describe('getDisplayItems', () => {
  it('match applied and unapplied server issue resolutions by message hash', () => {
    const unappliedResolution: IssueResolution = {
      message: 'Very long issue message copied from the server',
      messageHash: 'sha1-message-hash',
      reason: 'BUILD_TOOL_ISSUE',
      comment: 'Updated comment',
      source: 'SERVER',
    };
    const appliedResolution: AppliedIssueResolution = {
      ...unappliedResolution,
      message: 'Older persisted server message representation',
      isDeleted: false,
    };

    const item = createIssue({
      message: unappliedResolution.message,
      resolutions: [appliedResolution],
      unappliedResolutions: [unappliedResolution],
    });

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
