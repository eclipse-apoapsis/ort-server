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

import { describe, expect, it } from 'vitest';

import {
  getContrastRatio,
  getLicenseBadgeColors,
  getLicenseHue,
  parseLicenseToken,
} from '@/helpers/licenses/license-colors';

type RgbColor = {
  red: number;
  green: number;
  blue: number;
};

const parseRgbCss = (value: string): RgbColor => {
  const match = value.match(/^rgb\((\d+), (\d+), (\d+)\)$/);

  if (!match) {
    throw new Error(`Unexpected rgb color value: ${value}`);
  }

  return {
    red: Number.parseInt(match[1] ?? '', 10),
    green: Number.parseInt(match[2] ?? '', 10),
    blue: Number.parseInt(match[3] ?? '', 10),
  };
};

describe('getLicenseBadgeColors', () => {
  it('keeps badge contrast at or above normal-text WCAG AA against the chosen text color', () => {
    const samples = [
      'Apache-2.0',
      'Apache-1.0',
      'LGPL-2.1-only',
      'GPL-2.0-or-later',
      'MIT',
      'BSD-3-Clause',
    ];

    samples.forEach((license) => {
      const colors = getLicenseBadgeColors(license);
      const backgroundRgb = parseRgbCss(colors.backgroundColor);
      const foregroundRgb =
        colors.color === 'black'
          ? { red: 0, green: 0, blue: 0 }
          : { red: 255, green: 255, blue: 255 };

      expect(
        getContrastRatio(backgroundRgb, foregroundRgb)
      ).toBeGreaterThanOrEqual(4.5);
    });
  });

  it('keeps related Apache licenses visually close', () => {
    expect(getLicenseBadgeColors('Apache-1.0').backgroundColor).not.toEqual(
      getLicenseBadgeColors('Apache-2.0').backgroundColor
    );
    expect(getLicenseBadgeColors('Apache-1.0').color).toBe('black');
    expect(getLicenseBadgeColors('Apache-2.0').color).toBe('black');
  });

  it('keeps related LGPL licenses visually close', () => {
    expect(getLicenseBadgeColors('LGPL-2.0').backgroundColor).not.toEqual(
      getLicenseBadgeColors('LGPL-2.1-only').backgroundColor
    );
    expect(getLicenseBadgeColors('LGPL-2.0').color).toBe('black');
    expect(getLicenseBadgeColors('LGPL-2.1-only').color).toBe('black');
  });

  it('keeps related license families closer than unrelated ones in hue space', () => {
    const apacheOneHue = getLicenseHue(parseLicenseToken('Apache-1.0'));
    const apacheTwoHue = getLicenseHue(parseLicenseToken('Apache-2.0'));
    const mitHue = getLicenseHue(parseLicenseToken('MIT'));
    const apacheDistance = Math.abs(apacheOneHue - apacheTwoHue);
    const unrelatedDistance = Math.abs(apacheOneHue - mitHue);

    expect(apacheDistance).toBeLessThan(unrelatedDistance);
  });

  it('normalizes loosely formatted input before generating colors', () => {
    expect(getLicenseBadgeColors('Apache 2')).toEqual(
      getLicenseBadgeColors('Apache-2.0')
    );
  });

  it('returns CSS rgb colors for background and border', () => {
    const result = getLicenseBadgeColors('BSD-3-Clause');

    expect(result.backgroundColor).toMatch(/^rgb\(\d+, \d+, \d+\)$/);
    expect(result.borderColor).toMatch(/^rgb\(\d+, \d+, \d+\)$/);
    expect(['black', 'white']).toContain(result.color);
  });

  it('returns deterministic colors for the same license', () => {
    expect(getLicenseBadgeColors('MIT')).toEqual(getLicenseBadgeColors('MIT'));
  });
});
