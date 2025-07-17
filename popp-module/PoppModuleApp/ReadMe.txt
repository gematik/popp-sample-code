TODOs um das Projekt zum Laufen zu bringen
==========================================

1. Projekt mit AndroidStudio öffnen

2. Die folgenden beiden Dependencies müssen ins lokale Maven-Repository gepackt werden:
    implementation 'de.gematik.openhealth:smartcard-jvm:0.1.0-SNAPSHOT'
    implementation 'de.gematik.openhealth:crypto-jvm:0.1.0-SNAPSHOT'

   Diese Dependencies sollten eigentlich vom Gematik-Nexus gezogen werden, aber das habe ich auf die Schnelle
   nicht hingekriegt. Sie sind jedenfalls im Gematik-Nexus vorhanden.

3. Smartphone über USB an den Rechner anschließen und die App mit AndroidStudio installieren

==========================================

Die beiden Libs smartcardio-api-1.0.2.jar und smartcardio-nfc-1.1.9.aar werden benötigt, da es unter Android
keine SmartcardIO-Implementierung gibt. Sie stammen aus dem Zukunftslabor und der Code liegt unter:

https://gitlab.prod.ccs.gematik.solutions/git/spezifikation/vorentwicklung/smartcardioapi
https://gitlab.prod.ccs.gematik.solutions/git/spezifikation/vorentwicklung/smartcardionfc

Ich konnte auf die Schnelle nicht klären, wie die eRezept-App dieses Problem löst. Wenn das geklärt ist kann
natürlich deren Lösung verwendet und die beiden Libs entfernt werden.
