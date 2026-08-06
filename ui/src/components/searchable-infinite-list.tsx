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

import { Fragment, ReactNode, useEffect, type Key } from 'react';

import {
  Command,
  CommandGroup,
  CommandInput,
  CommandList,
} from '@/components/ui/command';
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover';
import { useInView } from '@/hooks/use-in-view';
import { InfiniteList } from '@/lib/infinite-list';
import { cn } from '@/lib/utils';

type SearchableInfiniteListProps<TItem> = {
  /** What opens the list, for example a chevron or a combobox button. */
  trigger: ReactNode;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  list: InfiniteList<TItem>;
  /** Render one entry of the list, usually as a `CommandItem`. */
  renderItem: (item: TItem) => ReactNode;
  getItemKey: (item: TItem) => Key;
  /** Shown when the list has loaded without returning anything. */
  emptyMessage: string;
  /** Shown in front of the reason when the list fails to load. */
  errorMessage: string;
  /**
   * Whether the list has a search box. Turn this off for endpoints that cannot be filtered, such
   * as the runs of a repository.
   */
  searchable?: boolean;
  searchPlaceholder?: string;
  searchTerm?: string;
  onSearchTermChange?: (value: string) => void;
  align?: 'start' | 'center' | 'end';
  className?: string;
};

/**
 * A dropdown that loads its entries from a paged endpoint of the API, a page at a time, and lets
 * the user search them.
 *
 * The list is searched on the server, so the client-side filtering of `Command` is turned off, and
 * the search term itself is owned by the caller, which is what decides how it is debounced and how
 * it reaches the query. The next page is requested once the end of the list is scrolled into view.
 */
export function SearchableInfiniteList<TItem>({
  trigger,
  open,
  onOpenChange,
  list,
  renderItem,
  getItemKey,
  emptyMessage,
  errorMessage,
  searchable = true,
  searchPlaceholder = 'Search...',
  searchTerm = '',
  onSearchTermChange,
  align = 'start',
  className,
}: SearchableInfiniteListProps<TItem>) {
  const { ref, inView } = useInView();
  const { items, isPending, isError, error } = list;
  const { hasNextPage, isFetchingNextPage, fetchNextPage } = list;

  useEffect(() => {
    if (inView && hasNextPage && !isFetchingNextPage) {
      fetchNextPage();
    }
  }, [inView, hasNextPage, isFetchingNextPage, fetchNextPage]);

  return (
    <Popover open={open} onOpenChange={onOpenChange}>
      <PopoverTrigger asChild>{trigger}</PopoverTrigger>
      <PopoverContent
        align={align}
        className={cn(
          'w-auto max-w-[min(32rem,calc(100vw-2rem))] min-w-[var(--radix-popover-trigger-width)] p-0',
          className
        )}
      >
        <Command shouldFilter={false}>
          {searchable && (
            <CommandInput
              placeholder={searchPlaceholder}
              value={searchTerm}
              onValueChange={onSearchTermChange}
            />
          )}
          <CommandList>
            {isPending && (
              <div className='text-muted-foreground py-6 text-center text-sm'>
                Loading...
              </div>
            )}
            {isError && (
              <div className='text-destructive py-6 text-center text-sm'>
                {`${errorMessage}: ${error?.message}`}
              </div>
            )}
            {!isPending && !isError && items.length === 0 && (
              <div className='text-muted-foreground py-6 text-center text-sm'>
                {emptyMessage}
              </div>
            )}
            {items.length > 0 && (
              <CommandGroup>
                {items.map((item) => (
                  <Fragment key={getItemKey(item)}>{renderItem(item)}</Fragment>
                ))}
              </CommandGroup>
            )}
            {/* Scrolling this into view is what asks for the next page. */}
            <div ref={ref} />
            {isFetchingNextPage && (
              <div className='text-muted-foreground py-2 text-center text-sm'>
                Loading more...
              </div>
            )}
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}
