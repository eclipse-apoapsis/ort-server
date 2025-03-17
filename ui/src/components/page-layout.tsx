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

import { Sidebar, SidebarNavProps } from '@/components/sidebar';
import { Bar, Content, Page, Pane } from '@/components/ui/layout';

interface PageLayoutProps {
  sections: SidebarNavProps['sections'] | undefined;
  topBar?: React.ReactNode;
  children: React.ReactNode;
}

export const PageLayout = ({
  sections,
  topBar,

  children,
}: PageLayoutProps) => {
  return (
    <>
      {topBar && (
        <Bar className='-mt-8 flex flex-col justify-center md:flex-row'>
          <Content className='w-full max-w-[1400px]'>{topBar}</Content>
        </Bar>
      )}
      <Page className='flex flex-col justify-center gap-4 md:flex-row'>
        {sections && (
          <Pane className='w-full md:w-52'>
            <Sidebar sections={sections} />
          </Pane>
        )}
        <Content className='w-full md:max-w-5xl'>{children}</Content>
      </Page>
    </>
  );
};
