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

import { CopyToClipboard } from '@/components/copy-to-clipboard';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { cn } from '@/lib/utils';

type Sha1ComponentProps = {
  sha1: string;
  // Turn the copy button off where the component sits inside a link, which a button must not.
  copyable?: boolean;
  // Turn the tooltip with the full SHA1 off where tooltips would crowd each other, like in a list.
  withTooltip?: boolean;
  className?: string;
};

export const Sha1Component = ({
  sha1,
  copyable = true,
  withTooltip = true,
  className,
}: Sha1ComponentProps) => {
  const shortSha1 = sha1.slice(0, 7);
  return (
    <span className={cn('inline-flex items-center font-mono', className)}>
      (
      {withTooltip ? (
        <Tooltip>
          <TooltipTrigger asChild>
            <span>{shortSha1}</span>
          </TooltipTrigger>
          <TooltipContent>
            <span className='font-mono'>SHA1: {sha1}</span>
          </TooltipContent>
        </Tooltip>
      ) : (
        <span>{shortSha1}</span>
      )}
      {copyable && (
        <CopyToClipboard
          copyText={sha1}
          className='h-5 pr-0 pl-1 align-middle'
        />
      )}
      )
    </span>
  );
};
