/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import { useAdminServiceGetApiV1AdminContentManagementSectionsBySectionId } from '@/api/queries';
import { MarkdownRenderer } from '@/components/markdown-renderer.tsx';

function extractCompanyTag(markdown: string): string | undefined {
  const match = markdown.match(/:::\s?company\s+(.+?)\s+:::/s);
  return match ? match[1] : undefined;
}

function extractColumns(markdown: string): string[] {
  const regex = /:::\s?column\s+([\s\S]*?)\s+:::/g;
  const matches = [];
  let match;

  while ((match = regex.exec(markdown)) !== null) {
    if (match[1] !== undefined) {
      matches.push(match[1].trim());
    }
  }

  return matches;
}

export function Footer() {
  const { data } =
    useAdminServiceGetApiV1AdminContentManagementSectionsBySectionId({
      sectionId: 'footer',
    });

  const markdown = data?.markdown || '';
  const enabled = data?.isEnabled || false;

  const companyTag = extractCompanyTag(markdown);
  const columns = extractColumns(markdown);
  return (
    enabled && (
      <footer className='bg-muted text-muted-foreground w-full border-t text-sm'>
        <div className='mx-auto flex max-w-screen-xl flex-col items-center justify-between gap-4 p-4 sm:flex-row md:w-full md:gap-8 md:p-8'>
          {/* Left: copyright or app info */}
          <MarkdownRenderer markdown={companyTag || ''} />
          {/* Right: links, legal, repo */}
          <nav className='flex gap-4'>
            {columns.map((content, index) => (
              <MarkdownRenderer key={index} markdown={content} />
            ))}
          </nav>
        </div>
      </footer>
    )
  );
}
