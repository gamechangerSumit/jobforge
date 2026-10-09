#!/usr/bin/env bash
# Idempotently creates the JobForge Kafka topics (ARCHITECTURE §14). Runs inside the kafka-init one-shot container.
# Topics: jobforge.<domain>.v1 and jobforge.<domain>.v1.dlq. Auto-create is disabled on the broker on purpose:
# topics are a contract; add new ones via a contract change + this list.
set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP_INTERNAL:-kafka:19092}"
PARTITIONS="${KAFKA_TOPIC_PARTITIONS:-3}"
REPLICATION="${KAFKA_TOPIC_REPLICATION:-1}"
RETENTION_MS="${KAFKA_TOPIC_RETENTION_MS:-604800000}"        # 7 days
DLQ_RETENTION_MS="${KAFKA_DLQ_RETENTION_MS:-1209600000}"     # 14 days
KT=/opt/kafka/bin/kafka-topics.sh

DOMAINS=(users jobs applications interviews community moderation ai audit)

create() {
  local topic="$1" retention="$2"
  "$KT" --bootstrap-server "$BOOTSTRAP" --create --if-not-exists \
        --topic "$topic" --partitions "$PARTITIONS" --replication-factor "$REPLICATION" \
        --config "retention.ms=${retention}"
}

for d in "${DOMAINS[@]}"; do
  create "jobforge.${d}.v1" "$RETENTION_MS"
  create "jobforge.${d}.v1.dlq" "$DLQ_RETENTION_MS"
done

echo "Topics ready:"
"$KT" --bootstrap-server "$BOOTSTRAP" --list | grep '^jobforge\.' | sort
