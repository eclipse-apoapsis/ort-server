#!/bin/bash
#
# Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# SPDX-License-Identifier: Apache-2.0
# License-Filename: LICENSE

# A script to support the synchronization between different containers if the Analyzer runs in multiple phases.
# The script expects the path to a file as single argument. It waits until this file is created and then returns.

if [ -z "$1" ]; then
    echo "Error: No target file path provided." >&2
    echo "Usage: $0 <target_file>" >&2
    exit 1
fi

TARGET_FILE=$1
echo "Waiting for file $TARGET_FILE."

while [ ! -f "$TARGET_FILE" ]; do
    sleep 5
done

echo "File $TARGET_FILE detected."
