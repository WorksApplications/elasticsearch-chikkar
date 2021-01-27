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

import java.util.HashMap;
import java.util.Map;

import com.worksap.nlp.elasticsearch.plugins.analysis.ChikkarSynonymTokenFilterFactory;
import com.worksap.nlp.elasticsearch.plugins.analysis.ChikkarSynonymGraphTokenFilterFactory;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.indices.analysis.AnalysisModule;
import org.elasticsearch.plugins.AnalysisPlugin;
import org.elasticsearch.plugins.Plugin;

public class AnalysisChikkarPlugin extends Plugin implements AnalysisPlugin {

    public static final String SYNONYM_FILTER_NAME = "chikkar_synonym";
    public static final String SYNONYM_GRAPH_FILTER_NAME = "chikkar_synonym_graph";

    @Override
    public Map<String, AnalysisModule.AnalysisProvider<TokenFilterFactory>> getTokenFilters() {
        Map<String, AnalysisModule.AnalysisProvider<TokenFilterFactory>> tokenFilters = new HashMap<>();
        tokenFilters.put(SYNONYM_FILTER_NAME, ChikkarSynonymTokenFilterFactory::new);
        tokenFilters.put(SYNONYM_GRAPH_FILTER_NAME, ChikkarSynonymGraphTokenFilterFactory::new);
        return tokenFilters;
    }
}