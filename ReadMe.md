# Hinweise zum Programmierbeispiel

<Juergen.Zimmermann@HS-Karlsruhe.de>

> Diese Datei ist in Markdown geschrieben und kann z.B. mit IntelliJ IDEA
> oder NetBeans gelesen werden. Näheres zu Markdown gibt es in einem
> [Wiki](http://bit.ly/Markdown-Cheatsheet)

## Powershell

Überprüfung, ob sich Skripte (s.u.) starten lassen:

```CMD
    Get-ExecutionPolicy -list
```

Ggf. das Ausführungsrecht ergänzen:

```CMD
    Set-ExecutionPolicy RemoteSigned CurrentUser
```

## Falls die Speichereinstellung für Gradle zu großzügig ist

In `gradle.properties` bei `org.gradle.jvmargs` den voreingestellten Wert
(1,5 GB) ggf. reduzieren.

## Vorbereitungen im Quellcode der Microservices

In `src\main\resources` ist bei den eigenen Microservices zusätzlich die
Konfigurationsdatei `bootstrap.yml` für _Spring Config_ erforderlich.

## Vorbereitung für den Start der Server

### Internet-Verbindung

Eine _Internet-Verbindung_ muss vorhanden sein, damit die eigenen Microservices
über die IP-Adresse des Rechners aufgerufen werden können. Ansonsten würden die
Rechnernamen verwendet werden, wozu ein DNS-Server benötigt wird.

### IP-Adresse und hosts

Die IP-Adresse wird über das Kommando `ipconfig` ermittelt und liefert z.B.
folgende Ausgabe:

```TXT
    C:\>ipconfig

    Windows-IP-Konfiguration

    Ethernet-Adapter Ethernet:

       ...
       IPv4-Adresse  . . . . . . . . . . : 193.196.84.110
       ...
```

Die IP-Adresse muss dann in `C:\Windows\System32\drivers\etc\hosts` am
Dateiende eingetragen und abgespeichert werden. Dazu muss man
Administrator-Berechtigung haben.

```TXT
    193.196.84.110 localhost
```

### VirtualBox ggf. deaktivieren

Falls VirtualBox installiert ist, darf es nicht aktiviert sein, weil sonst
intern die IP-Adresse `192.168.56.1` verwendet wird.

VirtualBox wird folgendermaßen deaktiviert:

* Netzwerk- und Freigabecenter öffnen, z.B. Kontextmenü beim WLAN-Icon
* _"Adaptereinstellungen ändern"_ anklicken
* _"VirtualBox Host-only Network"_ anklicken
* Deaktivieren

## Überblick: Start der Server

* MongoDB
* Service Discovery (Consul)
* Config
* Zookeeper
* Kafka
* Mailserver
* Messaging-Receiver
* API-Gateway
* Zipkin
* kunde
* bestellung

Die Server (außer MongoDB, Consul, Zookeeper, Kafka und Mailserver) sind
jeweils in einem eigenen Gradle-Projekt.

## MongoDB starten und beenden

Durch Aufruf der .ps1-Datei:

````CMD
    .\mongodb.ps1
````

bzw.

````CMD
    .\mongodb.ps1 stop
````
s
## Mailserver

_FakeSMTP_ wird durch die .ps1-Datei `mailserver` gestartet und läuft auf Port 25000.

## Config-Server starten

Siehe `ReadMe.md` im Beispiel `config`.

Zusätzlich in `git\kunde-dev.properties` die Zeilen mit `server.http2.` und
`server.ssl.` auskommentieren, so dass der aufgerufene Microservice mit _http_
statt http/2 läuft.

## Übersetzung und Ausführung

### Start des Servers in der Kommandozeile

In einer Powershell wird der Server mit der Möglichkeit für einen
_Restart_ gestartet, falls es geänderte Dateien gibt:

```CMD
    .\kunde.ps1
```

### Start des Servers innerhalb von IntelliJ IDEA

Im Auswahlmenü rechts oben, d.h. dort wo _Application_ steht, die erste Option _Edit Configurations ..._ auswählen.
Danach beim Abschnitt _Environment_ im Unterabschnitt _VM options_ den Wert
`-Djavax.net.ssl.trustStore=C:/Users/MEINE_KENNUNG/IdeaProjects/kunde/src/main/resources/truststore.p12 -Djavax.net.ssl.trustStorePassword=zimmermann` eintragen, wobei `MEINE_KENNUNG` durch die eigene Benutzerkennung
zu ersetzen ist. Nun beim Abschnitt _Spring Boot_ im Unterabschnitt _Active Profiles_ den Wert `dev` eintragen und
mit dem Button _OK_ abspeichern.

Von nun an kann man durch Anklicken des grünen Dreiecks rechts oben die _Application_ bzw. den Microservice starten.

### Kontinuierliches Monitoring von Dateiänderungen

In einer zweiten Powershell überwachen, ob es Änderungen gibt, so dass
die Dateien für den Server neu bereitgestellt werden müssen; dazu gehören die
übersetzten .class-Dateien und auch Konfigurationsdateien. Damit nicht bei jeder
Änderung der Server neu gestartet wird und man ständig warten muss, gibt es eine
"Trigger-Datei". Wenn die Datei `restart.txt` im Verzeichnis
`src\main\resources` geändert wird, dann wird ein _Neustart des Servers_
ausgelöst und nur dann.

Die Powershell, um kontinuierlich geänderte Dateien für den Server
bereitzustellen, kann auch innerhalb der IDE geöffnet werden (z.B. als
_Terminal_ bei IntelliJ).

```CMD
    .\gradlew classes -t
```

### Eventuelle Probleme mit Windows

_Nur_ falls es mit Windows Probleme gibt, weil der CLASSPATH zu lang ist und
deshalb `java.exe` nicht gestartet werden kann, dann kann man auf die beiden
folgenden Kommandos absetzen:

```CMD
    .\gradlew bootJar
    java -jar build/libs/kunde-1.0.jar --spring.profiles.active=dev `
         -Djavax.net.ssl.trustStore=./src/main/resources/truststore.p12 `
         -Djavax.net.ssl.trustStorePassword=zimmermann
```

Die [Dokumentation](http://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#executable-jar)
enthält weitere Details zu einer ausführbaren JAR-Datei bei Spring Boot

### Properties beim gestarteten Microservice _kunde_ überprüfen

Mit der URI `https://localhost:8444/actuator/env` kann überprüft werden, ob der
Microservice _kunde_ die Properties vom Config-Server korrekt ausliest. Der
Response wird mit dem MIME-Type `application/vnd.spring-boot.actuator.v1+json`
zurückgegeben, welcher von einem Webbrowser nur mit einem JSON-Plugin nicht verstanden wird.

Die vom Config-Server bereitgestellten Properties sind bei
`"configService:file:///C:/Users/.../IdeaProjects/config/git-repo/kunde-dev.properties"`
zu finden.

Analog können bei Microservice `bestellung` die Properties überprüft werden:

* Der Port ist von `8444` auf `8445` zu ändern.
* Bei `"configService:file:///C:/Users/...` steht `bestellung-dev.properties`

### Registrierung bei _Service Discovery_ überprüfen

```URI
    https://localhost:8501
```

### Spans mit _Zipkin_ visualisieren

Im Webbrowser aufrufen:

```URI
    http://localhost:9411
```

Nachdem mindestens 1 Request zu einem Microservice (z.B. _kunde_) abgesetzt wurde,
auf den Button *Find Traces* klicken. Danach sieht man die einzelnen Methodenaufrufe
als _Spans_.

### Herunterfahren in einer eigenen Powershell

*Funktioniert noch nicht mit Spring WebFlux*

```CMD
    .\kunde.ps1 stop
```

### API-Dokumentation

```CMD
    .\gradlew dokka
```

Dazu muss man mit dem Internet verbunden sein, _ohne_ einen Proxy zu benutzen.

### Consul-Client für die Tests aktivieren

In `src\test\resources\rest-test.properties` die Zeilen mit `spring.cloud. ... = false` auskommentieren

### Tests

Folgende Server müssen gestartet sein:

* MongoDB
* Service Discovery (Consul)
* Config
* Zookeeper
* Kafka
* Messaging-Receiver
* Mailserver

```CMD
    .\gradlew test --fail-fast [--rerun-tasks]
    .\gradlew jacocoTestReport
```

### Zertifikat ggf. in Chrome importieren

Chrome starten und die URI `chrome://settings` eingeben. Danach `Zertifikate verwalten`
im Suchfeld eingeben und auf den gefundenen Link klicken. Jetzt den Karteireiter
_Vertrauenswürdige Stammzertifizierungsstellen_ anklicken und über den Button _Importieren_
die Datei `src\test\resources\certificate.cer` auswählen.

### Codeanalyse durch detekt und ktlint

```CMD
    .\gradlew ktlint detekt
```

## curl als REST-Client

Beispiel:

```CMD
   C:\Zimmermann\Git\mingw64\bin\curl --include --basic --user admin:p --tlsv1.3 --insecure https://localhost:8444/00000000-0000-0000-0000-000000000001
```

## Dashboard für Service Discovery (Consul)

```URI
    https://localhost:8501
```
