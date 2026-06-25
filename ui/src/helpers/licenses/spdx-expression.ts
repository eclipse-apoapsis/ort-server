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
