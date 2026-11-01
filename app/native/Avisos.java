package com.davidfernandes.financas;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.Locale;

/** A notificação "De quem foi?" com os botões que classificam o lançamento sem abrir nada. */
final class Avisos {
    static final String CANAL = "revisao";

    private Avisos() {}

    static void criarCanal(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel canal = new NotificationChannel(CANAL, "De quem foi?", NotificationManager.IMPORTANCE_HIGH);
        canal.setDescription("Pergunta de quem foi cada compra ou Pix que chegou dos bancos");
        c.getSystemService(NotificationManager.class).createNotificationChannel(canal);
    }

    static void perguntar(Context c, String id, String titulo, String texto) {
        criarCanal(c);
        int numero = id.hashCode();
        String pessoa = Rede.prefs(c).getString("pessoa", "");
        String tudo = (titulo + " " + texto).toLowerCase(Locale.ROOT);
        boolean entrada = tudo.contains("recebid") || tudo.contains("você recebeu");
        boolean pix = tudo.contains("pix");

        Intent abrir = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CANAL)
                .setSmallIcon(c.getApplicationInfo().icon)
                .setContentTitle(entrada ? "De onde veio?" : "De quem foi?")
                .setContentText(texto)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(texto))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(c, numero, abrir, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));

        // no máximo 3 botões aparecem na notificação; o resto se resolve abrindo o app
        if (entrada) {
            if (!pessoa.isEmpty()) b.addAction(0, pessoa + " pagou", acao(c, id, numero, "pagou"));
            if (pix) b.addAction(0, "Entre minhas contas", acao(c, id, numero, "transf"));
        } else {
            b.addAction(0, "Meu", acao(c, id, numero, "meu"));
            if (!pessoa.isEmpty()) {
                b.addAction(0, pessoa, acao(c, id, numero, "dela"));
                b.addAction(0, pix ? "Entre contas" : "Dividir", acao(c, id, numero, pix ? "transf" : "dividir"));
            } else if (pix) {
                b.addAction(0, "Entre minhas contas", acao(c, id, numero, "transf"));
            }
        }
        try {
            NotificationManagerCompat.from(c).notify(numero, b.build());
        } catch (SecurityException semPermissao) {
            // sem permissão de notificação: o lançamento continua em "A revisar" no painel
        }
    }

    /** Troca a pergunta por um aviso curto do resultado. */
    static void resultado(Context c, int numero, String texto) {
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CANAL)
                .setSmallIcon(c.getApplicationInfo().icon)
                .setContentTitle(texto)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setTimeoutAfter(4000)
                .setAutoCancel(true);
        try {
            NotificationManagerCompat.from(c).notify(numero, b.build());
        } catch (SecurityException ignorado) { }
    }

    private static PendingIntent acao(Context c, String id, int numero, String tipo) {
        Intent i = new Intent(c, AcaoReceiver.class)
                .putExtra("id", id).putExtra("numero", numero).putExtra("tipo", tipo);
        return PendingIntent.getBroadcast(c, (id + tipo).hashCode(), i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
