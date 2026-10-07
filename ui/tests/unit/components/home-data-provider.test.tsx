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

import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  HomeDataProvider,
  useHomeDataProvider,
  useHomeFavoriteActions,
  useHomeFavorites,
} from '@/providers/home-data';
import { useFavoritesStore } from '@/store/favorites.store';
import { isCompiledByReactCompiler } from '../fixtures/react-compiler';

const auth = vi.hoisted(() => ({ userId: 'user-a' }));

vi.mock('react-oidc-context', () => ({
  useAuth: () => ({ user: { profile: { sub: auth.userId } } }),
}));

const FavoritesConsumer = () => {
  const favorites = useHomeFavorites();
  const { addFavorite } = useHomeFavoriteActions();

  return (
    <>
      <ul>
        {favorites.map((favorite) => (
          <li key={favorite.id}>{favorite.name}</li>
        ))}
      </ul>
      <button
        onClick={() =>
          addFavorite({
            id: 'organization-1',
            type: 'organization',
            name: 'Example org',
            breadcrumbs: [],
            to: '/organizations/$orgId',
            params: { orgId: '1' },
          })
        }
      >
        Add favorite
      </button>
    </>
  );
};

const tree = () => (
  <HomeDataProvider>
    <FavoritesConsumer />
  </HomeDataProvider>
);

describe('HomeDataProvider', () => {
  afterEach(() => {
    auth.userId = 'user-a';
    act(() =>
      useFavoritesStore.setState({
        favoritesByUser: {},
        favoriteGroupOrderByUser: {},
      })
    );
    localStorage.clear();
  });

  it('is compiled by React Compiler, unlike the home data hooks', () => {
    expect(isCompiledByReactCompiler(HomeDataProvider)).toBe(true);
    expect(isCompiledByReactCompiler(useHomeFavorites)).toBe(false);
  });

  it('passes the favorites of the current user to its consumers', async () => {
    const user = userEvent.setup();
    const { rerender } = render(tree());

    await user.click(screen.getByRole('button', { name: 'Add favorite' }));

    expect(screen.getByRole('listitem')).toHaveTextContent('Example org');

    auth.userId = 'user-b';
    rerender(tree());

    expect(screen.queryByRole('listitem')).toBeNull();

    auth.userId = 'user-a';
    rerender(tree());

    expect(screen.getByRole('listitem')).toHaveTextContent('Example org');
  });

  it('keeps the provider for a new profile object of the same user', () => {
    const providers: unknown[] = [];
    const ProviderConsumer = () => {
      providers.push(useHomeDataProvider());
      return null;
    };
    const providerTree = () => (
      <HomeDataProvider>
        <ProviderConsumer />
      </HomeDataProvider>
    );

    // The mocked `useAuth` returns a new profile object on every render.
    const { rerender } = render(providerTree());
    rerender(providerTree());

    expect(providers).toHaveLength(2);
    expect(providers[1]).toBe(providers[0]);
  });
});
