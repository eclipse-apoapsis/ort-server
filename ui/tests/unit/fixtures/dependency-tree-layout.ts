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

import { act } from '@testing-library/react';
import { afterEach, beforeEach } from 'vitest';

/**
 * Layout for testing the virtualized dependency tree in jsdom, which does not
 * lay out elements: every row is `ROW_HEIGHT` high unless `resizeRow` changes
 * it, and the window shows `VIEWPORT_HEIGHT` pixels.
 */
export const ROW_HEIGHT = 32;
export const VIEWPORT_HEIGHT = 640;

/** The most rows mounted at once: the visible ones and 10 on either side. */
export const MAX_MOUNTED_ROWS = VIEWPORT_HEIGHT / ROW_HEIGHT + 2 * 10 + 1;

/** Row heights that differ from `ROW_HEIGHT`, by the row's text. */
const rowHeights = new Map<string, number>();

/** The resize observers created by the virtualizer, to report size changes. */
const resizeObservers = new Set<{
  callback: ResizeObserverCallback;
  elements: Set<Element>;
}>();

/** Installs the layout before and removes it after each test of the file. */
export const setUpDependencyTreeLayout = () => {
  const originalOffsetHeight = Object.getOwnPropertyDescriptor(
    HTMLElement.prototype,
    'offsetHeight'
  );
  const originalResizeObserver = globalThis.ResizeObserver;

  beforeEach(() => {
    rowHeights.clear();
    resizeObservers.clear();

    Object.defineProperty(window, 'innerHeight', {
      configurable: true,
      value: VIEWPORT_HEIGHT,
    });
    Object.defineProperty(window, 'scrollY', { configurable: true, value: 0 });
    Object.defineProperty(HTMLElement.prototype, 'offsetHeight', {
      configurable: true,
      get(this: HTMLElement) {
        if (!this.hasAttribute('data-dependency-tree-row')) return 0;

        const text = this.textContent;
        const custom = [...rowHeights].find(([key]) => text.includes(key));

        return custom?.[1] ?? ROW_HEIGHT;
      },
    });

    globalThis.ResizeObserver = class implements ResizeObserver {
      private readonly observed = {
        callback: (() => {}) as ResizeObserverCallback,
        elements: new Set<Element>(),
      };

      constructor(callback: ResizeObserverCallback) {
        this.observed.callback = callback;
        resizeObservers.add(this.observed);
      }

      disconnect() {
        resizeObservers.delete(this.observed);
      }

      observe(element: Element) {
        this.observed.elements.add(element);
      }

      unobserve(element: Element) {
        this.observed.elements.delete(element);
      }
    };
  });

  afterEach(() => {
    globalThis.ResizeObserver = originalResizeObserver;

    if (originalOffsetHeight) {
      Object.defineProperty(
        HTMLElement.prototype,
        'offsetHeight',
        originalOffsetHeight
      );
    }
  });
};

export const mountedRows = () =>
  Array.from(
    document.querySelectorAll<HTMLElement>('[data-dependency-tree-row]')
  );

export const mountedIndexes = () =>
  mountedRows().map((row) => Number(row.getAttribute('data-index')));

const mountedRow = (text: string) =>
  mountedRows().find((element) => element.textContent.includes(text));

/** Returns the `transform` that positions the mounted row containing `text`. */
export const rowTop = (text: string) => mountedRow(text)?.style.transform;

/** Scrolls the window to `scrollY`. */
export const setScrollY = (scrollY: number) => {
  act(() => {
    Object.defineProperty(window, 'scrollY', {
      configurable: true,
      value: scrollY,
    });
    window.dispatchEvent(new Event('scroll'));
  });
};

/** Reports a changed height of the mounted row containing `text`. */
export const resizeRow = (text: string, height: number) => {
  rowHeights.set(text, height);
  const row = mountedRow(text);

  act(() => {
    resizeObservers.forEach(({ callback, elements }) => {
      if (row && elements.has(row)) {
        // Without a border box size, the virtualizer reads `offsetHeight`.
        callback(
          [{ target: row } as unknown as ResizeObserverEntry],
          {} as ResizeObserver
        );
      }
    });
  });
};
