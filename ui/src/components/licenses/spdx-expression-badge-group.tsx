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

import * as React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';

import { LicenseBadge } from '@/components/licenses/license-badge';
import {
  parseLicenseExpression,
  type SpdxConjunctionNode,
  type SpdxExpressionNode,
  type SpdxLicenseNode,
} from '@/helpers/licenses/spdx-expression';
import { cn } from '@/lib/utils';

type SpdxExpressionBadgeGroupProps = React.ComponentProps<'span'> & {
  expression: string | null | undefined;
};

const expressionWrapperClassName =
  'inline-flex max-w-full flex-wrap items-center gap-1';

function licenseNodeToString(node: SpdxLicenseNode): string {
  if (node.kind === 'license-ref') {
    return node.documentRef
      ? `${node.documentRef}:${node.licenseRef}`
      : node.licenseRef;
  }

  return node.exception
    ? `${node.license} WITH ${node.exception}`
    : node.license;
}

function needsParentheses(
  parent: SpdxConjunctionNode,
  child: SpdxExpressionNode
): boolean {
  return (
    child.kind === 'conjunction' &&
    parent.conjunction === 'and' &&
    child.conjunction === 'or'
  );
}

function renderExpressionNode(
  node: SpdxExpressionNode,
  parent?: SpdxConjunctionNode
): React.ReactNode {
  if (node.kind !== 'conjunction') {
    return <LicenseBadge license={licenseNodeToString(node)} />;
  }

  const renderedGroup = (
    <>
      {renderExpressionNode(node.left, node)}
      <span className='text-muted-foreground text-xs font-medium uppercase'>
        {node.conjunction}
      </span>
      {renderExpressionNode(node.right, node)}
    </>
  );

  if (parent && needsParentheses(parent, node)) {
    return (
      <>
        <span className='text-muted-foreground text-xs font-medium'>(</span>
        {renderedGroup}
        <span className='text-muted-foreground text-xs font-medium'>)</span>
      </>
    );
  }

  return renderedGroup;
}

export function SpdxExpressionBadgeGroup({
  expression,
  className,
  title,
  ...props
}: SpdxExpressionBadgeGroupProps) {
  if (!expression?.trim()) {
    return null;
  }

  const parsedExpression = parseLicenseExpression(expression);

  if (parsedExpression.kind === 'invalid') {
    return (
      <span className={cn(expressionWrapperClassName, className)}>
        <LicenseBadge license={parsedExpression.rawExpression.trim()} />
      </span>
    );
  }

  if (parsedExpression.kind === 'atomic') {
    return (
      <span className={cn(expressionWrapperClassName, className)}>
        <LicenseBadge
          license={licenseNodeToString(parsedExpression.node)}
          title={title ?? parsedExpression.normalizedExpression}
          {...props}
        />
      </span>
    );
  }

  return (
    <span
      className={cn(expressionWrapperClassName, className)}
      title={title ?? parsedExpression.normalizedExpression}
      {...props}
    >
      {renderExpressionNode(parsedExpression.node)}
    </span>
  );
}

//
// Unit tests for SPDX expression badge rendering.
//

if (import.meta.vitest) {
  const { describe, it, expect } = import.meta.vitest;

  describe('SpdxExpressionBadgeGroup', () => {
    it('renders a single badge for an atomic SPDX expression', () => {
      const markup = renderToStaticMarkup(
        <SpdxExpressionBadgeGroup expression='MIT' />
      );

      expect(markup).toContain('MIT');
      expect(markup.match(/data-slot="badge"/g)?.length).toBe(1);
      expect(markup).not.toContain('AND');
      expect(markup).not.toContain('OR');
    });

    it('renders conjunctions between multiple badges', () => {
      const markup = renderToStaticMarkup(
        <SpdxExpressionBadgeGroup expression='MIT AND Apache-2.0' />
      );

      expect(markup).toContain('MIT');
      expect(markup).toContain('Apache-2.0');
      expect(markup).toContain('and');
      expect(markup.match(/data-slot="badge"/g)?.length).toBe(2);
    });

    it('renders parentheses when an OR group is nested inside an AND group', () => {
      const markup = renderToStaticMarkup(
        <SpdxExpressionBadgeGroup expression='MIT AND (Apache-2.0 OR BSD-3-Clause)' />
      );

      expect(markup).toContain('(');
      expect(markup).toContain(')');
      expect(markup).toContain('MIT');
      expect(markup).toContain('Apache-2.0');
      expect(markup).toContain('BSD-3-Clause');
      expect(markup.match(/data-slot="badge"/g)?.length).toBe(3);
    });

    it('renders WITH expressions as a single badge', () => {
      const markup = renderToStaticMarkup(
        <SpdxExpressionBadgeGroup expression='GPL-2.0-only WITH Classpath-exception-2.0' />
      );

      expect(markup).toContain('GPL-2.0-only WITH Classpath-exception-2.0');
      expect(markup.match(/data-slot="badge"/g)?.length).toBe(1);
    });

    it('renders deprecated GPL plus syntax as its normalized atomic badge', () => {
      const markup = renderToStaticMarkup(
        <SpdxExpressionBadgeGroup expression='GPL-2.0+' />
      );

      expect(markup).toContain('GPL-2.0-or-later');
      expect(markup.match(/data-slot="badge"/g)?.length).toBe(1);
    });

    it('falls back to a single badge for invalid expressions', () => {
      const markup = renderToStaticMarkup(
        <SpdxExpressionBadgeGroup expression='MIT OR (' />
      );

      expect(markup).toContain('MIT OR (');
      expect(markup.match(/data-slot="badge"/g)?.length).toBe(1);
      expect(markup).not.toContain(
        'class="text-muted-foreground text-xs font-medium uppercase"'
      );
    });
  });
}
