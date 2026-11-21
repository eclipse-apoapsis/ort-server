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

import { InfoIcon } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { getHelpTopicTitle, type HelpTopic } from '@/help/topics';
import { cn } from '@/lib/utils';
import { useHelpPanelStore } from '@/store/help-panel.store';

type HelpButtonProps = {
  // The help document to open, optionally followed by `#` and the section to scroll to.
  topic: HelpTopic;
  className?: string;
};

/**
 * An info button that opens the help panel with the given topic. Place it next to the UI element
 * it explains, never inside a label, as a click inside a label focuses the labelled input. Its
 * name for screen readers is the title of the topic's section, so name the section after the
 * element.
 */
export const HelpButton = ({ topic, className }: HelpButtonProps) => {
  // The store knows topics only as strings.
  const topicId: string = topic;
  const title = getHelpTopicTitle(topicId);

  const isShown = useHelpPanelStore(
    (state) => state.isOpen && state.topic === topicId
  );
  const open = useHelpPanelStore((state) => state.open);
  const close = useHelpPanelStore((state) => state.close);

  return (
    <Button
      type='button'
      variant='ghost'
      size='icon'
      // While its topic is shown, the icon turns into a filled disc, so the user sees which
      // element the open help belongs to.
      className={cn(
        'text-muted-foreground aria-expanded:text-primary-foreground aria-expanded:hover:text-primary-foreground aria-expanded:[&_circle]:fill-primary aria-expanded:[&_circle]:stroke-primary size-6 rounded-full',
        className
      )}
      aria-label={title ? `Help: ${title}` : 'Help'}
      aria-expanded={isShown}
      onClick={(event) =>
        isShown ? close() : open(topicId, event.currentTarget)
      }
    >
      <InfoIcon className='size-5' />
    </Button>
  );
};
