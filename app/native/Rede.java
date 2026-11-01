package com.davidfernandes.financas;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Conversa com o Apps Script (o mesmo receptor que o MacroDroid usava). */
final class Rede {
    private Rede() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("financas", Context.MODE_PRIVATE);
    }

    /** Envia a notificação como o MacroDroid fazia. Resposta: "ok:<id>", "duplicado", "negado"... */
    static String enviarNotificacao(Context c, String app, String titulo, String texto) {
        SharedPreferences p = prefs(c);
        String url = p.getString("url", ""), token = p.getString("token", "");
        if (url.isEmpty() || token.isEmpty()) return "sem configuração";
        try {
            JSONObject o = new JSONObject();
            o.put("token", token);
            o.put("app", app);
            o.put("title", titulo);
            o.put("text", texto);
            return post(url, o.toString());
        } catch (Exception e) {
            return "erro: " + e.getMessage();
        }
    }

    /** Classifica um lançamento pela API do painel, usando a sessão aberta no app. */
    static boolean atualizar(Context c, String id, JSONObject mudancas) {
        SharedPreferences p = prefs(c);
        String url = p.getString("url", ""), sessao = p.getString("sessao", "");
        if (url.isEmpty() || sessao.isEmpty()) return false;
        try {
            JSONObject o = new JSONObject();
            o.put("acao", "atualizar");
            o.put("args", new JSONArray().put(sessao).put(id).put(mudancas));
            JSONObject r = new JSONObject(post(url, o.toString()));
            return r.optBoolean("ok", false);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * O Apps Script responde ao POST com um redirecionamento (302) para outro endereço,
     * que precisa ser lido com GET. Por isso o redirecionamento é seguido à mão.
     */
    static String post(String endereco, String corpo) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(endereco).openConnection();
        c.setInstanceFollowRedirects(false);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("Content-Type", "text/plain;charset=utf-8");
        try (OutputStream out = c.getOutputStream()) {
            out.write(corpo.getBytes(StandardCharsets.UTF_8));
        }
        int codigo = c.getResponseCode();
        if (codigo >= 300 && codigo < 400) {
            String destino = c.getHeaderField("Location");
            c.disconnect();
            HttpURLConnection g = (HttpURLConnection) new URL(destino).openConnection();
            g.setInstanceFollowRedirects(true);
            g.setConnectTimeout(15000);
            g.setReadTimeout(30000);
            return ler(g);
        }
        return ler(c);
    }

    private static String ler(HttpURLConnection c) throws Exception {
        InputStream in = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
        if (in == null) return "";
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] b = new byte[4096];
        int n;
        while ((n = in.read(b)) > 0) buf.write(b, 0, n);
        in.close();
        c.disconnect();
        return buf.toString("UTF-8");
    }
}
