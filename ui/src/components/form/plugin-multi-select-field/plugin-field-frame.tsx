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

import type { ReactNode } from 'react';

import {
  FormDescription,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Separator } from '@/components/ui/separator';
import { cn } from '@/lib/utils';

type PluginFieldFrameProps = {
  label?: string;
  description?: ReactNode;
  /** Controls shown below the description, such as the select-all checkbox. */
  headerActions?: ReactNode;
  className?: string;
  /** The plugin list. */
  children: ReactNode;
};

/** The bordered box around a plugin field, with its label, description and errors. */
export const PluginFieldFrame = ({
  label,
  description,
  headerActions,
  className,
  children,
}: PluginFieldFrameProps) => (
  <FormItem
    className={cn(
      'flex flex-col justify-between rounded-lg border p-4',
      className
    )}
  >
    <FormLabel>{label}</FormLabel>
    <FormDescription className='pb-4'>{description}</FormDescription>
    {headerActions}
    <Separator className='my-2' />
    {children}
    <FormMessage />
  </FormItem>
);
