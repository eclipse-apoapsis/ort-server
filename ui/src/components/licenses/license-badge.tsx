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

import * as React from 'react';

import { Badge } from '@/components/ui/badge';
import { getLicenseBadgeColors } from '@/helpers/licenses/license-colors';
import { cn } from '@/lib/utils';

type LicenseBadgeProps = Omit<
  React.ComponentProps<typeof Badge>,
  'children'
> & {
  license: string;
};

export function LicenseBadge({
  license,
  className,
  title,
  style,
  ...props
}: LicenseBadgeProps) {
  const colors = getLicenseBadgeColors(license);

  return (
    <Badge
      variant='small'
      className={cn(
        'max-w-full align-middle text-[0.7rem] leading-4 font-medium',
        className
      )}
      title={title ?? license}
      style={{
        backgroundColor: colors.backgroundColor,
        borderColor: colors.borderColor,
        color: colors.color,
        ...style,
      }}
      {...props}
    >
      {license}
    </Badge>
  );
}
