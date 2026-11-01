package com.davidfernandes.financas;

import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;

import androidx.core.app.NotificationManagerCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Ponte entre o painel (JavaScript) e a parte nativa: configuração, permissões e teste. */
@CapacitorPlugin(name = "Financas")
public class FinancasPlugin extends Plugin {

    /** Guarda token, endereço do receptor, sessão do painel e a pessoa principal (Luiza). */
    @PluginMethod
    public void configurar(PluginCall call) {
        SharedPreferences.Editor e = Rede.prefs(getContext()).edit();
        for (String k : new String[]{"token", "url", "sessao", "pessoa"}) {
            if (call.getData().has(k)) e.putString(k, call.getString(k, ""));
        }
        e.apply();
        call.resolve(estadoAtual());
    }

    @PluginMethod
    public void estado(PluginCall call) {
        call.resolve(estadoAtual());
    }

    /** Abre a tela do Android onde se libera a leitura de notificações. */
    @PluginMethod
    public void abrirPermissaoNotificacoes(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    /** Manda uma notificação de teste ao receptor (sem "R$", então não vira lançamento). */
    @PluginMethod
    public void testar(PluginCall call) {
        new Thread(() -> {
            JSObject o = new JSObject();
            o.put("resposta", Rede.enviarNotificacao(getContext(), "App Finanças", "Teste de conexão", "Teste sem valor"));
            call.resolve(o);
        }).start();
    }

    private JSObject estadoAtual() {
        SharedPreferences p = Rede.prefs(getContext());
        JSObject o = new JSObject();
        o.put("token", !p.getString("token", "").isEmpty());
        o.put("sessao", !p.getString("sessao", "").isEmpty());
        o.put("pessoa", p.getString("pessoa", ""));
        o.put("leitura", NotificationManagerCompat.getEnabledListenerPackages(getContext()).contains(getContext().getPackageName()));
        o.put("avisos", NotificationManagerCompat.from(getContext()).areNotificationsEnabled());
        return o;
    }
}
