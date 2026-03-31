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

import { useEffect, useRef, useState } from 'react';

import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';

type TooltipIfTruncatedProps = {
  text: string;
};

/**
 * Renders text truncated with an ellipsis, and shows a tooltip with the full
 * text only when the text is actually overflowing its container.
 */
export const TooltipIfTruncated = ({ text }: TooltipIfTruncatedProps) => {
  const ref = useRef<HTMLDivElement>(null);
  const [isTruncated, setIsTruncated] = useState(false);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;

    const check = () => setIsTruncated(el.scrollWidth > el.clientWidth);
    check();

    const observer = new ResizeObserver(check);
    observer.observe(el);
    return () => observer.disconnect();
  }, [text]);

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <div
          ref={ref}
          className='overflow-hidden text-ellipsis whitespace-nowrap'
        >
          {text}
        </div>
      </TooltipTrigger>
      {isTruncated && <TooltipContent>{text}</TooltipContent>}
    </Tooltip>
  );
};
