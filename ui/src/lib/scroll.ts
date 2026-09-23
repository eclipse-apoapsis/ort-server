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

/**
 * Router options for a navigation that must leave the viewer where they are.
 *
 * The router scrolls back to the top of the page on every navigation, which is
 * right for moving through a top-level table but wrong inside an expanded panel:
 * expanding a row, or paging a nested table, would throw the viewer away from
 * the panel they are reading.
 */
export const KEEP_SCROLL_POSITION = { resetScroll: false } as const;

/** Identify a panel by the row path that opens it, without concatenating raw IDs. */
export const panelKey = (...parts: string[]) => JSON.stringify(parts);

/**
 * Panels the viewer just opened, waiting to be scrolled to once they render.
 * A panel restored from the URL on load is never in here, so reopening a shared
 * link leaves the viewer where they are instead of jumping.
 */
const pendingPanels = new Set<string>();

/** Record that the viewer opened this panel, for the panel itself to consume. */
export const markPanelOpened = (key: string) => {
  pendingPanels.add(key);
};

/**
 * Move a just-opened panel's trigger row to the top of the visible viewport,
 * once.
 *
 * This runs when the panel has rendered rather than when it was clicked: until
 * its content exists the page is still short, so there is nothing to scroll and
 * the browser would simply ignore the request.
 */
export const scrollOpenedPanelIntoView = (
  key: string,
  panel: HTMLElement | null
) => {
  if (!panel || !pendingPanels.delete(key)) return;

  // The panel occupies the row directly below the row whose button opened it.
  const triggerRow = panel.closest('tr')?.previousElementSibling;
  if (!(triggerRow instanceof HTMLElement)) return;

  // Keep the row below the sticky application header (`h-16`).
  triggerRow.style.scrollMarginTop = '4rem';
  triggerRow.scrollIntoView({
    block: 'start',
    behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches
      ? 'auto'
      : 'smooth',
  });
};
