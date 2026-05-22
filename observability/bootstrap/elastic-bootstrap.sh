#!/usr/bin/env bash
set -euo pipefail

ES_URL="${ES_URL:-http://elasticsearch:9200}"
KIBANA_URL="${KIBANA_URL:-http://kibana:5601}"
DATA_VIEW_ID="${DATA_VIEW_ID:-fopconverter-logs}"
DATA_VIEW_TITLE="${DATA_VIEW_TITLE:-fopconverter-logs-*}"

wait_for_http() {
  local url="$1"
  local name="$2"

  for _ in $(seq 1 120); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      echo "$name is ready"
      return 0
    fi
    sleep 2
  done

  echo "$name did not become ready in time" >&2
  return 1
}

echo "Waiting for Elasticsearch"
wait_for_http "$ES_URL" "Elasticsearch"

echo "Installing Elasticsearch index template"
curl -fsS -X PUT "$ES_URL/_index_template/fopconverter-logs" \
  -H 'Content-Type: application/json' \
  -d @- <<'JSON'
{
  "index_patterns": ["fopconverter-logs-*"] ,
  "priority": 500,
  "template": {
    "settings": {
      "number_of_shards": 1,
      "number_of_replicas": 0
    },
    "mappings": {
      "properties": {
        "@timestamp": { "type": "date" },
        "service_name": { "type": "keyword" },
        "service_role": { "type": "keyword" },
        "level": { "type": "keyword" },
        "thread_name": { "type": "keyword", "ignore_above": 256 },
        "logger_name": { "type": "keyword", "ignore_above": 1024 },
        "message": {
          "type": "text",
          "fields": {
            "keyword": { "type": "keyword", "ignore_above": 2048 }
          }
        },
        "event": {
          "properties": {
            "original": { "type": "wildcard" }
          }
        }
      }
    }
  }
}
JSON

echo "Updating mappings on existing log indices"
curl -fsS -X PUT "$ES_URL/fopconverter-logs-*/_mapping" \
  -H 'Content-Type: application/json' \
  -d @- <<'JSON' || true
{
  "properties": {
    "service_role": { "type": "keyword" }
  }
}
JSON

echo "Waiting for Kibana"
wait_for_http "$KIBANA_URL/api/status" "Kibana"

echo "Creating Kibana data view"
curl -fsS -X POST "$KIBANA_URL/api/data_views/data_view" \
  -H 'kbn-xsrf: bootstrap' \
  -H 'Content-Type: application/json' \
  -d @- <<JSON
{
  "data_view": {
    "id": "$DATA_VIEW_ID",
    "name": "FOP Converter Logs",
    "title": "$DATA_VIEW_TITLE",
    "timeFieldName": "@timestamp"
  },
  "override": true,
  "refresh_fields": true
}
JSON

echo "Setting default Kibana data view"
curl -fsS -X POST "$KIBANA_URL/api/data_views/default" \
  -H 'kbn-xsrf: bootstrap' \
  -H 'Content-Type: application/json' \
  -d @- <<JSON
{
  "data_view_id": "$DATA_VIEW_ID",
  "force": true
}
JSON

echo "Observability bootstrap completed"