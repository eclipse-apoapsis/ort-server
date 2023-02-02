/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.transport.kubernetes

import com.typesafe.config.Config

/**
 * A configuration class used by the Kubernetes Transport implementation.
 */
data class KubernetesConfig(
    /** The namespace inside the Kubernetes Cluster. */
    val namespace: String,

    /** The image name for the container that will run in the Pod. */
    val imageName: String
) {
    companion object {
        /**
         * The name of this transport implementation, which will be used in the message sender and receiver factories.
         */
        const val TRANSPORT_NAME = "kubernetes"

        /** The name of the configuration property for the Kubernetes namespace. */
        private const val NAMESPACE_PROPERTY = "namespace"

        /** The name of the configuration property for the container image name. */
        private const val IMAGE_NAME_PROPERTY = "imageName"

        /**
         * Create a [KubernetesConfig] from the provided [config].
         */
        fun createConfig(config: Config) =
            KubernetesConfig(
                namespace = config.getString(NAMESPACE_PROPERTY),
                imageName = config.getString(IMAGE_NAME_PROPERTY)
            )
    }
}
