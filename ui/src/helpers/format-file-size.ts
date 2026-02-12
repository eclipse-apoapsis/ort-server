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
 * Format a file size in bytes to a human-readable string.
 * Uses 1024-based divisions with conventional SI labels (kB, MB, GB).
 * @param bytes - The file size in bytes
 * @returns A formatted string such as "512 B", "1.5 kB", or "2.3 MB"
 */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }

  const units = ['kB', 'MB', 'GB'];
  let value = bytes;

  for (const unit of units) {
    value /= 1024;
    if (value < 1024 || unit === 'GB') {
      return `${value.toFixed(1)} ${unit}`;
    }
  }

  // This is unreachable but satisfies the compiler.
  return `${value.toFixed(1)} GB`;
}

// Unit tests.

if (import.meta.vitest) {
  const { it, expect } = import.meta.vitest;

  it('formatFileSize - zero bytes', () => {
    expect(formatFileSize(0)).toBe('0 B');
  });

  it('formatFileSize - bytes below 1 kB', () => {
    expect(formatFileSize(512)).toBe('512 B');
  });

  it('formatFileSize - exactly 1 kB', () => {
    expect(formatFileSize(1024)).toBe('1.0 kB');
  });

  it('formatFileSize - kB range', () => {
    expect(formatFileSize(1536)).toBe('1.5 kB');
  });

  it('formatFileSize - MB range', () => {
    expect(formatFileSize(2.3 * 1024 * 1024)).toBe('2.3 MB');
  });

  it('formatFileSize - GB range', () => {
    expect(formatFileSize(1.1 * 1024 * 1024 * 1024)).toBe('1.1 GB');
  });

  it('formatFileSize - large GB value', () => {
    expect(formatFileSize(500 * 1024 * 1024 * 1024)).toBe('500.0 GB');
  });
}
