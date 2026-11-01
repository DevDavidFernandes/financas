/**
 * Roda no GitHub Actions depois de `npx cap add android`:
 * copia o código nativo e registra o serviço de leitura de notificações no AndroidManifest.
 */
const fs = require('fs');
const path = require('path');

const raiz = path.join(__dirname, '..');
const pacote = path.join(raiz, 'android/app/src/main/java/com/davidfernandes/financas');
const manifesto = path.join(raiz, 'android/app/src/main/AndroidManifest.xml');

fs.mkdirSync(pacote, { recursive: true });
for (const arq of fs.readdirSync(path.join(raiz, 'native'))) {
  fs.copyFileSync(path.join(raiz, 'native', arq), path.join(pacote, arq));
  console.log('copiado', arq);
}

let xml = fs.readFileSync(manifesto, 'utf8');
const permissoes = `
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />`;
const componentes = `
        <service
            android:name=".NotificacoesService"
            android:exported="true"
            android:label="Finanças"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>
        <receiver android:name=".AcaoReceiver" android:exported="false" />
`;
if (!xml.includes('POST_NOTIFICATIONS')) xml = xml.replace('<application', permissoes.trim() + '\n\n    <application');
if (!xml.includes('NotificacoesService')) xml = xml.replace('</application>', componentes + '    </application>');
fs.writeFileSync(manifesto, xml);
console.log('AndroidManifest atualizado');
