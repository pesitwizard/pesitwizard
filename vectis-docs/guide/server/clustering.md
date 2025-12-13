# Clustering et Haute Disponibilité

## Architecture

Le serveur Vectis supporte le clustering pour la haute disponibilité :

```
┌─────────────────────────────────────────────────────────┐
│                   LoadBalancer                          │
│              (selector: vectis-leader=true)              │
└─────────────────────┬───────────────────────────────────┘
                      │
        ┌─────────────┼─────────────┐
        │             │             │
        ▼             ▼             ▼
   ┌─────────┐   ┌─────────┐   ┌─────────┐
   │  Pod 1  │   │  Pod 2  │   │  Pod 3  │
   │ LEADER  │   │ Standby │   │ Standby │
   │ ✓       │   │         │   │         │
   └─────────┘   └─────────┘   └─────────┘
        │             │             │
        └─────────────┼─────────────┘
                      │
                      ▼
              ┌──────────────┐
              │  PostgreSQL  │
              │  (partagé)   │
              └──────────────┘
```

## Fonctionnement

### Élection de leader

- Utilise **JGroups** pour la découverte et l'élection
- Le premier pod à rejoindre le cluster devient leader
- En cas de perte du leader, un nouveau est élu automatiquement

### Labeling Kubernetes

Le pod leader est automatiquement labellisé `vectis-leader=true` :

```bash
# Voir le leader actuel
kubectl get pods -l vectis-leader=true

# Voir tous les pods avec leurs labels
kubectl get pods --show-labels
```

### Routage du trafic

Le Service Kubernetes utilise un selector pour router uniquement vers le leader :

```yaml
spec:
  selector:
    app: vectis-server
    vectis-leader: "true"
```

## Configuration

### Activer le clustering

```yaml
vectis:
  cluster:
    enabled: true
    name: vectis-cluster
```

### Variables d'environnement requises

```yaml
env:
- name: POD_NAME
  valueFrom:
    fieldRef:
      fieldPath: metadata.name
- name: POD_NAMESPACE
  valueFrom:
    fieldRef:
      fieldPath: metadata.namespace
- name: VECTIS_CLUSTER_ENABLED
  value: "true"
```

### RBAC requis

Le ServiceAccount doit pouvoir modifier les labels des pods :

```yaml
rules:
- apiGroups: [""]
  resources: ["pods"]
  verbs: ["get", "list", "patch"]
```

## Comportement en cas de failover

### Scénario : Le leader tombe

1. JGroups détecte la perte du leader (timeout ~10s)
2. Un nouveau leader est élu parmi les pods restants
3. Le nouveau leader :
   - Ajoute le label `vectis-leader=true` à son pod
   - Démarre les serveurs PeSIT configurés
4. Le LoadBalancer route vers le nouveau leader
5. Les connexions en cours sont perdues (les clients doivent reconnecter)

### Scénario : Un pod standby tombe

1. JGroups détecte la perte du membre
2. Le cluster continue de fonctionner normalement
3. Kubernetes recrée le pod automatiquement
4. Le nouveau pod rejoint le cluster en standby

## Monitoring du cluster

### API de statut

```bash
curl http://localhost:8080/api/cluster/status -u admin:admin
```

Réponse :
```json
{
  "clusterName": "vectis-cluster",
  "isLeader": true,
  "members": [
    {
      "name": "vectis-server-abc123",
      "address": "10.42.0.100",
      "isLeader": true
    },
    {
      "name": "vectis-server-def456",
      "address": "10.42.0.101",
      "isLeader": false
    },
    {
      "name": "vectis-server-ghi789",
      "address": "10.42.0.102",
      "isLeader": false
    }
  ],
  "memberCount": 3
}
```

### Logs de clustering

```bash
# Voir les logs du leader
kubectl logs -l vectis-leader=true -f

# Filtrer les logs JGroups
kubectl logs -l app=vectis-server | grep -i "cluster\|leader\|jgroups"
```

### Métriques

- `vectis_cluster_members` : Nombre de membres du cluster
- `vectis_cluster_is_leader` : 1 si ce pod est leader, 0 sinon

## Bonnes pratiques

### Nombre de replicas

| Environnement | Replicas | Justification |
|---------------|----------|---------------|
| Dev/Test | 1 | Pas besoin de HA |
| Staging | 2 | Test du failover |
| Production | 3 | Tolérance à 1 panne |
| Production critique | 5 | Tolérance à 2 pannes |

### Anti-affinité

Répartir les pods sur différents nœuds :

```yaml
spec:
  affinity:
    podAntiAffinity:
      preferredDuringSchedulingIgnoredDuringExecution:
      - weight: 100
        podAffinityTerm:
          labelSelector:
            matchLabels:
              app: vectis-server
          topologyKey: kubernetes.io/hostname
```

### PodDisruptionBudget

Garantir un minimum de pods disponibles :

```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: vectis-server-pdb
spec:
  minAvailable: 2
  selector:
    matchLabels:
      app: vectis-server
```
