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

import { cn } from '@/lib/utils';

/**
 * Wraps a single tree item and draws the connector lines that link it to its
 * parent, rendering the hierarchy as proper "branch segments" / t-junctions
 * instead of plain vertical lines.
 *
 * Each item draws a short horizontal segment from the trunk to its own row and
 * a vertical trunk segment shared with its siblings. The last sibling only
 * draws the trunk down to the branch point (forming an "└" junction), while the
 * other siblings extend the trunk past their own box to bridge the gap to the
 * next sibling (forming "├" junctions).
 */
export const TreeBranch = ({
  isLast,
  children,
}: {
  isLast: boolean;
  children: React.ReactNode;
}) => (
  <div className='relative ml-2 pl-4'>
    <span
      aria-hidden
      className={cn(
        'bg-border absolute top-0 left-0 w-px',
        isLast ? 'h-[11px]' : '-bottom-2'
      )}
    />
    <span
      aria-hidden
      className='bg-border absolute top-[11px] left-0 h-px w-4'
    />
    {children}
  </div>
);
