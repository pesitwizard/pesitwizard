# Guide d'Intégration CX - PeSIT Server

## Vue d'ensemble

Ce guide décrit comment tester l'interopérabilité entre le serveur PeSIT (pesitwizard-server) et IBM Sterling Connect:Express (CX) en tant que client PeSIT.

---

## Prérequis

### ✅ Serveur PeSIT
- **Status:** ✅ Implémenté et testé (897 tests passent)
- **Conformité:** 95% PeSIT-E
- **Location:** `/home/cpo/pesitwizard/pesitwizard-server`
- **Port par défaut:** 6502
- **Profile:** Hors-SIT

### ✅ IBM CX
- **Version:** Sterling Connect:Express for Unix V15.0
- **Installation:** `/home/cpo/cexp`
- **Status:** Installé, non démarré
- **Configuration:** `/home/cpo/cexp/config/`

---

## Étape 1: Démarrage du Serveur PeSIT

### Configuration Minimale

Créer un fichier de configuration : `/home/cpo/pesitwizard/pesitwizard-server/config/application-cx-test.yml`

```yaml
pesit:
  server:
    enabled: true
    server-id: PESITSERVER
    port: 6502
    max-connections: 10
    strict-partner-check: false  # Allow any partner for initial testing
    protocol-version: 2

spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop

logging:
  level:
    com.pesitwizard: DEBUG
```

### Démarrer le serveur

```bash
cd /home/cpo/pesitwizard/pesitwizard-server

# Option 1: Avec Maven
mvn spring-boot:run -Dspring-boot.run.profiles=cx-test

# Option 2: Avec le JAR
mvn clean package -DskipTests
java -jar target/pesitwizard-server-1.0.0-SNAPSHOT.jar --spring.profiles.active=cx-test
```

### Vérifier le serveur

```bash
# Health check
curl http://localhost:8080/actuator/health

# Vérifier le port PeSIT
netstat -an | grep 6502
# Devrait montrer: tcp6       0      0 :::6502      :::*       LISTEN
```

---

## Étape 2: Configuration CX

### 2.1 Démarrer CX

```bash
cd /home/cpo/cexp
source profile
$start_tom

# Vérifier que CX est démarré
ps aux | grep tom_mon
```

### 2.2 Accéder au Terminal CX

```bash
# Ouvrir le terminal CX
$sterm

# Ou en mode commande
$sterm_c
```

### 2.3 Configurer le Partenaire PeSIT

Dans le terminal CX, créer un partenaire:

```
# Syntaxe CX pour ajouter un partenaire PeSIT
# Note: La syntaxe exacte dépend de la version de CX

# 1. Définir le partenaire
ADD PARTENAIRE PESITSERVER
  NOM_COMPLET="PeSIT Server Test"
  TYPE=PESIT
  PROTOCOLE=PESIT-E

# 2. Définir le serveur distant
ADD SERVEUR PESITSERVER_SRV
  PARTENAIRE=PESITSERVER
  HOTE=localhost
  PORT=6502
  PROTOCOLE=TCP

# 3. Définir les identifiants
ADD IDENTITE PESITSERVER_ID
  PARTENAIRE=PESITSERVER
  UTILISATEUR=CXCLIENT
  MOT_DE_PASSE=test123
```

### 2.4 Configuration Alternative via Fichiers

Si le terminal CX n'est pas disponible, éditer directement les fichiers de configuration:

```bash
# Sauvegarder la configuration actuelle
cd /home/cpo/cexp/config
cp RPAR.dat RPAR.dat.backup
cp RENC.dat RENC.dat.backup
```

**Note:** CX utilise des fichiers binaires. Il est recommandé d'utiliser les utilitaires CX:

```bash
# Utiliser l'utilitaire de configuration CX
cd /home/cpo/cexp/config
./tom_prm

# Ou utiliser le script de configuration
./ch_conf
```

---

## Étape 3: Configuration du Serveur PeSIT

### 3.1 Créer un Partenaire pour CX

Via l'API REST du serveur:

```bash
# Créer le partenaire CXCLIENT
curl -X POST http://localhost:8080/api/v1/partners \
  -H "Content-Type: application/json" \
  -d '{
    "id": "CXCLIENT",
    "description": "IBM CX Client for testing",
    "password": "test123",
    "enabled": true,
    "accessType": "READ_WRITE",
    "maxConnections": 5
  }'
```

### 3.2 Créer des Fichiers Virtuels

```bash
# Fichier pour réception (CX -> Server)
curl -X POST http://localhost:8080/api/v1/virtual-files \
  -H "Content-Type: application/json" \
  -d '{
    "virtualName": "TEST_RECEIVE",
    "physicalPath": "/tmp/pesit-received/${PARTNER}/${FILE}",
    "direction": "RECEIVE",
    "partnerId": "CXCLIENT",
    "enabled": true
  }'

# Fichier pour envoi (Server -> CX)
curl -X POST http://localhost:8080/api/v1/virtual-files \
  -H "Content-Type: application/json" \
  -d '{
    "virtualName": "TEST_SEND",
    "physicalPath": "/tmp/pesit-send/testfile.dat",
    "direction": "SEND",
    "partnerId": "CXCLIENT",
    "enabled": true
  }'

# Créer les répertoires
mkdir -p /tmp/pesit-received/CXCLIENT
mkdir -p /tmp/pesit-send

# Créer un fichier de test
dd if=/dev/urandom of=/tmp/pesit-send/testfile.dat bs=1M count=5
```

---

## Étape 4: Tests d'Intégration

### Test 1: Connexion Simple

#### Depuis CX
```bash
# Dans CX, tester la connexion
# (Syntaxe dépend de la version CX)
TEST CONNEXION PESITSERVER
```

#### Vérifier dans les logs du serveur
```bash
tail -f /home/cpo/pesitwizard/pesitwizard-server/logs/application.log | grep -i connect
```

Vous devriez voir:
```
INFO  [SessionHandler] New session created from 127.0.0.1 (server: PESITSERVER)
INFO  [ConnectionValidator] Partner 'CXCLIENT' validated successfully
INFO  [SessionHandler] CONNECT accepted for partner CXCLIENT
```

### Test 2: Envoi de Fichier (CX -> Serveur)

#### Préparer le fichier dans CX
```bash
# Créer un fichier test dans le répertoire d'envoi CX
echo "Test content from CX" > /home/cpo/cexp/out/test_to_server.txt
```

#### Lancer le transfert depuis CX
```bash
# Commande CX pour envoyer le fichier
# (Syntaxe à adapter selon votre configuration CX)
SEND FICHIER test_to_server.txt
  PARTENAIRE=PESITSERVER
  FICHIER_DISTANT=TEST_RECEIVE
```

#### Vérifier la réception
```bash
# Le fichier devrait apparaître dans le répertoire configuré
ls -lh /tmp/pesit-received/CXCLIENT/
cat /tmp/pesit-received/CXCLIENT/test_to_server.txt
```

### Test 3: Réception de Fichier (Serveur -> CX)

#### Lancer la demande depuis CX
```bash
# Commande CX pour recevoir le fichier
# (Syntaxe à adapter)
RECEIVE FICHIER testfile.dat
  PARTENAIRE=PESITSERVER
  FICHIER_DISTANT=TEST_SEND
  DESTINATION=/home/cpo/cexp/in/received_from_server.dat
```

#### Vérifier la réception
```bash
ls -lh /home/cpo/cexp/in/
md5sum /tmp/pesit-send/testfile.dat /home/cpo/cexp/in/received_from_server.dat
# Les checksums doivent être identiques
```

### Test 4: Transfert avec Restart

#### Créer un gros fichier
```bash
dd if=/dev/urandom of=/tmp/pesit-send/largefile.dat bs=1M count=100
```

#### Lancer le transfert
```bash
# Depuis CX
RECEIVE FICHIER largefile.dat PARTENAIRE=PESITSERVER
```

#### Interrompre le transfert
```bash
# Arrêter le serveur pendant le transfert
pkill -f "spring-boot:run"

# Attendre quelques secondes
sleep 5

# Redémarrer le serveur
cd /home/cpo/pesitwizard/pesitwizard-server
mvn spring-boot:run -Dspring-boot.run.profiles=cx-test &
```

#### Reprendre le transfert
```bash
# CX devrait automatiquement reprendre depuis le dernier sync point
# Ou relancer manuellement avec option RESTART
```

---

## Étape 5: Monitoring et Diagnostic

### Logs du Serveur PeSIT

```bash
# Logs en temps réel
tail -f /home/cpo/pesitwizard/pesitwizard-server/logs/application.log

# Filtrer les événements de transfert
tail -f logs/application.log | grep -E "CONNECT|WRITE|READ|DTF|CLOSE"

# Voir les erreurs
tail -f logs/application.log | grep ERROR
```

### Logs CX

```bash
# Log principal
tail -f /home/cpo/cexp/config/LOG

# Erreurs
tail -f /home/cpo/cexp/errors.log
```

### Métriques du Serveur

```bash
# Endpoints Actuator
curl http://localhost:8080/actuator/metrics

# Nombre de connexions actives
curl http://localhost:8080/actuator/metrics/pesit.connections.active

# Nombre de transferts
curl http://localhost:8080/actuator/metrics/pesit.transfers.total

# Bytes transférés
curl http://localhost:8080/actuator/metrics/pesit.transfers.bytes.total
```

### Base de Données du Serveur

```bash
# Se connecter à H2 console (si activé)
# URL: http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:mem:testdb

# Voir l'historique des transferts
SELECT * FROM transfer_record ORDER BY started_at DESC;

# Voir les événements d'audit
SELECT * FROM audit_event ORDER BY event_time DESC LIMIT 20;
```

---

## Étape 6: Cas de Test Détaillés

### 1. Test d'Authentification

```bash
# Test avec mot de passe invalide
curl -X POST http://localhost:8080/api/v1/partners \
  -H "Content-Type: application/json" \
  -d '{
    "id": "BADCLIENT",
    "password": "wrong",
    "enabled": true
  }'

# CX essaie de se connecter avec un mauvais mot de passe
# Résultat attendu: RCONNECT avec diagnostic D3-304
```

### 2. Test de Fichier Inexistant

```bash
# CX demande un fichier qui n'existe pas
# Résultat attendu: Rejet avec diagnostic D3-301
```

### 3. Test de Permission

```bash
# Créer un partenaire en lecture seule
curl -X POST http://localhost:8080/api/v1/partners \
  -H "Content-Type: application/json" \
  -d '{
    "id": "READONLY",
    "password": "test",
    "enabled": true,
    "accessType": "READ"
  }'

# CX essaie d'envoyer un fichier (WRITE)
# Résultat attendu: Rejet avec diagnostic D3-304
```

### 4. Test de Gros Fichiers

```bash
# Créer un fichier de 1GB
dd if=/dev/urandom of=/tmp/pesit-send/bigfile.dat bs=1M count=1024

# Transférer via CX
# Vérifier l'intégrité avec checksums
```

### 5. Test de Sync Points

```bash
# Le serveur envoie des SYN à intervalles réguliers
# Vérifier dans les logs:
grep "SYN sent" logs/application.log
grep "ACK_SYN received" logs/application.log
```

---

## Dépannage

### Problème: CX ne peut pas se connecter

**Vérifications:**
```bash
# 1. Serveur écoute sur le bon port?
netstat -an | grep 6502

# 2. Firewall?
sudo ufw status
sudo ufw allow 6502/tcp

# 3. Logs du serveur
tail logs/application.log | grep -i error
```

### Problème: Authentification échoue

**Vérifications:**
```bash
# 1. Partenaire existe?
curl http://localhost:8080/api/v1/partners/CXCLIENT

# 2. Mot de passe correct?
# Vérifier dans les logs d'audit
curl http://localhost:8080/api/v1/audit-events?type=AUTHENTICATION_FAILURE
```

### Problème: Fichier non trouvé

**Vérifications:**
```bash
# 1. Fichier virtuel configuré?
curl http://localhost:8080/api/v1/virtual-files

# 2. Fichier physique existe?
ls -lh /tmp/pesit-send/

# 3. Permissions?
ls -ld /tmp/pesit-send/
chmod 755 /tmp/pesit-send/
```

### Problème: Transfert interrompu

**Vérifications:**
```bash
# 1. Sync points activés?
# Vérifier dans la configuration du serveur

# 2. Dernier sync point enregistré?
# Vérifier dans transfer_record
SELECT last_sync_point, last_sync_byte_position
FROM transfer_record
WHERE status = 'FAILED'
ORDER BY started_at DESC LIMIT 1;
```

---

## Script d'Automatisation

Créer `/home/cpo/pesitwizard/scripts/test-cx-integration.sh`:

```bash
#!/bin/bash
set -e

echo "=== PeSIT Server - CX Integration Test ==="

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

# Configuration
SERVER_DIR="/home/cpo/pesitwizard/pesitwizard-server"
CX_DIR="/home/cpo/cexp"
TEST_DIR="/tmp/pesit-test-$(date +%s)"

# Setup
echo "Setting up test environment..."
mkdir -p $TEST_DIR/{send,receive}

# Start server
echo "Starting PeSIT server..."
cd $SERVER_DIR
mvn spring-boot:run -Dspring-boot.run.profiles=cx-test > $TEST_DIR/server.log 2>&1 &
SERVER_PID=$!
sleep 15

# Check server health
if curl -s http://localhost:8080/actuator/health | grep -q '"status":"UP"'; then
    echo -e "${GREEN}✓${NC} Server started successfully"
else
    echo -e "${RED}✗${NC} Server failed to start"
    cat $TEST_DIR/server.log
    exit 1
fi

# Create partner
echo "Creating test partner..."
curl -s -X POST http://localhost:8080/api/v1/partners \
  -H "Content-Type: application/json" \
  -d '{
    "id": "CXCLIENT",
    "password": "test123",
    "enabled": true,
    "accessType": "READ_WRITE"
  }' > /dev/null && echo -e "${GREEN}✓${NC} Partner created"

# Create virtual file
echo "Creating virtual file..."
curl -s -X POST http://localhost:8080/api/v1/virtual-files \
  -H "Content-Type: application/json" \
  -d "{
    \"virtualName\": \"TESTFILE\",
    \"physicalPath\": \"$TEST_DIR/receive/\${FILE}\",
    \"direction\": \"BOTH\",
    \"partnerId\": \"CXCLIENT\",
    \"enabled\": true
  }" > /dev/null && echo -e "${GREEN}✓${NC} Virtual file created"

# Start CX
echo "Starting CX..."
cd $CX_DIR
source profile
$start_tom > $TEST_DIR/cx.log 2>&1 &
CX_PID=$!
sleep 5

# TODO: Configure CX partner and run tests
echo ""
echo "Manual steps required:"
echo "1. Configure CX partner: $sterm"
echo "2. Run test transfers"
echo "3. Verify results in $TEST_DIR"
echo ""
echo "Press Enter when tests are complete..."
read

# Cleanup
echo "Cleaning up..."
kill $SERVER_PID 2>/dev/null || true
cd $CX_DIR && $stop_tom || true

echo "Test completed. Logs in: $TEST_DIR"
```

---

## Référénces

1. **Documentation PeSIT:** `/home/cpo/pesitwizard/PeSIT-e-fr.pdf`
2. **Manuel CX:** https://www.ibm.com/support/pages/system/files/inline-files/CXUX15_UserGuide.pdf
3. **Documentation Serveur:** `/home/cpo/pesitwizard/PESIT_SERVER_CONFORMANCE.md`
4. **Résultats Tests:** `/home/cpo/pesitwizard/SERVER_TESTING_SUMMARY.md`
