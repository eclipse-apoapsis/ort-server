/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

-- This migration script adds cascade deletions on  scan_results table and it's children tables.

ALTER TABLE snippet_findings
    DROP CONSTRAINT snippet_findings_scan_summary_id_fkey,
  ADD CONSTRAINT snippet_findings_scan_summary_id_fkey
    FOREIGN KEY (scan_summary_id) REFERENCES scan_summaries (id) ON DELETE CASCADE;

ALTER TABLE snippet_findings_snippets
    DROP CONSTRAINT snippet_findings_snippets_snippet_finding_id_fkey,
  ADD CONSTRAINT snippet_findings_snippets_snippet_finding_id_fkey
    FOREIGN KEY (snippet_finding_id) REFERENCES snippet_findings (id) ON DELETE CASCADE;

ALTER TABLE license_findings
    DROP CONSTRAINT license_findings_scan_summary_id_fkey,
  ADD CONSTRAINT license_findings_scan_summary_id_fkey
    FOREIGN KEY (scan_summary_id) REFERENCES scan_summaries (id) ON DELETE CASCADE;

ALTER TABLE copyright_findings
    DROP CONSTRAINT copyright_findings_scan_summary_id_fkey,
  ADD CONSTRAINT copyright_findings_scan_summary_id_fkey
    FOREIGN KEY (scan_summary_id) REFERENCES scan_summaries (id) ON DELETE CASCADE;
