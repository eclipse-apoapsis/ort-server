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

type HighlightedMatchProps = {
  searchTerm: string;
  text: string;
};

export const HighlightedMatch = ({
  searchTerm,
  text,
}: HighlightedMatchProps) => {
  if (!searchTerm) {
    return text;
  }

  const normalizedText = text.toLowerCase();
  const matchIndex = normalizedText.indexOf(searchTerm);

  if (matchIndex < 0) {
    return text;
  }

  const prefix = text.slice(0, matchIndex);
  const match = text.slice(matchIndex, matchIndex + searchTerm.length);
  const suffix = text.slice(matchIndex + searchTerm.length);

  return (
    <>
      <span className='text-blue-400'>{prefix}</span>
      <span className='font-bold text-green-600'>{match}</span>
      <span className='text-blue-400'>{suffix}</span>
    </>
  );
};
