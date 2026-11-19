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
  depth?: number;
  children?: MarkdownNode[];
  data?: { hName?: string; hProperties?: Record<string, unknown> };
};

const headingId = (node: MarkdownNode) => {
  const id = node.type === 'heading' ? node.data?.hProperties?.id : undefined;

  return typeof id === 'string' ? id : undefined;
};

// Wrap every heading with an ID and the nodes that belong to it in a section node.
const wrap = (nodes: MarkdownNode[]): MarkdownNode[] => {
  const result: MarkdownNode[] = [];
  let index = 0;

  while (index < nodes.length) {
    const node = nodes[index]!;
    const id = headingId(node);

    if (!id) {
      result.push(node);
      index++;
      continue;
    }

    // The section ends before the next heading of the same or a higher level.
    let end = index + 1;

    while (
      end < nodes.length &&
      !(
        nodes[end]!.type === 'heading' &&
        (nodes[end]!.depth ?? 0) <= (node.depth ?? 0)
      )
    ) {
      end++;
    }

    result.push({
      type: 'section',
      children: [node, ...wrap(nodes.slice(index + 1, end))],
      data: { hName: 'section', hProperties: { dataSection: id } },
    });
    index = end;
  }

  return result;
};

/**
 * A remark plugin that wraps each heading with an ID, and the content up to the next heading of
 * the same or a higher level, in a `<section data-section="<id>">`, so that a whole section can be
 * styled. It reads the IDs that remarkHeadingIds sets, so it has to come after that plugin.
 */
export const remarkSections = () => (tree: MarkdownNode) => {
  if (tree.children) tree.children = wrap(tree.children);
};
