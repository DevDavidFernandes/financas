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
 *
 * Para não perder nada quando o HyperOS congela o app: ao reconectar (ou ao abrir o app),
 * reenvia o que ficou na fila e varre as notificações dos bancos que ainda estão na barra.
 * O servidor descarta o que já recebeu.
 */
public class NotificacoesService extends NotificationListenerService {
    /** Trechos do nome do app que interessam (o nome que aparece no celular, sem acento). */
    private static final String[] APPS = {"santander", "mercado pago"};
    private static final ArrayDeque<String> RECENTES = new ArrayDeque<>();
    static volatile NotificacoesService instancia;

    @Override
    public void onListenerConnected() {
        instancia = this;
        new Thread(this::recuperar).start();
    }

    @Override
    public void onListenerDisconnected() {
        instancia = null;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        instancia = this;
        final Notificacao n = ler(sbn);
        if (n == null || jaVisto(n)) return;
        new Thread(() -> {
            Rede.reenviarPendentes(this);
            enviar(n);
        }).start();
    }

    /** Reenvia a fila e as notificações dos bancos ainda na barra (chamado também pelo app). */
    void recuperar() {
        Rede.reenviarPendentes(this);
        try {
            StatusBarNotification[] ativas = getActiveNotifications();
            if (ativas == null) return;
            for (StatusBarNotification sbn : ativas) {
                Notificacao n = ler(sbn);
                if (n != null && !jaVisto(n)) enviar(n);
            }
        } catch (Exception semAcesso) {
            // sem permissão de leitura no momento: nada a varrer
        }
    }

    private void enviar(Notificacao n) {
        String r = Rede.enviarNotificacao(this, n.app, n.titulo, n.corpo);
        if (r != null && r.startsWith("ok:")) Avisos.perguntar(this, r.substring(3).trim(), n.titulo, n.corpo);
    }

    /** null quando a notificação não é de um banco acompanhado ou não tem valor em R$. */
    private Notificacao ler(StatusBarNotification sbn) {
        if (sbn == null || getPackageName().equals(sbn.getPackageName())) return null;
        String app = nomeDoApp(sbn.getPackageName());
        String alvo = app.toLowerCase(Locale.ROOT);
        boolean interessa = false;
        for (String a : APPS) if (alvo.contains(a)) interessa = true;
        if (!interessa) return null;
        // resumo de grupo ("3 novas notificações") não tem o texto da compra
        if ((sbn.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) != 0) return null;

        Bundle ex = sbn.getNotification().extras;
        String titulo = texto(ex.getCharSequence(Notification.EXTRA_TITLE));
        CharSequence grande = ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
        String corpo = texto(grande != null ? grande : ex.getCharSequence(Notification.EXTRA_TEXT));
        if (!(titulo + " " + corpo).contains("R$")) return null;
        return new Notificacao(app, titulo, corpo);
    }

    /** O Android repete a mesma notificação quando ela é atualizada; trata só uma vez. */
    private static boolean jaVisto(Notificacao n) {
        String chave = n.app + "|" + n.titulo + "|" + n.corpo;
        synchronized (RECENTES) {
            if (RECENTES.contains(chave)) return true;
            RECENTES.addLast(chave);
            while (RECENTES.size() > 100) RECENTES.removeFirst();
            return false;
        }
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

    private static final class Notificacao {
        final String app, titulo, corpo;

        Notificacao(String app, String titulo, String corpo) {
            this.app = app;
            this.titulo = titulo;
            this.corpo = corpo;
        }
    }
}
