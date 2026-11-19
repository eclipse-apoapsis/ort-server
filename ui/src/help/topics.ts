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

import { helpIndex } from './index.gen';

type HelpIndex = typeof helpIndex;

/** The name of a help document, which is the Markdown file `<name>.md` in this directory. */
export type HelpDocument = keyof HelpIndex;

/** A help document, optionally followed by `#` and the ID of a section in it. */
export type HelpTopic = {
  [Document in HelpDocument]:
    Document | `${Document}#${keyof HelpIndex[Document]['sections'] & string}`;
}[HelpDocument];

// The index as plain records, for looking up topics that are only known as strings at runtime.
const index: Record<
  string,
  { title: string; sections: Record<string, string> } | undefined
> = helpIndex;

/** The loaders of all help documents, keyed by their path relative to this directory. */
const helpDocumentFiles = import.meta.glob<string>(['./*.md', '!./README.md'], {
  query: '?raw',
  import: 'default',
});

/** Split a help topic into its document and its optional section. */
export const parseHelpTopic = (topic: string) => {
  const [document = '', section] = topic.split('#');

  return { document, section };
};

/** Return the title of the section of a help topic, or of its document if it has no section. */
export const getHelpTopicTitle = (topic: string) => {
  const { document, section } = parseHelpTopic(topic);
  const entry = index[document];

  return section ? entry?.sections[section] : entry?.title;
};

/** Load the Markdown text of a help document. */
export const loadHelpDocument = (document: string) => {
  const load = helpDocumentFiles[`./${document}.md`];

  if (!load) {
    throw new Error(`There is no help document '${document}'.`);
  }

  // The dev server serves the current text of the file, which modules cached by the browser would
  // not, so that an edited document shows its changes without a page reload.
  if (import.meta.hot && import.meta.env.MODE !== 'test') {
    return fetch(`/src/help/${document}.md`, { cache: 'no-store' }).then(
      (response) => response.text()
    );
  }

  return load();
};
