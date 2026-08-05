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

import { useCallback, useEffect, useState } from 'react';

type UseInViewOptions = {
  /** Margin around the scroll container, in CSS units, as for `IntersectionObserver`. */
  rootMargin?: string;
  /** How much of the element has to be visible before it counts as being in view. */
  threshold?: number;
};

/**
 * Track whether an element is visible in the scroll container it lives in.
 *
 * Attach the returned `ref` to the element to watch. Because it is a callback ref, the element is
 * observed as soon as it is mounted, and observing switches over if the ref moves to another
 * element. The observer is disconnected when the element goes away or the component unmounts.
 */
export function useInView<T extends Element = HTMLDivElement>({
  rootMargin,
  threshold,
}: UseInViewOptions = {}) {
  const [element, setElement] = useState<T | null>(null);
  const [inView, setInView] = useState(false);

  useEffect(() => {
    if (!element) return;

    const observer = new IntersectionObserver(
      (entries) => setInView(entries.some((entry) => entry.isIntersecting)),
      { rootMargin, threshold }
    );
    observer.observe(element);

    return () => observer.disconnect();
  }, [element, rootMargin, threshold]);

  const ref = useCallback((element: T | null) => {
    setElement(element);
    // The element that was in view is gone, so nothing is in view anymore.
    if (!element) setInView(false);
  }, []);

  return { ref, inView };
}
