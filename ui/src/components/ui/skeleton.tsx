/*
 * The component is originally a part of the shadcn/ui library at https://github.com/shadcn-ui/ui.
 * The library is licensed as set out below:
 *
 * Copyright (c) 2023 shadcn
 *
 * SPDX-License-Identifier: MIT
 */

import { cn } from '@/lib/utils';

function Skeleton({
  className,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      data-slot='skeleton'
      className={cn('bg-primary/10 animate-pulse rounded-md', className)}
      {...props}
    />
  );
}

export { Skeleton };
