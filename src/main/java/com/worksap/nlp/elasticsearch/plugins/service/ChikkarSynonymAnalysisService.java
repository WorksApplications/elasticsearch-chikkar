/*
 * Copyright (c) 2019 Works Applications Co., Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.worksap.nlp.elasticsearch.plugins.service;

import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.analysis.AnalysisRegistry;

import com.worksap.nlp.elasticsearch.plugins.AnalysisChikkarPlugin;

public class ChikkarSynonymAnalysisService extends AbstractLifecycleComponent {

    @Inject
    public ChikkarSynonymAnalysisService(final Settings settings, final AnalysisRegistry analysisRegistry,
            final AnalysisChikkarPlugin.PluginComponent pluginComponent) {
        super(settings);
        pluginComponent.setAnalysisRegistry(analysisRegistry);
    }

    @Override
    protected void doStart() {
        // nothing
    }

    @Override
    protected void doStop() {
        // nothing
    }

    @Override
    protected void doClose() {
        // nothing
    }

}
