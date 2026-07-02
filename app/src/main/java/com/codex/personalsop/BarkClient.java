package com.codex.personalsop;

import java.io.ByteArrayOutputStream;
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

        String token = endpoint.trim();
        String safeTitle = title.trim();
        String safeMessage = message.trim();
        Result jsonResult = sendJson(token, safeTitle, safeMessage);
        if (jsonResult.ok || !looksLikeInvalidData(jsonResult.message)) {
            return jsonResult;
        }
        Result formResult = sendMultipart(token, safeTitle, safeMessage);
        if (formResult.ok) {
            return formResult;
        }
        return new Result(false, jsonResult.message + "；表单重试：" + formResult.message);
    }

    private static Result sendJson(String token, String title, String message) {
        try {
            byte[] body = buildJson(token, title, message).getBytes("UTF-8");
            return post(body, "application/json", "JSON");
        } catch (IOException ex) {
            return new Result(false, ex.getMessage() == null ? "Bark JSON 请求失败" : ex.getMessage());
        }
    }

    private static Result sendMultipart(String token, String title, String message) {
        String boundary = "----PersonalSopBark" + System.currentTimeMillis();
        try {
            byte[] body = buildMultipart(boundary, token, title, message);
            return post(body, "multipart/form-data; boundary=" + boundary, "表单");
        } catch (IOException ex) {
            return new Result(false, ex.getMessage() == null ? "Bark 表单请求失败" : ex.getMessage());
        }
    }

    private static Result post(byte[] body, String contentType, String label) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(API_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", contentType);
            connection.setRequestProperty("Accept", "application/json");
            connection.setFixedLengthStreamingMode(body.length);
            connection.setDoOutput(true);
            OutputStream outputStream = connection.getOutputStream();
            outputStream.write(body);
            outputStream.close();
            int code = connection.getResponseCode();
            String response = readResponse(connection, code);
            if (code >= 200 && code < 300 && isSuccess(response)) {
                return new Result(true, "Bark 已发送");
            }
            return new Result(false, "Bark " + label + " 返回 HTTP " + code + ": " + response);
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

    private static byte[] buildMultipart(String boundary, String token, String title, String message) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writePart(output, boundary, "title", title);
        writePart(output, boundary, "msg", message);
        writePart(output, boundary, "url", "");
        writePart(output, boundary, "token", token);
        writePart(output, boundary, "issecure", "0");
        writePart(output, boundary, "sender", title);
        output.write(("--" + boundary + "--\r\n").getBytes("UTF-8"));
        return output.toByteArray();
    }

    private static void writePart(ByteArrayOutputStream output, String boundary, String name, String value) throws IOException {
        output.write(("--" + boundary + "\r\n").getBytes("UTF-8"));
        output.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes("UTF-8"));
        output.write(value.getBytes("UTF-8"));
        output.write("\r\n".getBytes("UTF-8"));
    }

    private static boolean isSuccess(String response) {
        return response != null
                && (response.contains("\"code\":\"1\"")
                || response.contains("\"code\":1")
                || response.contains("\"code\":\"80000000\"")
                || response.contains("\"code\":80000000"));
    }

    private static boolean looksLikeInvalidData(String message) {
        return message != null && message.contains("无效数据");
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
