/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import { Link } from '@tanstack/react-router';

import { buttonVariants } from '@/components/ui/button-variants';
import { Label } from '@/components/ui/label';
import { Separator } from '@/components/ui/separator';
import { cn } from '@/lib/utils';

export interface SidebarNavProps {
  sections: {
    label?: string;
    items: {
      to?: string;
      params?: Record<string, string | undefined>;
      title: string;
      icon?: React.ComponentType<{
        className?: string;
      }>;
      visible?: boolean; // default: true
      activeOptions?: {
        exact?: boolean; // default: true
        includeSearch?: boolean; // default: false
        includeHash?: boolean; // default: false
      };
    }[];
    visible?: boolean; // default: true
  }[];
  className?: string;
}

export const Sidebar = ({ sections, className, ...props }: SidebarNavProps) => {
  // Helper function to determine if there are any visible sections after the given index.
  const visibleRemainingSections = (index: number) =>
    sections.slice(index + 1).some((section) => section.visible !== false);

  return (
    <nav className={cn('w-full', className)} {...props}>
      <div className='flex flex-col items-start'>
        {sections.map((section) => {
          if (section.visible === false) {
            return null;
          }
          const index = sections.indexOf(section);
          return (
            <div key={index} className='w-full'>
              {section.label && (
                <Label className='mb-1 text-sm text-muted-foreground'>
                  {section.label}
                </Label>
              )}
              {section.items.map((item) => {
                const disabled = item.to === undefined;
                return item.visible === undefined || item.visible ? (
                  <Link
                    key={item.title}
                    // When a link is disabled, make it point to completely separate
                    // (but existing) route on the server, otherwise all disabled links
                    // are grouped together with an existing one in the UI.
                    to={disabled ? '/' : item.to}
                    disabled={disabled}
                    params={item.params}
                    activeProps={{
                      className: 'gap-2 bg-muted hover:bg-muted',
                    }}
                    inactiveProps={
                      disabled
                        ? { className: 'gap-2 text-muted-foreground italic' }
                        : {
                            className:
                              'gap-2 hover:bg-transparent hover:underline',
                          }
                    }
                    activeOptions={{
                      exact:
                        item.activeOptions?.exact === undefined
                          ? true
                          : item.activeOptions.exact,
                      includeSearch:
                        item.activeOptions?.includeSearch === undefined
                          ? false
                          : item.activeOptions.includeSearch,
                      includeHash:
                        item.activeOptions?.includeHash === undefined
                          ? false
                          : item.activeOptions.includeHash,
                    }}
                    className={cn(
                      buttonVariants({ variant: 'ghost' }),
                      'w-full justify-start'
                    )}
                  >
                    {item.icon && <item.icon />}
                    {item.title}
                  </Link>
                ) : null;
              })}
              {sections.length > 1 &&
                index < sections.length - 1 &&
                visibleRemainingSections(index) && (
                  <Separator className='my-2' />
                )}
            </div>
          );
        })}
      </div>
    </nav>
  );
};
