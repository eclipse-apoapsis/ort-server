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

// The parts of a Markdown syntax tree node that the plugin reads and writes.
type MarkdownNode = {
  type: string;
  value?: string;
  children?: MarkdownNode[];
  data?: { hProperties?: Record<string, unknown> };
};

const HEADING_ID = /\s*\{#([\w-]+)\}\s*$/;

type RemarkHeadingIdsOptions = {
  // A prefix for the IDs, so that they do not clash with the IDs of the page.
  prefix?: string;
};

const visit = (node: MarkdownNode, prefix: string) => {
  if (node.type === 'heading') {
    const last = node.children?.at(-1);
    const match =
      last?.type === 'text' ? HEADING_ID.exec(last.value ?? '') : null;

    if (last && match) {
      last.value = last.value?.slice(0, match.index);
      node.data = {
        ...node.data,
        hProperties: { ...node.data?.hProperties, id: `${prefix}${match[1]}` },
      };
    }
  }

  node.children?.forEach((child) => visit(child, prefix));
};

/**
 * A remark plugin that gives a heading the ID written at its end as `{#id}`, the syntax Docusaurus
 * uses for explicit heading IDs. The marker is removed from the heading text.
 */
export const remarkHeadingIds =
  ({ prefix = '' }: RemarkHeadingIdsOptions = {}) =>
  (tree: MarkdownNode) => {
    visit(tree, prefix);
  };
