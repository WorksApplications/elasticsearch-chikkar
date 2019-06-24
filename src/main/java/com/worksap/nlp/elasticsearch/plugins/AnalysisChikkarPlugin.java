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

package com.worksap.nlp.elasticsearch.plugins;

import static java.util.Collections.singletonList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.AnalysisRegistry;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.indices.analysis.AnalysisModule.AnalysisProvider;
import org.elasticsearch.plugins.AnalysisPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.watcher.ResourceWatcherService;

import com.worksap.nlp.elasticsearch.plugins.analysis.ChikkarSynonymTokenFilterFactory;
import com.worksap.nlp.elasticsearch.plugins.service.ChikkarSynonymAnalysisService;

public class AnalysisChikkarPlugin extends Plugin implements AnalysisPlugin {

    private final PluginComponent pluginComponent = new PluginComponent();

    public static final String CHIKKAR_PLUGIN_NAME = "chikkar_synonym";

    @Override
    public Collection<Class<? extends LifecycleComponent>> getGuiceServiceClasses() {
        return singletonList(ChikkarSynonymAnalysisService.class);
    }

    @Override
    public Collection<Object> createComponents(Client client, ClusterService clusterService, ThreadPool threadPool,
            ResourceWatcherService resourceWatcherService, ScriptService scriptService,
            NamedXContentRegistry xContentRegistry) {
        final Collection<Object> components = new ArrayList<>();
        components.add(pluginComponent);
        return components;
    }

    @Override
    public Map<String, AnalysisProvider<TokenFilterFactory>> getTokenFilters() {
        final Map<String, AnalysisProvider<TokenFilterFactory>> tokenFilters = new HashMap<>();
        tokenFilters.put(CHIKKAR_PLUGIN_NAME, new AnalysisProvider<TokenFilterFactory>() {
            @Override
            public TokenFilterFactory get(final IndexSettings indexSettings, final Environment environment,
                    final String name, final Settings settings) throws IOException {
                return new ChikkarSynonymTokenFilterFactory(indexSettings, environment, name, settings,
                        pluginComponent.getAnalysisRegistry());
            }

            @Override
            public boolean requiresAnalysisSettings() {
                return true;
            }
        });
        return tokenFilters;
    }

    public static class PluginComponent {
        private AnalysisRegistry analysisRegistry;

        public AnalysisRegistry getAnalysisRegistry() {
            return analysisRegistry;
        }

        public void setAnalysisRegistry(final AnalysisRegistry analysisRegistry) {
            this.analysisRegistry = analysisRegistry;
        }
    }

}