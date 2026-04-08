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

import { normalizeLicenseExpression } from '@/helpers/licenses/spdx-expression';

type RgbColor = {
  red: number;
  green: number;
  blue: number;
};

export type LicenseBadgeColors = {
  backgroundColor: string;
  borderColor: string;
  color: 'black' | 'white';
};

type ParsedLicenseToken = {
  normalizedToken: string;
  family: string;
  majorVersion?: number;
  minorVersion?: number;
  qualifier: string;
};

/**
 * Generate a stable 32-bit hash for a string.
 *
 * This is the primitive used to make all color decisions deterministic. If the
 * project ever wants a different overall distribution of colors, this is one of
 * the few places worth reconsidering.
 */
function hashString(value: string): number {
  let hash = 2166136261;

  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }

  return hash >>> 0;
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}

function getSignedOffset(seed: string, magnitude: number): number {
  const hash = hashString(seed);
  const direction = hash % 2 === 0 ? -1 : 1;
  const offset = hash % (magnitude + 1);

  return direction * offset;
}

/**
 * Split a license-like token into the pieces that drive color selection.
 *
 * Tuning notes:
 * - `family` is the dominant input for hue, so license families such as Apache
 *   and LGPL remain visually grouped.
 * - `majorVersion` and `minorVersion` are used only as small hue offsets so
 *   close versions stay close in color.
 * - `qualifier` captures suffixes such as `only`, `or-later`, and similar
 *   trailing markers. These intentionally affect the final color only slightly.
 * - `WITH` exceptions are ignored for family extraction because they should not
 *   move the base license family into a completely different color region.
 */
function parseLicenseToken(license: string): ParsedLicenseToken {
  const normalizedToken = normalizeLicenseExpression(license.trim());
  const tokenWithoutException =
    normalizedToken.split(/\s+WITH\s+/i)[0] || normalizedToken;
  const familyMatch = tokenWithoutException.match(
    /^(.*?)(?:-(\d+(?:\.\d+)*).*)?$/
  );
  const family = familyMatch?.[1] || tokenWithoutException;
  const versionString = familyMatch?.[2];
  const [majorVersion, minorVersion] = versionString
    ? versionString.split('.').map((part) => Number.parseInt(part, 10))
    : [undefined, undefined];
  const qualifierStartIndex = versionString
    ? tokenWithoutException.indexOf(versionString) + versionString.length
    : family.length;
  const qualifier = tokenWithoutException
    .slice(qualifierStartIndex)
    .replace(/^[-\s:]+/, '')
    .trim();

  return {
    normalizedToken,
    family: family || normalizedToken,
    majorVersion,
    minorVersion,
    qualifier,
  };
}

/**
 * Map a parsed license token into a hue angle in the HSL color space.
 *
 * Tuning notes:
 * - `familyHue` defines the broad color family. Changing the modulus or moving
 *   away from HSL hue-based grouping would have the largest visual impact.
 * - `majorVersion * 9` and `minorVersion * 3` are intentionally small offsets.
 *   Increase them to make versions more visually distinct; decrease them to
 *   keep version families tighter.
 * - `qualifierOffset` is deliberately tiny so `-only` and `-or-later` remain
 *   close to the same base license.
 */
function getLicenseHue(token: ParsedLicenseToken): number {
  const familyHue = hashString(token.family) % 360;
  const majorOffset =
    token.majorVersion !== undefined ? token.majorVersion * 9 : 0;
  const minorOffset =
    token.minorVersion !== undefined ? token.minorVersion * 3 : 0;
  const qualifierOffset = token.qualifier
    ? getSignedOffset(`${token.family}:${token.qualifier}`, 4)
    : 0;

  return (familyHue + majorOffset + minorOffset + qualifierOffset + 360) % 360;
}

/**
 * Choose badge saturation for a license family.
 *
 * This is kept in a narrow range to avoid loud / fully saturated colors in the
 * UI. Broaden the range if the badges should become more vivid.
 */
function getLicenseSaturation(token: ParsedLicenseToken): number {
  return 58 + (hashString(`${token.family}:s`) % 8);
}

/**
 * Choose badge lightness for a license family.
 *
 * The current range intentionally favors light backgrounds so black text is
 * usually sufficient. If the design moves toward darker badges, this is the
 * main function to revisit together with the text-color contrast policy.
 */
function getLicenseLightness(token: ParsedLicenseToken): number {
  const qualifierOffset = token.qualifier
    ? getSignedOffset(`${token.normalizedToken}:l`, 2)
    : 0;

  return clamp(85 + qualifierOffset, 82, 88);
}

/**
 * Convert an HSL color into RGB for CSS output and contrast calculations.
 */
function hslToRgb(
  hue: number,
  saturation: number,
  lightness: number
): RgbColor {
  const normalizedHue = hue / 360;
  const normalizedSaturation = saturation / 100;
  const normalizedLightness = lightness / 100;

  if (normalizedSaturation === 0) {
    const value = Math.round(normalizedLightness * 255);

    return { red: value, green: value, blue: value };
  }

  const hueToRgb = (p: number, q: number, t: number) => {
    let normalizedT = t;

    if (normalizedT < 0) normalizedT += 1;
    if (normalizedT > 1) normalizedT -= 1;
    if (normalizedT < 1 / 6) return p + (q - p) * 6 * normalizedT;
    if (normalizedT < 1 / 2) return q;
    if (normalizedT < 2 / 3) return p + (q - p) * (2 / 3 - normalizedT) * 6;

    return p;
  };

  const q =
    normalizedLightness < 0.5
      ? normalizedLightness * (1 + normalizedSaturation)
      : normalizedLightness +
        normalizedSaturation -
        normalizedLightness * normalizedSaturation;
  const p = 2 * normalizedLightness - q;

  return {
    red: Math.round(hueToRgb(p, q, normalizedHue + 1 / 3) * 255),
    green: Math.round(hueToRgb(p, q, normalizedHue) * 255),
    blue: Math.round(hueToRgb(p, q, normalizedHue - 1 / 3) * 255),
  };
}

/**
 * Calculate the WCAG relative luminance of an RGB color.
 */
function getRelativeLuminance({ red, green, blue }: RgbColor): number {
  const normalizeChannel = (channel: number) => {
    const normalized = channel / 255;

    return normalized <= 0.03928
      ? normalized / 12.92
      : ((normalized + 0.055) / 1.055) ** 2.4;
  };

  const linearRed = normalizeChannel(red);
  const linearGreen = normalizeChannel(green);
  const linearBlue = normalizeChannel(blue);

  return 0.2126 * linearRed + 0.7152 * linearGreen + 0.0722 * linearBlue;
}

/**
 * Calculate the contrast ratio between two RGB colors.
 *
 * The result is used to choose black vs. white text for the final badge.
 *
 * Reference: https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html
 * WCAG AA reuires text/background contrast of at least 4.5:1 for normal text, and
 * the contrast calculation is based on relative luminance with the standard ratio formula:
 *   (lighter + 0.05) / (darker + 0.05)
 */
function getContrastRatio(first: RgbColor, second: RgbColor): number {
  const firstLuminance = getRelativeLuminance(first);
  const secondLuminance = getRelativeLuminance(second);
  const lighter = Math.max(firstLuminance, secondLuminance);
  const darker = Math.min(firstLuminance, secondLuminance);

  return (lighter + 0.05) / (darker + 0.05);
}

function toRgbCss(color: RgbColor): string {
  return `rgb(${color.red}, ${color.green}, ${color.blue})`;
}

/**
 * Create the final badge colors for a license string.
 *
 * Algorithm summary:
 * - Normalize the input so equivalent license spellings map to the same color.
 * - Derive a base hue from the license family.
 * - Apply small hue shifts from version and qualifier information.
 * - Keep saturation and lightness in restrained ranges for readable badges.
 * - Create a slightly darker border from the same base color.
 * - Choose black or white text based on whichever yields the stronger
 *   contrast ratio.
 *
 * Primary tuning knobs:
 * - `getLicenseHue()` for family grouping and version spacing
 * - `getLicenseSaturation()` for vividness
 * - `getLicenseLightness()` for how light or dark the badges feel
 * - the border deltas below (`- 10`) for edge definition
 */
export function getLicenseBadgeColors(license: string): LicenseBadgeColors {
  const token = parseLicenseToken(license);
  const backgroundRgb = hslToRgb(
    getLicenseHue(token),
    getLicenseSaturation(token),
    getLicenseLightness(token)
  );
  const borderRgb = hslToRgb(
    getLicenseHue(token),
    clamp(getLicenseSaturation(token) - 10, 30, 100),
    clamp(getLicenseLightness(token) - 10, 30, 100)
  );
  const black = { red: 0, green: 0, blue: 0 };
  const white = { red: 255, green: 255, blue: 255 };
  const blackContrast = getContrastRatio(backgroundRgb, black);
  const whiteContrast = getContrastRatio(backgroundRgb, white);

  return {
    backgroundColor: toRgbCss(backgroundRgb),
    borderColor: toRgbCss(borderRgb),
    color: blackContrast >= whiteContrast ? 'black' : 'white',
  };
}

//
// Unit tests for license color helpers.
//

if (import.meta.vitest) {
  const { describe, it, expect } = import.meta.vitest;

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
    it('returns deterministic colors for the same license', () => {
      expect(getLicenseBadgeColors('MIT')).toEqual(
        getLicenseBadgeColors('MIT')
      );
    });

    it('normalizes loosely formatted input before generating colors', () => {
      expect(getLicenseBadgeColors('Apache 2')).toEqual(
        getLicenseBadgeColors('Apache-2.0')
      );
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

    it('returns CSS rgb colors for background and border', () => {
      const result = getLicenseBadgeColors('BSD-3-Clause');

      expect(result.backgroundColor).toMatch(/^rgb\(\d+, \d+, \d+\)$/);
      expect(result.borderColor).toMatch(/^rgb\(\d+, \d+, \d+\)$/);
      expect(['black', 'white']).toContain(result.color);
    });

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
  });
}
