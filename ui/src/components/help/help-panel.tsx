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

import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouterState } from '@tanstack/react-router';
import { useEffect, useRef } from 'react';

import { LoadingIndicator } from '@/components/loading-indicator';
import { MarkdownRenderer } from '@/components/markdown-renderer';
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet';
import {
  getHelpTopicTitle,
  loadHelpDocument,
  parseHelpTopic,
} from '@/help/topics';
import { remarkHeadingIds } from '@/lib/remark-heading-ids';
import { remarkSections } from '@/lib/remark-sections';
import { useHelpPanelStore } from '@/store/help-panel.store';

// The prefix of the section IDs, so that they do not clash with the IDs of the page.
const SECTION_ID_PREFIX = 'help-';

const HIGHLIGHT_CLASSES = ['bg-accent', 'rounded-sm'];

// The elements that the Tab key moves the focus to.
const TABBABLE_SELECTOR =
  'a[href], button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])';

// Remove the level one heading, which is the title of a help document and shown as the title of
// the panel.
const removeTitle = (markdown: string) => markdown.replace(/^# .*\n/, '');

const HelpDocumentText = ({
  text,
  section,
}: {
  text: string;
  section?: string;
}) => {
  // Scroll to the section of the topic and highlight its heading, so that the user sees where the
  // answer starts.
  useEffect(() => {
    if (!section) return;

    const heading = document.getElementById(`${SECTION_ID_PREFIX}${section}`);

    if (!heading) return;

    heading.scrollIntoView({ block: 'start' });
    heading.classList.add(...HIGHLIGHT_CLASSES);

    // The section around the heading gets a line on its left, see the styles of the panel.
    const sectionElement = heading.closest('section');
    sectionElement?.setAttribute('data-highlighted-section', '');

    return () => {
      heading.classList.remove(...HIGHLIGHT_CLASSES);
      sectionElement?.removeAttribute('data-highlighted-section');
    };
  }, [text, section]);

  return (
    <MarkdownRenderer
      markdown={text}
      remarkPlugins={[
        [remarkHeadingIds, { prefix: SECTION_ID_PREFIX }],
        remarkSections,
      ]}
    />
  );
};

/**
 * The panel on the right that shows the help document chosen with a help button, scrolled to the
 * section of the button. It does not block the page, so that the user can keep working while
 * reading the help, and Tab leads out of the panel back to the page. It closes on Escape, with its
 * close button, with the help button of the shown topic, and on navigation to another page.
 */
export const HelpPanel = () => {
  const isOpen = useHelpPanelStore((state) => state.isOpen);
  const topic = useHelpPanelStore((state) => state.topic);
  const trigger = useHelpPanelStore((state) => state.trigger);
  const close = useHelpPanelStore((state) => state.close);
  const pathname = useRouterState({
    select: (state) => state.location.pathname,
  });
  const { document: helpDocument, section } = topic
    ? parseHelpTopic(topic)
    : { document: undefined, section: undefined };

  const { data, isPending, isError } = useQuery({
    queryKey: ['help-document', helpDocument],
    queryFn: () => loadHelpDocument(helpDocument!),
    enabled: helpDocument !== undefined,
    staleTime: Infinity,
    select: removeTitle,
  });

  useEffect(() => {
    close();
  }, [pathname, close]);

  const scrollContainer = useRef<HTMLDivElement>(null);

  // A topic without a section shows the document from the top, also when the panel was scrolled to
  // a section before.
  useEffect(() => {
    if (!section && scrollContainer.current) {
      scrollContainer.current.scrollTop = 0;
    }
  }, [helpDocument, section, data]);

  // During development, reload the shown document when it is edited.
  const queryClient = useQueryClient();

  useEffect(() => {
    const hot = import.meta.hot;

    if (!hot) return;

    const reload = () =>
      void queryClient.invalidateQueries({ queryKey: ['help-document'] });

    hot.on('help:document-changed', reload);

    return () => hot.off('help:document-changed', reload);
  }, [queryClient]);

  return (
    <Sheet
      open={isOpen}
      onOpenChange={(open) => {
        if (!open) close();
      }}
      modal={false}
    >
      <SheetContent
        side='right'
        aria-describedby={undefined}
        // Clicks on the page must not close the panel, as the user may be filling in a form while
        // reading the help.
        onInteractOutside={(event) => event.preventDefault()}
        // The dialog keeps the focus inside the panel, but the user should be able to go back to
        // the page and keep the help open. Tabbing out of either end of the panel returns to the
        // help button that opened it.
        onKeyDown={(event) => {
          if (event.key !== 'Tab' || !trigger?.isConnected) return;

          const tabbables = [
            ...event.currentTarget.querySelectorAll<HTMLElement>(
              TABBABLE_SELECTOR
            ),
          ];
          const edge = event.shiftKey ? tabbables.at(0) : tabbables.at(-1);

          if (tabbables.length === 0 || document.activeElement === edge) {
            event.preventDefault();
            event.stopPropagation();
            trigger.focus();
          }
        }}
        onCloseAutoFocus={(event) => {
          event.preventDefault();
          trigger?.focus();
        }}
        className='gap-0 sm:max-w-md'
      >
        <SheetHeader className='border-b pr-12'>
          <SheetTitle>
            {(helpDocument && getHelpTopicTitle(helpDocument)) ?? 'Help'}
          </SheetTitle>
        </SheetHeader>
        <div
          ref={scrollContainer}
          // Every section has a transparent line on its left, which turns into the primary colour
          // when the section is highlighted. Rounded corners curve its ends towards the text. The
          // negative margin keeps the text of the sections in line with the overview.
          className='[&_section[data-highlighted-section]]:border-primary overflow-y-auto p-4 [&_section]:-ml-3 [&_section]:rounded-l-lg [&_section]:border-l-4 [&_section]:border-transparent [&_section]:pl-2 [&_section]:transition-colors'
        >
          {isPending ? (
            <LoadingIndicator />
          ) : isError ? (
            <p className='text-destructive text-sm'>
              The help text could not be loaded.
            </p>
          ) : (
            <HelpDocumentText text={data} section={section} />
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
};
