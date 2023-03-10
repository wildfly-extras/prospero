/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.prospero.cli;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ArgumentParsingException extends Exception {

    private List<String> details = Collections.emptyList();

    public ArgumentParsingException(String msg, Exception e) {
        super(msg, e);
    }

    public ArgumentParsingException(String msg) {
        super(msg);
    }

    public ArgumentParsingException(String msg, String... details) {
        super(msg);
        Objects.requireNonNull(details);
        this.details = Arrays.asList(details);
    }

    public List<String> getDetails() {
        return details;
    }
}
