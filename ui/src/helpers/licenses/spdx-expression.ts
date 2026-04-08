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

import {
  normalize as normalizeSpdx,
  parse as parseSpdx,
  type ParsedSpdxExpression,
} from 'license-expressions';

export type SpdxLicenseNode =
  | {
      kind: 'license';
      license: string;
      exception?: string;
    }
  | {
      kind: 'license-ref';
      licenseRef: string;
      documentRef?: string;
    };

export type SpdxConjunctionNode = {
  kind: 'conjunction';
  conjunction: 'and' | 'or';
  left: SpdxExpressionNode;
  right: SpdxExpressionNode;
};

export type SpdxExpressionNode = SpdxLicenseNode | SpdxConjunctionNode;

export type ParsedSpdxExpressionResult =
  | {
      kind: 'atomic';
      rawExpression: string;
      normalizedExpression: string;
      node: SpdxLicenseNode;
    }
  | {
      kind: 'compound';
      rawExpression: string;
      normalizedExpression: string;
      node: SpdxConjunctionNode;
    }
  | {
      kind: 'invalid';
      rawExpression: string;
      normalizedExpression: string;
      error: string;
    };

const DEFAULT_PARSE_OPTIONS = {
  strictSyntax: false,
  upgradeGPLVariants: true,
};

function mapParsedNode(node: ParsedSpdxExpression): SpdxExpressionNode {
  if ('conjunction' in node) {
    return {
      kind: 'conjunction',
      conjunction: node.conjunction,
      left: mapParsedNode(node.left),
      right: mapParsedNode(node.right),
    };
  }

  if ('licenseRef' in node) {
    return {
      kind: 'license-ref',
      licenseRef: node.licenseRef,
      documentRef: node.documentRef,
    };
  }

  return {
    kind: 'license',
    license: node.license,
    exception: node.exception,
  };
}

export function normalizeLicenseExpression(expression: string): string {
  const trimmedExpression = expression.trim();

  if (trimmedExpression.length === 0) {
    return '';
  }

  try {
    return normalizeSpdx(trimmedExpression);
  } catch {
    return trimmedExpression;
  }
}

export function parseLicenseExpression(
  expression: string
): ParsedSpdxExpressionResult {
  const trimmedExpression = expression.trim();

  if (trimmedExpression.length === 0) {
    return {
      kind: 'invalid',
      rawExpression: expression,
      normalizedExpression: '',
      error: 'License expression is empty.',
    };
  }

  try {
    const parsedExpression = parseSpdx(
      trimmedExpression,
      DEFAULT_PARSE_OPTIONS
    );
    const mappedExpression = mapParsedNode(parsedExpression);
    const normalizedExpression = normalizeLicenseExpression(trimmedExpression);

    if (mappedExpression.kind === 'conjunction') {
      return {
        kind: 'compound',
        rawExpression: expression,
        normalizedExpression,
        node: mappedExpression,
      };
    }

    return {
      kind: 'atomic',
      rawExpression: expression,
      normalizedExpression,
      node: mappedExpression,
    };
  } catch (error) {
    return {
      kind: 'invalid',
      rawExpression: expression,
      normalizedExpression: trimmedExpression,
      error:
        error instanceof Error
          ? error.message
          : 'Failed to parse license expression.',
    };
  }
}

export function isAtomicLicenseExpression(expression: string): boolean {
  return parseLicenseExpression(expression).kind === 'atomic';
}

//
// Unit tests for SPDX expression parsing helpers.
//

if (import.meta.vitest) {
  const { describe, it, expect } = import.meta.vitest;

  describe('normalizeLicenseExpression', () => {
    it('normalizes valid SPDX expressions', () => {
      expect(normalizeLicenseExpression('  mit or apache-2.0  ')).toBe(
        'Apache-2.0 OR MIT'
      );
    });

    it('normalizes deprecated GPL plus syntax', () => {
      expect(normalizeLicenseExpression('GPL-2.0+')).toBe('GPL-2.0-or-later');
    });

    it('returns a trimmed original string for invalid expressions', () => {
      expect(normalizeLicenseExpression(' MIT OR ( ')).toBe('MIT OR (');
    });
  });

  describe('parseLicenseExpression', () => {
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

    it('returns an invalid result instead of throwing for bad input', () => {
      const result = parseLicenseExpression('MIT OR (');

      expect(result.kind).toBe('invalid');
      expect(result.rawExpression).toBe('MIT OR (');
      expect(result.normalizedExpression).toBe('MIT OR (');

      if (result.kind === 'invalid') {
        expect(result.error.length).toBeGreaterThan(0);
      }
    });

    it('returns an invalid result for an empty expression', () => {
      expect(parseLicenseExpression('   ')).toEqual({
        kind: 'invalid',
        rawExpression: '   ',
        normalizedExpression: '',
        error: 'License expression is empty.',
      });
    });
  });

  describe('isAtomicLicenseExpression', () => {
    it('returns true for a single SPDX license', () => {
      expect(isAtomicLicenseExpression('Apache-2.0')).toBe(true);
    });

    it('returns false for compound expressions', () => {
      expect(isAtomicLicenseExpression('Apache-2.0 OR MIT')).toBe(false);
    });

    it('returns false for invalid expressions', () => {
      expect(isAtomicLicenseExpression('MIT OR (')).toBe(false);
    });

    it('returns true for normalized GPL plus syntax because it remains atomic', () => {
      expect(isAtomicLicenseExpression('GPL-2.0+')).toBe(true);
    });
  });
}
