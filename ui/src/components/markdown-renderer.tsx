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

import Markdown from 'react-markdown';

type MarkdownRendererProps = {
  markdown?: string;
};

export const MarkdownRenderer = ({ markdown }: MarkdownRendererProps) => {
  return (
    <div className='prose dark:prose-invert text-sm'>
      <Markdown
        components={{
          h1: ({ children }) => (
            <p
              role='heading'
              aria-level={1}
              className='mt-6 mb-6 text-xl font-semibold'
            >
              {children}
            </p>
          ),
          h2: ({ children }) => (
            <p
              role='heading'
              aria-level={2}
              className='mt-4 mb-4 text-lg font-semibold'
            >
              {children}
            </p>
          ),
          h3: ({ children }) => (
            <p
              role='heading'
              aria-level={3}
              className='mt-2 mb-2 font-semibold'
            >
              {children}
            </p>
          ),
          h4: ({ children }) => (
            <p role='heading' aria-level={4} className='mt-2 font-semibold'>
              {children}
            </p>
          ),
          h5: ({ children }) => (
            <p role='heading' aria-level={5} className='mt-2 font-semibold'>
              {children}
            </p>
          ),
          h6: ({ children }) => (
            <p role='heading' aria-level={6} className='mt-2 font-semibold'>
              {children}
            </p>
          ),
          pre: ({ children }) => {
            return (
              <pre className='dark:bg-muted/100 rounded bg-gray-800 p-1'>
                {children}
              </pre>
            );
          },
          code: ({ children }) => {
            return <code className='p-1'>{children}</code>;
          },
          a: ({ children, href }) => (
            <a
              href={href}
              target='_blank'
              rel='noopener noreferrer'
              className='font-semibold text-blue-400 hover:underline'
            >
              {children}
            </a>
          ),
        }}
      >
        {markdown}
      </Markdown>
    </div>
  );
};
