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
import { parseLicenseExpression } from '@/helpers/licenses/spdx-expression';
import { cn } from '@/lib/utils';

type SpdxExpressionBadgeGroupProps = React.ComponentProps<'span'> & {
  expression: string | null | undefined;
  suffix?: React.ReactNode;
};

const expressionWrapperClassName =
  'inline-flex max-w-full flex-wrap items-center gap-1';

type ExpressionToken =
  | { type: 'license'; value: string }
  | { type: 'operator'; value: string }
  | { type: 'parenthesis'; value: string };

/** Split an SPDX expression for display without changing its order or grouping. */
function tokenizeExpressionForDisplay(expression: string): ExpressionToken[] {
  const tokens: ExpressionToken[] = [];
  const separatorPattern = /(\(|\)|\s+(?:AND|OR)\s+)/gi;
  let start = 0;

  for (const match of expression.matchAll(separatorPattern)) {
    const index = match.index;
    const license = expression.slice(start, index).trim();
    if (license) tokens.push({ type: 'license', value: license });

    const separator = match[0];
    const value = separator.trim();
    tokens.push({
      type: value === '(' || value === ')' ? 'parenthesis' : 'operator',
      value,
    });

    start = index + separator.length;
  }

  const license = expression.slice(start).trim();
  if (license) tokens.push({ type: 'license', value: license });

  return tokens;
}

function renderToken(token: ExpressionToken, expressionTitle: string) {
  return token.type === 'license' ? (
    <LicenseBadge license={token.value} title={expressionTitle} />
  ) : (
    <span className='text-muted-foreground text-xs font-medium'>
      {token.value}
    </span>
  );
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

  const rawExpression = parsedExpression.rawExpression.trim();

  if (parsedExpression.kind === 'atomic') {
    return (
      <span className={cn(expressionWrapperClassName, className)}>
        <span className='inline-flex items-center'>
          <LicenseBadge
            license={rawExpression}
            title={title ?? rawExpression}
            {...props}
          />
          {suffix}
        </span>
      </span>
    );
  }

  const expressionTitle = title ?? rawExpression;
  const tokens = tokenizeExpressionForDisplay(rawExpression);

  return (
    <span
      className={cn(expressionWrapperClassName, className)}
      title={expressionTitle}
      {...props}
    >
      {tokens.map((token, index) => {
        const renderedToken = renderToken(token, expressionTitle);

        return index === tokens.length - 1 && suffix ? (
          <span key={index} className='inline-flex items-center'>
            {renderedToken}
            {suffix}
          </span>
        ) : (
          <React.Fragment key={index}>{renderedToken}</React.Fragment>
        );
      })}
    </span>
  );
}
