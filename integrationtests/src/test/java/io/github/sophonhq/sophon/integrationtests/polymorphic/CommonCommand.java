/*
 * Copyright (c) 2010-2022. Axon Framework
 * Modifications Copyright (c) 2026. SophonHQ
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.sophonhq.sophon.integrationtests.polymorphic;

import io.github.sophonhq.sophon.modelling.command.TargetAggregateIdentifier;

/**
 * The common command for both first level children. Command handler for it is placed on abstract parent.
 *
 * @author Milan Savic
 */
public class CommonCommand {

    @TargetAggregateIdentifier
    private final String id;

    public CommonCommand(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
