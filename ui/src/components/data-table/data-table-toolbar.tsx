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

import { Cross2Icon } from '@radix-ui/react-icons';

import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

interface DataTableToolbarProps {
  filters: React.ReactNode;
  resetFilters: () => void;
  resetBtnVisible?: boolean;
  className?: string;
}

export const DataTableToolbar = ({
  filters,
  resetFilters,
  className,
  resetBtnVisible = false,
}: DataTableToolbarProps) => {
  return (
    <div className={cn('flex items-center gap-2', className)}>
      {filters}
      {resetBtnVisible && (
        <Button
          variant='ghost'
          onClick={() => resetFilters()}
          className='h-8 px-2 lg:px-3'
        >
          Reset
          <Cross2Icon className='ml-2 h-4 w-4' />
        </Button>
      )}
    </div>
  );
};
