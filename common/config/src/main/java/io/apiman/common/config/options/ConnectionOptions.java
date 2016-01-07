/*
 * Copyright 2015 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.apiman.common.config.options;

import java.util.Map;

/**
 * Options parser for connection settings.
 */
public class ConnectionOptions extends AbstractOptions {
    public static final String PREFIX = "connection."; //$NON-NLS-1$
    public static final String FOLLOW_REDIRECTS = PREFIX + "followRedirects"; //$NON-NLS-1$

    private boolean followRedirects;

    /**
     * Constructor. Parses options immediately.
     * @param options the options
     */
    public ConnectionOptions(Map<String, String> options) {
        super(options);
    }

    /**
     * @see AbstractOptions#parse(Map)
     */
    @Override
    protected void parse(Map<String, String> options) {
        setFollowRedirects(parseBool(options, FOLLOW_REDIRECTS, true));
    }

    /**
     * @return the followRedirects
     */
    public boolean isFollowRedirects() {
        return followRedirects;
    }

    /**
     * @param followRedirects the followRedirects to set
     */
    public void setFollowRedirects(boolean followRedirects) {
        this.followRedirects = followRedirects;
    }
}
