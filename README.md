# Venii Local — application Android native (prototype 1.0)

**Aucune dépendance à un Mac ou à un serveur chinois pendant l'utilisation.** Le téléphone communique directement avec le Venii N1 Max connecté en Wi-Fi local. Cette application est indépendante et non officielle.

## Ce qui est implémenté

- Détection du robot par UDP (`n1_find` et `veniibot_find`, 192.168.78.1:9000).
- Connexion TCP au port 9000 et authentification `67` avec les paramètres temporaires fournis par le robot.
- Chiffrement/déchiffrement compatible avec le script Python d'origine : AES-128-ECB, bourrage nul, Base64, en-tête de longueur 4 octets en big endian.
- Requête d'état `888`, et affichage sécurisé des valeurs numériques connues (888, 60, 1300, 1502, 70).
- Bouton Démarrer `1020` : **désactivé par défaut**, ne peut être déclenché qu'après lecture d'état, déclaration que l'alerte brosse est réglée, et confirmation supplémentaire. Une seule commande de démarrage par appui.
- Boutons Pause `1060` et Base `1050` affichés **mais désactivés** : leurs effets n'ont pas été validés sur le robot.
- N'affiche ni n'enregistre les paramètres secrets `auth` et `mac`. Aucun backend, compte, collecte ni connexion à une API Internet.

## Sécurité importante

**Le robot s'est arrêté avec une alerte vocale concernant la brosse latérale.** Ne cochez pas la validation de réparation et ne testez pas de nouveau démarrage tant que cette erreur n'a pas disparu. Pour tout test actif : surface dégagée, loin des escaliers, une personne près du bouton physique d'arrêt. La pause distante `1060` n'est pas encore confirmée.

Les codes bruts d'état (par exemple `12` ou `2`) ne sont pas traduits en « au repos » ou « nettoyage » faute de preuve suffisante. Le rapport d'alimentation `60` avec valeur `100` n'est pas affiché comme une batterie à 100 % certaine. Une réponse à l'authentification `67` ne prouve pas à elle seule l'autorisation de chaque commande.

## Compiler l'APK sans Android Studio

**Méthode conseillée : GitHub Actions**, gratuite pour un petit dépôt personnel selon les limites du compte.

1. Créez un nouveau dépôt GitHub privé, par exemple `venii-local`.
2. Importez **le contenu de ce dossier** à la racine du dépôt, en conservant `.github/workflows/android-apk.yml` (dossier caché).
3. Ouvrez l'onglet **Actions**, workflow `Construire APK Venii Local`, puis cliquez **Run workflow**. Le workflow s'exécute aussi après un push sur `main`.
4. À la fin, téléchargez l'artifact `VeniiLocal-debug-APK`, décompressez-le et récupérez `app-debug.apk`.
5. Transférez l'APK sur le téléphone Android et installez-la avec l'autorisation d'installer depuis cette source, si demandée.

Le workflow utilise JDK 17, Android SDK 36, Android Gradle Plugin 8.13.2 et Gradle 8.13. C'est un **APK debug pour essai**, non destiné à la publication sur Play Store. Aucune clé de signature privée utilisateur n'est nécessaire pour cet APK debug.

Alternative : avec JDK 17, Android SDK 36 et Gradle 8.13 déjà installés, exécutez `gradle :app:assembleDebug`.

## Utilisation sur Android

1. Connectez manuellement le smartphone au **Wi-Fi VENII** émis par le robot. Si Android indique que ce Wi-Fi n'a pas Internet, sélectionnez **Rester connecté**. Laissez le Wi-Fi actif.
2. Ouvrez **Venii Local** et appuyez sur **Connecter et lire l'état**.
3. Consultez les chiffres affichés. Ils sont des **valeurs brutes du protocole**.
4. **Laissez le bouton Démarrer verrouillé** tant que l'erreur de brosse n'a pas été résolue.
5. Après réparation et nouvel accord explicite, le bouton peut transmettre `1020`. L'effet physique doit être contrôlé visuellement. Les commandes Pause/Base sont toujours inactives.

## Spécifications techniques

- Android natif Java, minSdk 26 / targetSdk 36 / compileSdk 36.
- Android 17 : ce projet cible 36 afin de conserver l'accès LAN implicite pour une installation locale. Si on passe à targetSdk 37, il faudra explicitement gérer la nouvelle autorisation runtime `ACCESS_LOCAL_NETWORK`.
- L'application ne nécessite pas de permission de localisation ni la lecture du SSID. Elle se lie au réseau Wi-Fi présent et utilise l'adresse IP locale fixe du robot.
- La trame chiffrement est inspirée du script authentification fourni par l'utilisateur. La structure `[{"from":"android","to_name":MAC,"from_name":"local","type":"cmd","id":888/1020}]` provient du script V2 et du démarrage observé.
- Le chiffrement AES-ECB est ancien et n'offre pas les propriétés de sécurité modernes ; il est utilisé uniquement pour compatibilité avec le protocole existant du robot sur son Wi-Fi.

## Tests réalisés et limites

- Les tests locaux exécutent `tools/CodecSelfTest.java` sur une JVM pour valider l'encodage et le décodage des trames.
- **Aucune compilation APK native ni essai avec le robot/Android réel ne peut être revendiqué sans les outils SDK et le téléphone/robot.** Le script d'intégration du robot ayant fonctionné sur Mac, le code Android implémente le même protocole, mais cela reste à tester.
- Le code `1020` est validé physiquement dans le parcours précédent ; `888` est validé en réponse JSON. `1060` et `1050` ne le sont pas.

### Vérification hors Android

```bash
mkdir -p /tmp/venii-codec
javac -d /tmp/venii-codec app/src/main/java/com/veniilocal/VeniiFrameCodec.java tools/CodecSelfTest.java
java -cp /tmp/venii-codec CodecSelfTest
```
