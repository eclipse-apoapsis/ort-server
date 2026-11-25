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

// @vitest-environment jsdom

import { screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { HelpButton } from '@/components/help/help-button';
import { HelpPanel } from '@/components/help/help-panel';
import type { HelpTopic } from '@/help/topics';
import { useHelpPanelStore } from '@/store/help-panel.store';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';

const documents: Record<string, string> = {
  first: [
    '# First Document',
    'The overview of the first document.',
    '## Alpha {#alpha}',
    'The alpha section.',
    '## Beta {#beta}',
    'The beta section.',
  ].join('\n\n'),
  second: [
    '# Second Document',
    'The overview of the second document.',
    'Read [the beta section](first.md#beta) or [the website](https://example.org).',
  ].join('\n\n'),
};

vi.mock('@/help/index.gen', () => ({
  helpIndex: {
    first: {
      title: 'First Document',
      sections: { alpha: 'Alpha', beta: 'Beta' },
    },
    second: { title: 'Second Document', sections: {} },
  },
}));

vi.mock('@/help/topics', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/help/topics')>()),
  loadHelpDocument: (document: string) => Promise.resolve(documents[document]),
}));

// The test documents are not part of the generated index, so their topics are not known to
// TypeScript.
const topic = (id: string) => id as unknown as HelpTopic;

const renderPage = async () => {
  const result = renderInteractiveWithRouter(
    <>
      <input aria-label='Field' />
      <HelpButton topic={topic('first')} />
      <HelpButton topic={topic('first#alpha')} />
      <HelpButton topic={topic('first#beta')} />
      <HelpButton topic={topic('second')} />
      <HelpPanel />
    </>,
    {
      path: '/page',
      routes: [{ path: '/page' }, { path: '/other' }],
      withQueryClient: true,
    }
  );
  await screen.findByRole('button', { name: 'Help: First Document' });

  return result;
};

const helpButton = (label: string) =>
  screen.getByRole('button', { name: `Help: ${label}` });

describe('HelpPanel', () => {
  beforeEach(() => {
    useHelpPanelStore.setState({ isOpen: false, topic: null, trigger: null });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('shows the document of the clicked help button under its title', async () => {
    const { user } = await renderPage();

    await user.click(helpButton('First Document'));

    const panel = await screen.findByRole('dialog', {
      name: 'First Document',
    });
    expect(panel).toHaveTextContent('The overview of the first document.');
    expect(screen.getByRole('heading', { name: 'Alpha' })).toBeInTheDocument();
    expect(panel).not.toHaveTextContent('{#alpha}');
    expect(helpButton('First Document')).toHaveAttribute(
      'aria-expanded',
      'true'
    );
  });

  it('scrolls to the section of the help button and highlights it', async () => {
    const scrollIntoView = vi.spyOn(Element.prototype, 'scrollIntoView');
    const { user } = await renderPage();

    await user.click(helpButton('Beta'));

    const beta = await screen.findByRole('heading', { name: 'Beta' });
    // The whole section is marked, from its heading to the next one.
    const betaSection = beta.closest('section');
    await waitFor(() =>
      expect(betaSection).toHaveAttribute('data-highlighted-section')
    );
    expect(scrollIntoView.mock.contexts).toContain(beta);
    expect(betaSection).toHaveTextContent('The beta section.');
    expect(
      screen.getByRole('heading', { name: 'Alpha' }).closest('section')
    ).not.toHaveAttribute('data-highlighted-section');

    await user.click(helpButton('Alpha'));

    await waitFor(() =>
      expect(
        screen.getByRole('heading', { name: 'Alpha' }).closest('section')
      ).toHaveAttribute('data-highlighted-section')
    );
    expect(betaSection).not.toHaveAttribute('data-highlighted-section');
  });

  it('scrolls back to the top for a help button without a section', async () => {
    const setScrollTop = vi.spyOn(Element.prototype, 'scrollTop', 'set');
    const { user } = await renderPage();

    await user.click(helpButton('Beta'));
    const betaSection = (
      await screen.findByRole('heading', { name: 'Beta' })
    ).closest('section');
    await waitFor(() =>
      expect(betaSection).toHaveAttribute('data-highlighted-section')
    );
    setScrollTop.mockClear();

    await user.click(helpButton('First Document'));

    await waitFor(() => expect(setScrollTop).toHaveBeenCalledWith(0));
    expect(betaSection).not.toHaveAttribute('data-highlighted-section');
  });

  it('swaps the document when a help button of another document is clicked', async () => {
    const { user } = await renderPage();

    await user.click(helpButton('First Document'));
    await screen.findByRole('dialog', { name: 'First Document' });
    await user.click(helpButton('Second Document'));

    expect(
      await screen.findByRole('dialog', { name: 'Second Document' })
    ).toHaveTextContent('The overview of the second document.');
  });

  it('shows the target of a link to another help document', async () => {
    const { user } = await renderPage();

    await user.click(helpButton('Second Document'));
    await screen.findByRole('dialog', { name: 'Second Document' });
    expect(screen.getByRole('link', { name: 'the website' })).toHaveAttribute(
      'href',
      'https://example.org'
    );
    await user.click(screen.getByRole('button', { name: 'the beta section' }));

    const panel = await screen.findByRole('dialog', { name: 'First Document' });
    const beta = await screen.findByRole('heading', { name: 'Beta' });
    await waitFor(() =>
      expect(beta.closest('section')).toHaveAttribute(
        'data-highlighted-section'
      )
    );
    expect(panel).toHaveFocus();
    expect(helpButton('Beta')).toHaveAttribute('aria-expanded', 'true');
  });

  it('closes with the help button of the shown topic', async () => {
    const { user } = await renderPage();

    await user.click(helpButton('First Document'));
    await screen.findByRole('dialog');
    await user.click(helpButton('First Document'));

    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    );
  });

  it('closes on Escape and returns the focus to the help button', async () => {
    const { user } = await renderPage();

    await user.click(helpButton('First Document'));
    await screen.findByRole('dialog');
    expect(screen.getByRole('button', { name: 'Close' })).toHaveFocus();
    await user.keyboard('{Escape}');

    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    );
    expect(helpButton('First Document')).toHaveFocus();
  });

  it('stays open while the user works on the page', async () => {
    const { user } = await renderPage();

    await user.click(helpButton('First Document'));
    await screen.findByRole('dialog');
    await user.type(screen.getByRole('textbox', { name: 'Field' }), 'value');

    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Field' })).toHaveValue('value');
  });

  it('lets the user tab back to the page while it stays open', async () => {
    const { user } = await renderPage();

    await user.click(helpButton('Alpha'));
    await screen.findByRole('dialog');
    expect(screen.getByRole('button', { name: 'Close' })).toHaveFocus();

    await user.tab();

    expect(helpButton('Alpha')).toHaveFocus();
    await user.tab();
    expect(helpButton('Beta')).toHaveFocus();
    expect(screen.getByRole('dialog')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Close' }));
    await user.click(helpButton('Alpha'));
    await screen.findByRole('dialog');
    await user.tab({ shift: true });

    expect(helpButton('Alpha')).toHaveFocus();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('closes on navigation to another page', async () => {
    const { user, router } = await renderPage();

    await user.click(helpButton('First Document'));
    await screen.findByRole('dialog');
    router.history.push('/other');

    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    );
  });
});
