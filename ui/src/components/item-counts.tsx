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

import { Link, LinkProps } from '@tanstack/react-router';
import { Bug, Scale, ShieldQuestion } from 'lucide-react';

import { Button } from '@/components/ui/button';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { cn } from '@/lib/utils';

const issuesRoute =
  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/issues' as const;

const vulnerabilitiesRoute =
  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/vulnerabilities' as const;

const ruleViolationsRoute =
  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/rule-violations' as const;

type ItemCountsLink = {
  params: LinkProps['params'];
  issuesSearch?: LinkProps['search'];
  vulnerabilitiesSearch?: LinkProps['search'];
  ruleViolationsSearch?: LinkProps['search'];
};

type ItemCountsProps = {
  statistics:
    | {
        issuesCount?: number | null;
        vulnerabilitiesCount?: number | null;
        ruleViolationsCount?: number | null;
      }
    | undefined;
  showIssues?: boolean;
  showVulnerabilities?: boolean;
  showRuleViolations?: boolean;
  compact?: boolean;
  wide?: boolean;
  link?: ItemCountsLink;
  tooltip?: (label: string, count: number) => string;
};

export const ItemCounts = ({
  statistics,
  showIssues = true,
  showVulnerabilities = true,
  showRuleViolations = true,
  compact = false,
  wide = false,
  link,
  tooltip,
}: ItemCountsProps) => {
  return (
    <div className='grid grid-cols-3 gap-1'>
      {showIssues && (
        <CountBadge
          count={statistics?.issuesCount}
          icon={Bug}
          label='issues'
          colStart='col-start-1'
          compact={compact}
          link={
            link && {
              params: link.params,
              to: issuesRoute,
              search: link.issuesSearch,
            }
          }
          wide={wide}
          tooltip={tooltip}
        />
      )}
      {showVulnerabilities && (
        <CountBadge
          count={statistics?.vulnerabilitiesCount}
          icon={ShieldQuestion}
          label='vulnerabilities'
          colStart='col-start-2'
          compact={compact}
          link={
            link && {
              params: link.params,
              to: vulnerabilitiesRoute,
              search: link.vulnerabilitiesSearch,
            }
          }
          wide={wide}
          tooltip={tooltip}
        />
      )}
      {showRuleViolations && (
        <CountBadge
          count={statistics?.ruleViolationsCount}
          icon={Scale}
          label='rule violations'
          colStart='col-start-3'
          compact={compact}
          link={
            link && {
              params: link.params,
              to: ruleViolationsRoute,
              search: link.ruleViolationsSearch,
            }
          }
          wide={wide}
          tooltip={tooltip}
        />
      )}
    </div>
  );
};

type CountBadgeLinkProps = {
  params: LinkProps['params'];
  to: LinkProps['to'];
  search?: LinkProps['search'];
};

type CountBadgeProps = {
  count: number | null | undefined;
  icon: typeof Bug;
  label: string;
  colStart: string;
  compact: boolean;
  wide: boolean;
  link?: CountBadgeLinkProps;
  tooltip?: (label: string, count: number) => string;
};

const CountBadge = ({
  count,
  icon: Icon,
  label,
  colStart,
  compact,
  wide,
  link,
  tooltip,
}: CountBadgeProps) => {
  if (count == null) return null;

  const displayCount = compact && count > 99 ? '99+' : count;

  const tooltipText = tooltip
    ? tooltip(label, count)
    : link
      ? `View ${label}`
      : `${count} ${label} in total`;

  const showTooltip = link || (compact && count > 99);
  const widthClass = wide ? 'w-14' : 'w-12';

  const badgeContent = (
    <>
      <Icon className='size-3' />
      <div className='text-xs'>{displayCount}</div>
    </>
  );

  const button = link ? (
    <Button variant='outline' size='xs' asChild className='flex justify-start'>
      <Link to={link.to} params={link.params} search={link.search}>
        {badgeContent}
      </Link>
    </Button>
  ) : (
    <Button
      variant='outline'
      size='xs'
      className={cn('flex cursor-default justify-start', colStart, widthClass)}
    >
      {badgeContent}
    </Button>
  );

  if (showTooltip) {
    return (
      <Tooltip>
        <TooltipTrigger asChild className={cn(colStart, widthClass)}>
          {button}
        </TooltipTrigger>
        <TooltipContent>{tooltipText}</TooltipContent>
      </Tooltip>
    );
  }

  return button;
};
