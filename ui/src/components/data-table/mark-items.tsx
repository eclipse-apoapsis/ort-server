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

import { Link, LinkOptions } from '@tanstack/react-router';
import { Row } from '@tanstack/react-table';
import { Link as Chain } from 'lucide-react';

import { Button } from '@/components/ui/button';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';

interface MarkItemsProps<TData> {
  row: Row<TData>;
  /**
   * A function to provide `LinkOptions` for a link to set marked item in the URL.
   */
  setMarked: (marked: string) => LinkOptions;
}

export function MarkItems<TData>({ row, setMarked }: MarkItemsProps<TData>) {
  // Copy the URL with marked item to clipboard.
  // Because TanStack Router updates the URL asynchronously,
  // a small delay is used before copying, to ensure the new
  // URL is copied properly instead of the old one.
  const urlToClipboard = async () => {
    setTimeout(async () => {
      const newUrl = window.location.href;
      await navigator.clipboard.writeText(newUrl);
    }, 100);
  };

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Link {...setMarked(row.id)} onClick={urlToClipboard}>
          <Button size='sm' variant='ghost'>
            <Chain className='h-3 w-3' />
          </Button>
        </Link>
      </TooltipTrigger>

      <TooltipContent>
        Copy the link to this item (ID: {row.id}) to clipboard.
      </TooltipContent>
    </Tooltip>
  );
}
