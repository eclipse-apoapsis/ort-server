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

import { ProvenanceFile } from '@/api';

/** Human-readable labels for the kinds of provenances. */
export const provenanceFileKindLabels: Record<ProvenanceFile['kind'], string> =
  {
    SOURCE_ARTIFACT: 'Source artifact',
    VCS: 'VCS',
    UNKNOWN: 'Unknown',
  };

/**
 * Return the file name from the given value of a Content-Disposition header, or undefined if it is not a string or
 * does not contain a file name.
 */
export const fileNameFromContentDisposition = (
  contentDisposition: unknown
): string | undefined => {
  if (typeof contentDisposition !== 'string') return undefined;

  const fileName = contentDisposition
    .split(';')
    .map((part) => part.trim())
    .find((part) => part.startsWith('filename='))
    ?.substring('filename='.length)
    .replace(/^"(.*)"$/, '$1');

  return fileName || undefined;
};

/** Let the browser save the given data as a file with the given name. */
export const saveFile = (data: Blob, fileName: string) => {
  const url = window.URL.createObjectURL(data);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  a.click();
  window.URL.revokeObjectURL(url);
};
