/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import { ErrorComponentProps } from '@tanstack/react-router';
import { TriangleAlert } from 'lucide-react';

import {
  Card,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { cn } from '@/lib/utils';

type Props = ErrorComponentProps & {
  title?: string;
  className?: string;
  showStackTrace?: boolean;
};

export const ErrorComponent = ({
  error,
  title = 'Oops, something went wrong...',
  className,
  showStackTrace = false,
}: Props) => {
  return (
    <Card className={cn('flex h-full', className)}>
      <CardHeader>
        <CardTitle className='flex gap-2 align-baseline'>
          <TriangleAlert className='text-traffic-light-red size-5' />
          <div>{title}</div>
        </CardTitle>
        <CardDescription>{`Error: ${error.message}`}</CardDescription>
        {showStackTrace && (
          <CardDescription className='text-xs'>
            {error.stack
              ?.split('\n')
              .map((line, index) => <div key={index}>{line}</div>)}
          </CardDescription>
        )}
      </CardHeader>
    </Card>
  );
};
