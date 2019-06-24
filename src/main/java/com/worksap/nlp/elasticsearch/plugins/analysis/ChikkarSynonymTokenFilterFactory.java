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

package com.worksap.nlp.elasticsearch.plugins.analysis;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.AbstractTokenFilterFactory;
import org.elasticsearch.index.analysis.AnalysisRegistry;
import org.elasticsearch.index.analysis.TokenizerFactory;

import com.worksap.nlp.elasticsearch.plugins.chikkar.Chikkar;
import org.elasticsearch.indices.analysis.AnalysisModule;

public class ChikkarSynonymTokenFilterFactory extends AbstractTokenFilterFactory {

    private static final Logger log = LogManager.getLogger(ChikkarSynonymTokenFilterFactory.class);

    private final boolean ignoreCase;
    private final boolean restrictMode;
    private final List<String> dictList;
    private final String dictBinPath;
    protected final Settings settings;
    protected final Environment environment;
    private final ChikkarSynonymMap synonyms;

    /**
     * Constructor with argument
     *
     * @param indexSettings
     *            {@link IndexSettings} of elasticsearch server
     * @param env
     *            {@link Environment} of elasticsearch server
     * @param name
     *            Name of this token filter
     * @param settings
     *            {@link Settings} of this token filter
     * @param analysisRegistry
     *            {@link AnalysisRegistry} of elasticsearch server
     * @throws IOException
     *             throwS IOException if fail to create synonym token filter
     */
    public ChikkarSynonymTokenFilterFactory(IndexSettings indexSettings, Environment env, String name,
            Settings settings, AnalysisRegistry analysisRegistry) throws IOException {
        super(indexSettings, name, settings);

        // get the filter setting params
        this.ignoreCase = settings.getAsBoolean("ignore_case", false);
        this.restrictMode = settings.getAsBoolean("restrict_mode", false);
        this.dictList = Arrays.asList(settings.getAsArray("dict_list"));
        this.dictBinPath = settings.get("dict_bin_path");
        this.settings = settings;
        this.environment = env;

        String tokenizerName = settings.get("tokenizer", "whitespace");
        AnalysisModule.AnalysisProvider<TokenizerFactory> tokenizerFactoryFactory = analysisRegistry
                .getTokenizerProvider(tokenizerName, indexSettings);
        if (tokenizerFactoryFactory == null) {
            throw new IllegalArgumentException(
                    "failed to find tokenizer [" + tokenizerName + "] for " + "chikkar synonym token filter");
        }

        final TokenizerFactory tokenizerFactory = tokenizerFactoryFactory.get(indexSettings, env, tokenizerName,
                AnalysisRegistry.getSettingsFromIndexSettings(indexSettings,
                        AnalysisRegistry.INDEX_ANALYSIS_TOKENIZER + "." + tokenizerName));

        Analyzer analyzer = new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer tokenizer = tokenizerFactory == null ? new WhitespaceTokenizer() : tokenizerFactory.create();
                TokenStream stream = ignoreCase ? new LowerCaseFilter(tokenizer) : tokenizer;
                return new TokenStreamComponents(tokenizer, stream);
            }
        };

        this.synonyms = buildSynonyms(analyzer);
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        return synonyms.fst == null ? tokenStream : new ChikkarSynonymTokenFilter(tokenStream, synonyms, ignoreCase);
    }

    ChikkarSynonymMap buildSynonyms(Analyzer analyzer) {
        try {
            if (dictList.isEmpty() && dictBinPath == null) {
                log.error("Missing dict_list or dict_bin_path in settings. You need to set one of them in settings.");
                throw new IllegalArgumentException("Missing dict_list or dict_bin_path in settings. You need to set "
                        + "one of them in settings.");
            } else if (dictList.isEmpty()) {
                return ChikkarSynonymMap.read(environment.configFile().resolve(dictBinPath), analyzer);
            } else if (dictBinPath == null) {
                Chikkar chikkar = new Chikkar(analyzer, environment.configFile(), dictList, restrictMode);
                ChikkarSynonymMap.Builder builder = new ChikkarSynonymMap.Builder(true);
                return builder.build(chikkar);
            } else {
                log.error("both dict_list and dict_bin_path are set in settings. You can only set one of them "
                        + "in settings.");
                throw new IllegalArgumentException("both dict_list and dict_bin_path are set in settings. You can"
                        + " only set one of them in settings.");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to build synonyms", e);
        }
    }

}
