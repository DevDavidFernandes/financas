package com.davidfernandes.financas;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

/** Recebe o toque num botão da notificação e classifica o lançamento pela API do painel. */
public class AcaoReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        final String id = intent.getStringExtra("id");
        final String tipo = intent.getStringExtra("tipo");
        final int numero = intent.getIntExtra("numero", 0);
        final String pessoa = Rede.prefs(c).getString("pessoa", "");
        final PendingResult pendente = goAsync();
        new Thread(() -> {
            String rotulo;
            boolean ok;
            try {
                JSONObject m = new JSONObject().put("status", "OK");
                switch (String.valueOf(tipo)) {
                    case "meu":
                        m.put("divisao", "Meu").put("pessoa", "");
                        rotulo = "Meu";
                        break;
                    case "dela":
                        m.put("divisao", "Dela").put("pessoa", pessoa);
                        rotulo = pessoa;
                        break;
                    case "dividir":
                        m.put("divisao", "Dividido").put("pessoa", pessoa); // o servidor divide ao meio
                        rotulo = "Dividido com " + pessoa;
                        break;
                    case "pagou":
                        m.put("pessoa", pessoa).put("categoria", "Acerto").put("descricao", pessoa + " me pagou");
                        rotulo = pessoa + " pagou";
                        break;
                    default:
                        m.put("tipo", "Transferência").put("categoria", "Entre contas").put("descricao", "Transferência entre minhas contas");
                        rotulo = "Entre minhas contas";
                }
                ok = Rede.atualizar(c, id, m);
            } catch (Exception e) {
                rotulo = "";
                ok = false;
            }
            Avisos.resultado(c, numero, ok ? "Salvo: " + rotulo
                    : "Não consegui salvar. Abra o app e entre com a senha.");
            pendente.finish();
        }).start();
    }
}
