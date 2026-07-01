package com.codex.personalsop;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

final class BarkClient {
    static final String API_URL = "http://www.ggsuper.com.cn/push/api/v1/sendMsg_New.php";

    private BarkClient() {
    }

    static Result send(String endpoint, String message) {
        return send(endpoint, "个人sop", message);
    }

    static Result send(String endpoint, String title, String message) {
        if (endpoint == null || endpoint.trim().isEmpty()) {
            return new Result(false, "Token 未填写");
        }
        if (title == null || title.trim().isEmpty()) {
            return new Result(false, "标题未填写");
        }
        if (message == null || message.trim().isEmpty()) {
            return new Result(false, "提醒文字未填写");
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(API_URL);
            byte[] body = buildJson(endpoint.trim(), title.trim(), message).getBytes("UTF-8");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setDoOutput(true);
            OutputStream outputStream = connection.getOutputStream();
            outputStream.write(body);
            outputStream.close();
            int code = connection.getResponseCode();
            String response = readResponse(connection, code);
            if (code >= 200 && code < 300 && isSuccess(response)) {
                return new Result(true, "Bark 已发送");
            }
            return new Result(false, "Bark 返回 HTTP " + code + ": " + response);
        } catch (IOException ex) {
            return new Result(false, ex.getMessage() == null ? "Bark 请求失败" : ex.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String buildJson(String token, String title, String message) {
        return "{"
                + "\"title\":\"" + escapeJson(title) + "\","
                + "\"msg\":\"" + escapeJson(message) + "\","
                + "\"url\":\"\","
                + "\"token\":\"" + escapeJson(token) + "\","
                + "\"issecure\":0,"
                + "\"sender\":\"" + escapeJson(title) + "\""
                + "}";
    }

    private static boolean isSuccess(String response) {
        return response != null
                && (response.contains("\"code\":\"80000000\"")
                || response.contains("\"code\":80000000"));
    }

    private static String readResponse(HttpURLConnection connection, int code) throws IOException {
        InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) {
            return "";
        }
        byte[] buffer = new byte[4096];
        StringBuilder builder = new StringBuilder();
        int read;
        while ((read = stream.read(buffer)) != -1) {
            builder.append(new String(buffer, 0, read, Charset.forName("UTF-8")));
        }
        stream.close();
        return builder.toString();
    }

    private static String escapeJson(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\':
                    builder.append("\\\\");
                    break;
                case '"':
                    builder.append("\\\"");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                default:
                    builder.append(c);
                    break;
            }
        }
        return builder.toString();
    }

    static final class Result {
        final boolean ok;
        final String message;

        Result(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }
    }
}

