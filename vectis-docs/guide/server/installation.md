# Installation du Serveur Vectis

Le serveur Vectis permet de recevoir des fichiers de partenaires externes. Il est conçu pour être déployé sur Kubernetes avec haute disponibilité.

## Déploiement recommandé : via vectis-admin

La méthode recommandée est d'utiliser la console d'administration qui gère automatiquement le déploiement Kubernetes.

### 1. Installer vectis-admin

```bash
docker-compose up -d
```

Avec `docker-compose.yml` :

```yaml
version: '3.8'

services:
  vectis-admin:
    image: ghcr.io/cpoder/vectis-admin:latest
    ports:
      - "9080:9080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/vectis
      SPRING_DATASOURCE_USERNAME: vectis
      SPRING_DATASOURCE_PASSWORD: vectis
    volumes:
      - ~/.kube:/root/.kube:ro  # Accès au cluster K8s

  vectis-admin-ui:
    image: ghcr.io/cpoder/vectis-admin-ui:latest
    ports:
      - "3000:80"

  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: vectis
      POSTGRES_USER: vectis
      POSTGRES_PASSWORD: vectis
    volumes:
      - postgres-data:/var/lib/postgresql/data

volumes:
  postgres-data:
```

### 2. Créer un cluster via l'interface

1. Accédez à http://localhost:3000
2. Cliquez sur **Ajouter un cluster**
3. Renseignez :
   - **Nom** : Production Vectis
   - **Namespace** : vectis-prod
   - **Environnement** : Production
   - **Replicas** : 3
   - **Image** : ghcr.io/cpoder/vectis-server:latest
4. Cliquez sur **Créer**
5. Cliquez sur **Déployer**

## Déploiement manuel : Docker

Pour un déploiement simple sans Kubernetes :

```bash
docker run -d \
  --name vectis-server \
  -p 5000:5000 \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/vectis \
  -e SPRING_DATASOURCE_USERNAME=vectis \
  -e SPRING_DATASOURCE_PASSWORD=vectis \
  -e VECTIS_CLUSTER_ENABLED=false \
  -v vectis-data:/data \
  ghcr.io/cpoder/vectis-server:latest
```

## Déploiement manuel : Kubernetes

### Créer le namespace

```bash
kubectl create namespace vectis
```

### Déployer PostgreSQL

```yaml
# postgres.yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: postgres-pvc
  namespace: vectis
spec:
  accessModes: [ReadWriteOnce]
  resources:
    requests:
      storage: 10Gi
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: postgres
  namespace: vectis
spec:
  replicas: 1
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
      - name: postgres
        image: postgres:16
        env:
        - name: POSTGRES_DB
          value: vectis
        - name: POSTGRES_USER
          value: vectis
        - name: POSTGRES_PASSWORD
          value: vectis
        volumeMounts:
        - name: data
          mountPath: /var/lib/postgresql/data
      volumes:
      - name: data
        persistentVolumeClaim:
          claimName: postgres-pvc
---
apiVersion: v1
kind: Service
metadata:
  name: postgres
  namespace: vectis
spec:
  ports:
  - port: 5432
  selector:
    app: postgres
```

### Déployer le serveur Vectis

```yaml
# vectis-server.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: vectis-server
  namespace: vectis
spec:
  replicas: 3
  selector:
    matchLabels:
      app: vectis-server
  template:
    metadata:
      labels:
        app: vectis-server
    spec:
      serviceAccountName: vectis-server
      containers:
      - name: vectis-server
        image: ghcr.io/cpoder/vectis-server:latest
        ports:
        - containerPort: 5000
          name: vectis
        - containerPort: 8080
          name: http
        env:
        - name: POD_NAME
          valueFrom:
            fieldRef:
              fieldPath: metadata.name
        - name: POD_NAMESPACE
          valueFrom:
            fieldRef:
              fieldPath: metadata.namespace
        - name: SPRING_DATASOURCE_URL
          value: jdbc:postgresql://postgres:5432/vectis
        - name: VECTIS_CLUSTER_ENABLED
          value: "true"
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
---
apiVersion: v1
kind: Service
metadata:
  name: vectis-server
  namespace: vectis
spec:
  type: LoadBalancer
  ports:
  - port: 5000
    name: vectis
  selector:
    app: vectis-server
    vectis-leader: "true"  # Route uniquement vers le leader
```

### RBAC pour le labeling

```yaml
# rbac.yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: vectis-server
  namespace: vectis
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: vectis-server
  namespace: vectis
rules:
- apiGroups: [""]
  resources: ["pods"]
  verbs: ["get", "list", "patch"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: vectis-server
  namespace: vectis
subjects:
- kind: ServiceAccount
  name: vectis-server
roleRef:
  kind: Role
  name: vectis-server
  apiGroup: rbac.authorization.k8s.io
```

### Appliquer

```bash
kubectl apply -f postgres.yaml
kubectl apply -f rbac.yaml
kubectl apply -f vectis-server.yaml
```

## Vérification

```bash
# Vérifier les pods
kubectl get pods -n vectis

# Vérifier le leader
kubectl get pods -n vectis -l vectis-leader=true

# Vérifier le service
kubectl get svc -n vectis

# Logs du leader
kubectl logs -n vectis -l vectis-leader=true
```

## Prochaines étapes

- [Configuration](/guide/server/configuration)
- [Clustering](/guide/server/clustering)
- [Sécurité](/guide/server/security)
