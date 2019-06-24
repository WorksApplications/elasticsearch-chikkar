# elasticsearch-chikkar
A synonym token filter plugin for Elasticsearch

* For `ES 5.X` and `ES 6.0`, the highlighting functions may not work as expected when using this synonym token filter. The reason is that these versions of Elasticsearch don't have a feature [auto_generate_synonyms_phrase_query ](https://github.com/elastic/elasticsearch/pull/26097). It is the problem of Elasticsearch which is beyond control of this plugin.

* For `ES 6.1` and higher versions, this `auto_generate_synonyms_phrase_query` featured are merged to Elasticsearch, so the highlighting functions can work well. It is recommended that this plugin is used in `ES 6.1` or higher versions.

# usage example:
suppose we use `Sudachi` for tokenizer.

## use synonym dictionaries in text format

* Pass `chikkar_synonym` for the type of chikkar plugin.
* Set `restrict_mode` to true to ignore directed synonyms. Default value is false.
* Set `dict_list` with a list of synonym dictionary paths. The paths should be relative path to the ES config folder, as ES can only access files under config folder.

```
{
  "settings": {
    "index": {
      "analysis": {
        "tokenizer": {
          "sudachi_tokenizer": {
            "type": "sudachi_tokenizer",
            "mode": "search",
            "discard_punctuation": true,
            "resources_path": "sudachi_tokenizer"
          }
        },
        "filter" : {
            "chikkar_filter" : {
                "type" : "chikkar_synonym",
                "restrict_mode" : false,
                "dict_list" : ["chikkar/synonym1.txt", "chikkar/synonym2.txt", "chikkar/synonym3.txt"]
            }
        },
        "analyzer": {
          "sudachi_analyzer_no_synonym": {
            "filter": ["sudachi_normalizedform"],
            "tokenizer": "sudachi_tokenizer",
            "type": "custom"
          },
          "sudachi_analyzer_chikkar_synonym": {
            "filter": [
              "sudachi_normalizedform",
              "chikkar_filter"
            ],
            "tokenizer": "sudachi_tokenizer",
            "type": "custom"
          }
        }
      }
    }
  },
  "mappings": {
    "doc": {
        "properties": {
            "content": {
                "type": "text",
                "analyzer": "sudachi_analyzer_no_synonym",
                "search_analyzer": "sudachi_analyzer_chikkar_synonym",
                "term_vector": "with_positions_offsets"
            }
        }
    }
  }
}
```

## use built synonym dictionaries in binary format

For big dictionaries, it is much faster using the pre-built binary format of them to load.

* Pass `chikkar_synonym` for the type of chikkar plugin.
* Set `dict_bin_path` with relative path of the binary file. The path should be relative path to the ES config folder, as ES can only access files under config folder.
* Note that current implementation don't allow to set `restrict_mode` for binary dictionary. If user set `restrict_mode` for binary dictionary, it will not take effect.

```
{
  "settings": {
    "index": {
      "analysis": {
        "tokenizer": {
          "sudachi_tokenizer": {
            "type": "sudachi_tokenizer",
            "mode": "search",
            "discard_punctuation": true,
            "resources_path": "sudachi_tokenizer"
          }
        },
        "filter" : {
            "chikkar_filter" : {
                "type" : "chikkar_synonym",
                "dict_bin_path" : "chikkar/synonym.bin"
            }
        },
        "analyzer": {
          "sudachi_analyzer_no_synonym": {
            "filter": ["sudachi_normalizedform"],
            "tokenizer": "sudachi_tokenizer",
            "type": "custom"
          },
          "sudachi_analyzer_chikkar_synonym": {
            "filter": [
              "sudachi_normalizedform",
              "chikkar_filter"
            ],
            "tokenizer": "sudachi_tokenizer",
            "type": "custom"
          }
        }
      }
    }
  },
  "mappings": {
    "doc": {
        "properties": {
            "content": {
                "type": "text",
                "analyzer": "sudachi_analyzer_no_synonym",
                "search_analyzer": "sudachi_analyzer_chikkar_synonym",
                "term_vector": "with_positions_offsets"
            }
        }
    }
  }
}
```
