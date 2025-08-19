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

let originalFavicon: { href: string; type?: string } | null = null;

/**
 * Dynamically updates the favicon.
 *
 * - Call with (url, type?) to set a new favicon.
 * - Call with no arguments to restore the original favicon from index.html.
 *
 * @param url The URL or path to the new favicon. Can be local, remote, or data URI.
 * @param type Optional MIME type (inferred from file extension if omitted).
 */
export function setCustomFavicon(url?: string, type?: string): void {
  // On first run, remember the original favicon
  if (!originalFavicon) {
    const existing =
      document.querySelector<HTMLLinkElement>("link[rel~='icon']");
    if (existing) {
      originalFavicon = {
        href: existing.href,
        type: existing.type || undefined,
      };
    } else {
      // If no favicon found, set a sensible fallback
      originalFavicon = { href: '/favicon.svg', type: 'image/svg+xml' };
    }
  }

  // Restore the original if no arguments are given
  if (!url) {
    updateCustomFavicon(originalFavicon.href, originalFavicon.type);
    return;
  }

  // Infer MIME type if not provided
  if (!type) {
    if (url.startsWith('data:')) {
      type = '';
    } else if (url.match(/\.svg($|\?)/)) {
      type = 'image/svg+xml';
    } else if (url.match(/\.png($|\?)/)) {
      type = 'image/png';
    } else if (url.match(/\.ico($|\?)/)) {
      type = 'image/x-icon';
    } else if (url.match(/\.(jpg|jpeg)($|\?)/)) {
      type = 'image/jpeg';
    } else {
      type = 'image/png'; // fallback
    }
  }

  updateCustomFavicon(url, type);
}

function updateCustomFavicon(url: string, type?: string) {
  let link: HTMLLinkElement | null =
    document.querySelector("link[rel~='icon']");

  if (!link) {
    link = document.createElement('link');
    link.rel = 'icon';
    document.head.appendChild(link);
  }

  link.type = type || '';
  link.href = url;
}
