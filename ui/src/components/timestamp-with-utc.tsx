/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { formatTimestamp } from '@/lib/utils.ts';

type TimestampWithUTCProps = {
  timestamp: string;
  timeZone?: string | undefined;
  locales?: Intl.LocalesArgument | undefined;
  className?: string;
};

export const TimestampWithUTC = ({
  timestamp,
  timeZone,
  locales,
  className,
}: TimestampWithUTCProps) => {
  return (
    <Tooltip>
      <TooltipTrigger className={className}>
        {formatTimestamp(timestamp, timeZone, locales)}
      </TooltipTrigger>
      <TooltipContent>{new Date(timestamp).toUTCString()}</TooltipContent>
    </Tooltip>
  );
};
