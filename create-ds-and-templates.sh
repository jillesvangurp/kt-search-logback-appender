#!/usr/bin/env bash
set -euo pipefail

# === CONFIGURATION ===
ES_URL="http://localhost:9200"
PREFIX="applogs"
ILM_POLICY="${PREFIX}-ilm-policy"
TEMPLATE_SETTINGS="${PREFIX}-template-settings"
TEMPLATE_MAPPINGS="${PREFIX}-template-mappings"
INDEX_TEMPLATE="${PREFIX}-template"

# parameters
HOT_ROLLOVER_GB=5
HOT_MAX_AGE="1d"
REPLICAS=1
SHARDS=1
WARM_MIN_AGE="1d"
DELETE_MIN_AGE="7d"
WARM_SHRINK_SHARDS=1
WARM_SEGMENTS=1

# === HELPERS ===
curl_put() {
  curl -s -o /dev/null -w "%{http_code}" -X PUT "$1" -H "Content-Type: application/json" -d "$2"
}
curl_post() {
  curl -s -o /dev/null -w "%{http_code}" -X POST "$1" -H "Content-Type: application/json" -d "$2"
}

# === ILM POLICY ===
echo "Creating ILM policy $ILM_POLICY ..."
curl_put "$ES_URL/_ilm/policy/$ILM_POLICY" "{
  \"policy\": {
    \"phases\": {
      \"hot\": {
        \"actions\": {
          \"rollover\": {
            \"max_primary_shard_size\": \"${HOT_ROLLOVER_GB}gb\",
            \"max_age\": \"$HOT_MAX_AGE\"
          }
        }
      },
      \"warm\": {
        \"min_age\": \"$WARM_MIN_AGE\",
        \"actions\": {
          \"shrink\": {\"number_of_shards\": $WARM_SHRINK_SHARDS},
          \"forcemerge\": {\"max_num_segments\": $WARM_SEGMENTS}
        }
      },
      \"delete\": {
        \"min_age\": \"$DELETE_MIN_AGE\",
        \"actions\": {\"delete\": {}}
      }
    }
  }
}"

# === COMPONENT TEMPLATE: SETTINGS ===
echo "Creating component template $TEMPLATE_SETTINGS ..."
curl_put "$ES_URL/_component_template/$TEMPLATE_SETTINGS" "{
  \"template\": {
    \"settings\": {
      \"number_of_shards\": $SHARDS,
      \"number_of_replicas\": $REPLICAS,
      \"index.lifecycle.name\": \"$ILM_POLICY\"
    }
  },
  \"_meta\": {
    \"created_by\": \"bash-script\",
    \"created_at\": \"$(date -Iseconds)\"
  }
}"

# === COMPONENT TEMPLATE: MAPPINGS ===
echo "Creating component template $TEMPLATE_MAPPINGS ..."
curl_put "$ES_URL/_component_template/$TEMPLATE_MAPPINGS" "{
  \"template\": {
    \"mappings\": {
      \"dynamic_templates\": [
        {
          \"keywords\": {
            \"match_mapping_type\": \"string\",
            \"mapping\": {\"type\": \"keyword\", \"ignore_above\": 256}
          }
        }
      ],
      \"properties\": {
        \"@timestamp\": {\"type\": \"date\"},
        \"message\": {\"type\": \"text\", \"fields\": {\"keyword\": {\"type\": \"keyword\", \"ignore_above\": 256}}},
        \"thread\": {\"type\": \"keyword\"},
        \"level\": {\"type\": \"keyword\"},
        \"logger\": {\"type\": \"keyword\"},
        \"contextName\": {\"type\": \"keyword\"},
        \"mdc\": {\"type\": \"object\", \"dynamic\": true},
        \"context\": {\"type\": \"object\", \"dynamic\": true},
        \"exceptionList\": {
          \"properties\": {
            \"className\": {\"type\": \"keyword\", \"ignore_above\": 256},
            \"message\": {\"type\": \"text\"}
          }
        }
      }
    }
  },
  \"_meta\": {
    \"created_by\": \"bash-script\",
    \"created_at\": \"$(date -Iseconds)\"
  }
}"

# === INDEX TEMPLATE ===
echo "Creating index template $INDEX_TEMPLATE ..."
curl_put "$ES_URL/_index_template/$INDEX_TEMPLATE" "{
  \"index_patterns\": [\"${PREFIX}*\"],
  \"data_stream\": {},
  \"priority\": 300,
  \"composed_of\": [\"$TEMPLATE_SETTINGS\", \"$TEMPLATE_MAPPINGS\"]
}"

# === CREATE DATA STREAM ===
echo "Creating data stream $PREFIX ..."
curl_post "$ES_URL/_data_stream/$PREFIX" "{}"

echo "Done. Data stream '$PREFIX' created successfully."

# === VERIFY DEPLOYMENT ===
echo
echo "=== VERIFYING DEPLOYED RESOURCES ==="

echo
echo "--- ILM Policy ($ILM_POLICY) ---"
curl -s "$ES_URL/_ilm/policy/$ILM_POLICY?pretty" | jq .

echo
echo "--- Component Template: Settings ($TEMPLATE_SETTINGS) ---"
curl -s "$ES_URL/_component_template/$TEMPLATE_SETTINGS?pretty" | jq .

echo
echo "--- Component Template: Mappings ($TEMPLATE_MAPPINGS) ---"
curl -s "$ES_URL/_component_template/$TEMPLATE_MAPPINGS?pretty" | jq .

echo
echo "--- Index Template ($INDEX_TEMPLATE) ---"
curl -s "$ES_URL/_index_template/$INDEX_TEMPLATE?pretty" | jq .

echo
echo "--- Data Streams Matching '$PREFIX*' ---"
curl -s "$ES_URL/_data_stream/$PREFIX*?pretty" | jq .

echo
echo "Verification complete."