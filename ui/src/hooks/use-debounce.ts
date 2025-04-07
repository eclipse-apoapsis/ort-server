/*
 * The component is originally a part of the shadcn/ui expansions library
 * at https://shadcnui-expansions.typeart.cc/docs/multiple-selector.
 * The library is licensed as set out below:
 *
 * Copyright (c) 2023 shadcn
 *
 * SPDX-License-Identifier: MIT
 */

import { useEffect, useState } from 'react';

/**
 * A barebones debounce hook that delays updating the value until after a specified delay.
 *
 * @param value The input value to be debounced.
 * @param delay The delay in milliseconds before the value is updated.
 *              If not provided, defaults to 500 milliseconds.
 * @returns The debounced value.
 */
export function useDebounce<T>(value: T, delay?: number): T {
  const [debouncedValue, setDebouncedValue] = useState<T>(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedValue(value), delay || 500);

    return () => {
      clearTimeout(timer);
    };
  }, [value, delay]);

  return debouncedValue;
}
