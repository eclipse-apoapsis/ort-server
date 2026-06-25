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
  isAtomicLicenseExpression,
  normalizeLicenseExpression,
  parseLicenseExpression,
} from '@/helpers/licenses/spdx-expression';

describe('isAtomicLicenseExpression', () => {
  it('returns false for compound expressions', () => {
    expect(isAtomicLicenseExpression('Apache-2.0 OR MIT')).toBe(false);
  });

  it('returns false for invalid expressions', () => {
    expect(isAtomicLicenseExpression('MIT OR (')).toBe(false);
  });

  it('returns true for a single SPDX license', () => {
    expect(isAtomicLicenseExpression('Apache-2.0')).toBe(true);
  });

  it('returns true for normalized GPL plus syntax because it remains atomic', () => {
    expect(isAtomicLicenseExpression('GPL-2.0+')).toBe(true);
  });
});

describe('normalizeLicenseExpression', () => {
  it('normalizes deprecated GPL plus syntax', () => {
    expect(normalizeLicenseExpression('GPL-2.0+')).toBe('GPL-2.0-or-later');
  });

  it('normalizes valid SPDX expressions', () => {
    expect(normalizeLicenseExpression('  mit or apache-2.0  ')).toBe(
      'Apache-2.0 OR MIT'
    );
  });

  it('returns a trimmed original string for invalid expressions', () => {
    expect(normalizeLicenseExpression(' MIT OR ( ')).toBe('MIT OR (');
  });
});

describe('parseLicenseExpression', () => {
  it('accepts lower-case operators and normalizes them', () => {
    expect(parseLicenseExpression('mit or apache-2.0')).toEqual({
      kind: 'compound',
      rawExpression: 'mit or apache-2.0',
      normalizedExpression: 'Apache-2.0 OR MIT',
      node: {
        kind: 'conjunction',
        conjunction: 'or',
        left: {
          kind: 'license',
          license: 'MIT',
          exception: undefined,
        },
        right: {
          kind: 'license',
          license: 'Apache-2.0',
          exception: undefined,
        },
      },
    });
  });

  it('parses a single SPDX license identifier as an atomic expression', () => {
    expect(parseLicenseExpression('MIT')).toEqual({
      kind: 'atomic',
      rawExpression: 'MIT',
      normalizedExpression: 'MIT',
      node: {
        kind: 'license',
        license: 'MIT',
        exception: undefined,
      },
    });
  });

  it('parses nested SPDX expressions recursively', () => {
    expect(
      parseLicenseExpression('MIT AND (Apache-2.0 OR BSD-3-Clause)')
    ).toEqual({
      kind: 'compound',
      rawExpression: 'MIT AND (Apache-2.0 OR BSD-3-Clause)',
      normalizedExpression: 'MIT AND (Apache-2.0 OR BSD-3-Clause)',
      node: {
        kind: 'conjunction',
        conjunction: 'and',
        left: {
          kind: 'license',
          license: 'MIT',
          exception: undefined,
        },
        right: {
          kind: 'conjunction',
          conjunction: 'or',
          left: {
            kind: 'license',
            license: 'Apache-2.0',
            exception: undefined,
          },
          right: {
            kind: 'license',
            license: 'BSD-3-Clause',
            exception: undefined,
          },
        },
      },
    });
  });

  it('parses SPDX expressions with AND / OR into conjunction nodes', () => {
    expect(parseLicenseExpression('MIT AND Apache-2.0')).toEqual({
      kind: 'compound',
      rawExpression: 'MIT AND Apache-2.0',
      normalizedExpression: 'Apache-2.0 AND MIT',
      node: {
        kind: 'conjunction',
        conjunction: 'and',
        left: {
          kind: 'license',
          license: 'MIT',
          exception: undefined,
        },
        right: {
          kind: 'license',
          license: 'Apache-2.0',
          exception: undefined,
        },
      },
    });
  });

  it('parses WITH exceptions as atomic license nodes', () => {
    expect(
      parseLicenseExpression('GPL-2.0-only WITH Classpath-exception-2.0')
    ).toEqual({
      kind: 'atomic',
      rawExpression: 'GPL-2.0-only WITH Classpath-exception-2.0',
      normalizedExpression: 'GPL-2.0-only WITH Classpath-exception-2.0',
      node: {
        kind: 'license',
        license: 'GPL-2.0-only',
        exception: 'Classpath-exception-2.0',
      },
    });
  });

  it('returns an invalid result for an empty expression', () => {
    expect(parseLicenseExpression('   ')).toEqual({
      kind: 'invalid',
      rawExpression: '   ',
      normalizedExpression: '',
      error: 'License expression is empty.',
    });
  });

  it('returns an invalid result instead of throwing for bad input', () => {
    const result = parseLicenseExpression('MIT OR (');

    expect(result.kind).toBe('invalid');
    expect(result.rawExpression).toBe('MIT OR (');
    expect(result.normalizedExpression).toBe('MIT OR (');

    if (result.kind === 'invalid') {
      expect(result.error.length).toBeGreaterThan(0);
    }
  });

  it('supports custom LicenseRef identifiers', () => {
    expect(parseLicenseExpression('DocumentRef-foo:LicenseRef-bar')).toEqual({
      kind: 'atomic',
      rawExpression: 'DocumentRef-foo:LicenseRef-bar',
      normalizedExpression: 'DocumentRef-foo:LicenseRef-bar',
      node: {
        kind: 'license-ref',
        documentRef: 'DocumentRef-foo',
        licenseRef: 'LicenseRef-bar',
      },
    });
  });

  it('upgrades deprecated GPL plus syntax during parsing', () => {
    expect(parseLicenseExpression('GPL-2.0+')).toEqual({
      kind: 'atomic',
      rawExpression: 'GPL-2.0+',
      normalizedExpression: 'GPL-2.0-or-later',
      node: {
        kind: 'license',
        license: 'GPL-2.0-or-later',
        exception: undefined,
      },
    });
  });
});
