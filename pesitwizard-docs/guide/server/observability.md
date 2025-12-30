# Observabilité

PeSIT Wizard intègre des fonctionnalités d'observabilité complètes : métriques, tracing et logging.

## Métriques Prometheus

### Configuration

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: pesitwizard
      environment: ${ENVIRONMENT:production}
```

### Endpoint

Les métriques sont exposées sur : `http://localhost:8080/actuator/prometheus`

### Métriques disponibles

| Métrique | Type | Description |
|----------|------|-------------|
| `pesit_transfers_total` | Counter | Nombre total de transferts |
| `pesit_transfers_bytes_total` | Counter | Volume total transféré (bytes) |
| `pesit_transfer_duration_seconds` | Histogram | Durée des transferts |
| `pesit_active_connections` | Gauge | Connexions PeSIT actives |
| `pesit_partners_total` | Gauge | Nombre de partenaires configurés |

### ServiceMonitor Kubernetes

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: pesitwizard
  labels:
    release: prometheus
spec:
  selector:
    matchLabels:
      app: pesitwizard-server
  endpoints:
    - port: http
      path: /actuator/prometheus
      interval: 30s
```

### Dashboard Grafana

Importez le dashboard ID `XXXXX` ou utilisez le fichier `grafana-dashboard.json` :

```json
{
  "title": "PeSIT Wizard",
  "panels": [
    {
      "title": "Transferts par minute",
      "targets": [
        {
          "expr": "rate(pesit_transfers_total[5m])"
        }
      ]
    }
  ]
}
```

## Tracing OpenTelemetry

### Configuration

```yaml
management:
  tracing:
    enabled: true
    sampling:
      probability: 1.0  # 100% des traces (réduire en production)

otel:
  exporter:
    otlp:
      endpoint: http://jaeger-collector:4317
  service:
    name: pesitwizard-server
```

### Variables d'environnement

```bash
OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger:4317
OTEL_SERVICE_NAME=pesitwizard-server
OTEL_TRACES_SAMPLER=parentbased_traceidratio
OTEL_TRACES_SAMPLER_ARG=0.1  # 10% sampling
```

### Jaeger

Déployer Jaeger pour visualiser les traces :

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: jaeger
spec:
  template:
    spec:
      containers:
        - name: jaeger
          image: jaegertracing/all-in-one:1.52
          ports:
            - containerPort: 16686  # UI
            - containerPort: 4317   # OTLP gRPC
            - containerPort: 4318   # OTLP HTTP
```

### Spans personnalisés

Les transferts PeSIT génèrent automatiquement des spans :

```
pesit-transfer (root span)
├── pesit-connection
│   ├── pesit-handshake
│   └── pesit-auth
├── pesit-file-transfer
│   ├── pesit-read-chunk (repeated)
│   └── pesit-write-chunk (repeated)
└── pesit-disconnect
```

## Logging structuré

### Configuration Logback

```xml
<!-- logback-spring.xml -->
<configuration>
  <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
      <includeMdcKeyName>traceId</includeMdcKeyName>
      <includeMdcKeyName>spanId</includeMdcKeyName>
      <includeMdcKeyName>partnerId</includeMdcKeyName>
      <includeMdcKeyName>transferId</includeMdcKeyName>
    </encoder>
  </appender>
  
  <root level="INFO">
    <appender-ref ref="JSON"/>
  </root>
</configuration>
```

### Format JSON

```json
{
  "@timestamp": "2024-01-15T10:30:00.000Z",
  "level": "INFO",
  "logger": "c.p.server.PesitTransferHandler",
  "message": "Transfer completed",
  "traceId": "abc123",
  "spanId": "def456",
  "partnerId": "BANK01",
  "transferId": "tx-789",
  "bytesTransferred": 1048576,
  "durationMs": 1234
}
```

### Loki / ELK

Collectez les logs avec Promtail/Loki ou Filebeat/Elasticsearch :

```yaml
# Promtail config
scrape_configs:
  - job_name: pesitwizard
    kubernetes_sd_configs:
      - role: pod
    relabel_configs:
      - source_labels: [__meta_kubernetes_pod_label_app]
        regex: pesitwizard.*
        action: keep
```

## Health Checks

### Endpoints

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | État global |
| `/actuator/health/liveness` | Liveness probe (Kubernetes) |
| `/actuator/health/readiness` | Readiness probe (Kubernetes) |

### Configuration

```yaml
management:
  endpoint:
    health:
      show-details: when_authorized
      probes:
        enabled: true
  health:
    db:
      enabled: true
    diskspace:
      enabled: true
      threshold: 100MB
```

### Kubernetes Probes

```yaml
spec:
  containers:
    - name: pesitwizard
      livenessProbe:
        httpGet:
          path: /actuator/health/liveness
          port: 8080
        initialDelaySeconds: 30
        periodSeconds: 10
      readinessProbe:
        httpGet:
          path: /actuator/health/readiness
          port: 8080
        initialDelaySeconds: 10
        periodSeconds: 5
```

## Alerting

### Règles Prometheus

```yaml
groups:
  - name: pesitwizard
    rules:
      - alert: PesitwizardDown
        expr: up{job="pesitwizard"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "PeSIT Wizard is down"
          
      - alert: HighTransferErrorRate
        expr: rate(pesit_transfers_total{status="error"}[5m]) > 0.1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High transfer error rate"
          
      - alert: SlowTransfers
        expr: histogram_quantile(0.95, rate(pesit_transfer_duration_seconds_bucket[5m])) > 60
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Transfers are slow (p95 > 60s)"
```

## Stack complète

Pour une observabilité complète, déployez :

```bash
# Prometheus + Grafana
helm install prometheus prometheus-community/kube-prometheus-stack

# Jaeger
helm install jaeger jaegertracing/jaeger

# Loki
helm install loki grafana/loki-stack
```
