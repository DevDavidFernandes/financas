package com.davidfernandes.financas;

import android.app.Notification;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayDeque;
import java.util.Locale;

/**
 * Lê as notificações dos bancos (no lugar do MacroDroid) e manda o texto para o Apps Script.
 * A interpretação continua no servidor: aqui só filtramos o app e a presença de "R$".
 */
public class NotificacoesService extends NotificationListenerService {
    /** Trechos do nome do app que interessam (o nome que aparece no celular, sem acento). */
    private static final String[] APPS = {"santander", "mercado pago"};
    private static final ArrayDeque<String> RECENTES = new ArrayDeque<>();

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || getPackageName().equals(sbn.getPackageName())) return;
        final String app = nomeDoApp(sbn.getPackageName());
        String alvo = app.toLowerCase(Locale.ROOT);
        boolean interessa = false;
        for (String a : APPS) if (alvo.contains(a)) interessa = true;
        if (!interessa) return;

        Bundle ex = sbn.getNotification().extras;
        final String titulo = texto(ex.getCharSequence(Notification.EXTRA_TITLE));
        CharSequence grande = ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
        final String corpo = texto(grande != null ? grande : ex.getCharSequence(Notification.EXTRA_TEXT));
        if (!(titulo + " " + corpo).contains("R$")) return;

        // o Android repete a mesma notificação quando ela é atualizada; manda só uma vez
        String chave = app + "|" + titulo + "|" + corpo;
        synchronized (RECENTES) {
            if (RECENTES.contains(chave)) return;
            RECENTES.addLast(chave);
            while (RECENTES.size() > 50) RECENTES.removeFirst();
        }

        new Thread(() -> {
            String r = Rede.enviarNotificacao(this, app, titulo, corpo);
            if (r != null && r.startsWith("ok:")) Avisos.perguntar(this, r.substring(3).trim(), titulo, corpo);
        }).start();
    }

    private String nomeDoApp(String pacote) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(pacote, 0);
            return String.valueOf(pm.getApplicationLabel(info));
        } catch (Exception e) {
            return pacote;
        }
    }

    private static String texto(CharSequence s) {
        return s == null ? "" : s.toString().trim();
    }
}
