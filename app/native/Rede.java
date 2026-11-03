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
    private static final int MAX_PENDENTES = 50;
    private static final int MAX_HISTORICO = 30;

    private Rede() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("financas", Context.MODE_PRIVATE);
    }

    /**
     * Envia a notificação. Se não chegar ao servidor (sem internet, tempo esgotado),
     * ela fica na fila de pendentes e é reenviada depois. Resposta: "ok:<id>", "duplicado"...
     */
    static String enviarNotificacao(Context c, String app, String titulo, String texto) {
        String r = tentar(c, app, titulo, texto);
        registrar(c, titulo, texto, r);
        if (falhouNaRede(r)) guardarPendente(c, app, titulo, texto);
        return r;
    }

    /** Tenta de novo o que ficou na fila. Devolve quantas foram entregues. */
    static synchronized int reenviarPendentes(Context c) {
        SharedPreferences p = prefs(c);
        JSONArray fila = lerArray(p, "pendentes");
        if (fila.length() == 0) return 0;
        JSONArray resto = new JSONArray();
        int entregues = 0;
        for (int i = 0; i < fila.length(); i++) {
            JSONObject n = fila.optJSONObject(i);
            if (n == null) continue;
            String r = tentar(c, n.optString("app"), n.optString("title"), n.optString("text"));
            registrar(c, n.optString("title"), n.optString("text"), "reenvio: " + r);
            if (falhouNaRede(r)) resto.put(n);
            else entregues++;
        }
        p.edit().putString("pendentes", resto.toString()).apply();
        return entregues;
    }

    static int quantosPendentes(Context c) {
        return lerArray(prefs(c), "pendentes").length();
    }

    static JSONArray historico(Context c) {
        return lerArray(prefs(c), "historico");
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

    private static String tentar(Context c, String app, String titulo, String texto) {
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
            return "erro: " + e.getClass().getSimpleName();
        }
    }

    /** Só vale reenviar o que não chegou; o que o servidor respondeu (até "não reconhecido") já foi tratado. */
    private static boolean falhouNaRede(String r) {
        return r == null || r.isEmpty() || r.startsWith("erro") || r.startsWith("<");
    }

    private static synchronized void guardarPendente(Context c, String app, String titulo, String texto) {
        try {
            SharedPreferences p = prefs(c);
            JSONArray fila = lerArray(p, "pendentes");
            fila.put(new JSONObject().put("app", app).put("title", titulo).put("text", texto).put("quando", System.currentTimeMillis()));
            while (fila.length() > MAX_PENDENTES) fila.remove(0);
            p.edit().putString("pendentes", fila.toString()).apply();
        } catch (Exception ignorado) { }
    }

    private static synchronized void registrar(Context c, String titulo, String texto, String resultado) {
        try {
            SharedPreferences p = prefs(c);
            JSONArray h = lerArray(p, "historico");
            h.put(new JSONObject().put("quando", System.currentTimeMillis())
                    .put("texto", (titulo + " · " + texto).trim()).put("resultado", resultado));
            while (h.length() > MAX_HISTORICO) h.remove(0);
            p.edit().putString("historico", h.toString()).apply();
        } catch (Exception ignorado) { }
    }

    private static JSONArray lerArray(SharedPreferences p, String chave) {
        try {
            return new JSONArray(p.getString(chave, "[]"));
        } catch (Exception e) {
            return new JSONArray();
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
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
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
            g.setConnectTimeout(20000);
            g.setReadTimeout(60000);
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
        return buf.toString("UTF-8").trim();
    }
}
