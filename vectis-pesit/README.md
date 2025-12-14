# Vectis PeSIT

Bibliothèque Java implémentant le protocole PeSIT (Protocole d'Échange pour un Système Interbancaire de Télécompensation).

## Fonctionnalités

- **Encodage/décodage FPDU** : Sérialisation binaire conforme à la spécification PeSIT E
- **Tous les types de messages** : CONNECT, CREATE, SELECT, OPEN, WRITE, READ, DTF, etc.
- **Paramètres PeSIT** : Support complet des PI et PGI
- **Session PeSIT** : Gestion des connexions TCP et échanges de messages

## Installation

```xml
<dependency>
    <groupId>com.vectis</groupId>
    <artifactId>vectis-pesit</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Build

```bash
mvn clean install
```

## Utilisation

### Créer un message CONNECT

```java
import com.vectis.fpdu.Fpdu;
import com.vectis.fpdu.FpduType;
import com.vectis.fpdu.ParameterValue;
import static com.vectis.fpdu.ParameterIdentifier.*;

Fpdu connect = new Fpdu(FpduType.CONNECT)
    .withIdSrc(1)
    .withParameter(new ParameterValue(PI_03_DEMANDEUR, "MY_CLIENT"))
    .withParameter(new ParameterValue(PI_04_SERVEUR, "BANK_SERVER"));

byte[] data = connect.toBytes();
```

### Décoder un FPDU

```java
byte[] received = // ... données reçues du réseau
Fpdu fpdu = Fpdu.fromBytes(received);

if (fpdu.getType() == FpduType.ACONNECT) {
    int serverId = fpdu.getIdSrc();
}
```

## Prérequis

- Java 21+
- Maven 3.6+

## Référence

- Spécification PeSIT Version E (Septembre 1989) - GSIT
