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

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';

type StatisticsCardProps = {
  title: string;
  icon?: React.ComponentType<{ className?: string }>;
  value?: React.ReactNode;
  description?: string;
  className?: string;
};

export const StatisticsCard = ({
  title,
  icon: Icon,
  value,
  description,
  className,
}: StatisticsCardProps) => {
  return (
    <Card className={className}>
      <CardHeader>
        <CardTitle>
          <div className='flex items-center justify-between'>
            <span className='text-sm font-semibold'>{title}</span>
            {Icon && <Icon />}
          </div>
        </CardTitle>
      </CardHeader>
      <CardContent className='text-sm'>
        <div className='flex flex-col'>
          <div className='text-2xl font-bold'>
            {value !== undefined ? value : 'Failed'}
          </div>
          <div className='text-xs'>{description}</div>
        </div>
      </CardContent>
    </Card>
  );
};
