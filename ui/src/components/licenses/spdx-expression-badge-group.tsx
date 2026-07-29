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
  suffix?: React.ReactNode;
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

function needsParentheses(child: SpdxExpressionNode): boolean {
  return child.kind === 'conjunction';
}

function renderExpressionNode(
  node: SpdxExpressionNode,
  expressionTitle: string,
  parent?: SpdxConjunctionNode,
  suffix?: React.ReactNode
): React.ReactNode {
  if (node.kind !== 'conjunction') {
    const badge = (
      <LicenseBadge
        license={licenseNodeToString(node)}
        title={expressionTitle}
      />
    );

    return suffix ? (
      <span className='inline-flex items-center'>
        {badge}
        {suffix}
      </span>
    ) : (
      badge
    );
  }

  const parenthesized = Boolean(parent) && needsParentheses(node);
  const renderedGroup = (
    <>
      {renderExpressionNode(node.left, expressionTitle, node)}
      <span className='text-muted-foreground text-xs font-medium uppercase'>
        {node.conjunction}
      </span>
      {renderExpressionNode(
        node.right,
        expressionTitle,
        node,
        parenthesized ? undefined : suffix
      )}
    </>
  );

  if (parenthesized) {
    return (
      <>
        <span className='text-muted-foreground text-xs font-medium'>(</span>
        {renderedGroup}
        <span className='inline-flex items-center'>
          <span className='text-muted-foreground text-xs font-medium'>)</span>
          {suffix}
        </span>
      </>
    );
  }

  return renderedGroup;
}

export function SpdxExpressionBadgeGroup({
  expression,
  className,
  title,
  suffix,
  ...props
}: SpdxExpressionBadgeGroupProps) {
  if (!expression?.trim()) {
    return null;
  }

  const parsedExpression = parseLicenseExpression(expression);

  if (parsedExpression.kind === 'invalid') {
    return (
      <span className={cn(expressionWrapperClassName, className)}>
        <span className='inline-flex items-center'>
          <LicenseBadge license={parsedExpression.rawExpression.trim()} />
          {suffix}
        </span>
      </span>
    );
  }

  if (parsedExpression.kind === 'atomic') {
    return (
      <span className={cn(expressionWrapperClassName, className)}>
        <span className='inline-flex items-center'>
          <LicenseBadge
            license={licenseNodeToString(parsedExpression.node)}
            title={title ?? parsedExpression.normalizedExpression}
            {...props}
          />
          {suffix}
        </span>
      </span>
    );
  }

  const normalizedParsedExpression = parseLicenseExpression(
    parsedExpression.normalizedExpression
  );
  const expressionNode =
    normalizedParsedExpression.kind === 'compound'
      ? normalizedParsedExpression.node
      : parsedExpression.node;
  const expressionTitle = title ?? parsedExpression.normalizedExpression;

  return (
    <span
      className={cn(expressionWrapperClassName, className)}
      title={expressionTitle}
      {...props}
    >
      {renderExpressionNode(expressionNode, expressionTitle, undefined, suffix)}
    </span>
  );
}
