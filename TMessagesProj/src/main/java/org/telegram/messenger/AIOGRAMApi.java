package org.telegram.messenger;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * REST adapter for the independent AIOGRAM service.
 *
 * This class deliberately does not replace Telegram's MTProto transport.
 * UI code can use it as a separate backend client while the AIOGRAM screens
 * are migrated incrementally.
 */
public final class AIOGRAMApi {
    public interface Callback {
        void onSuccess(JSONObject result);
        void onError(String message);
    }

    private static final String BASE_URL = "https://aiogram-server.onrender.com";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 20000;

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private volatile String sessionToken = "";

    public void setSessionToken(String token) {
        sessionToken = token == null ? "" : token.trim();
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void requestEmailCode(String email, Callback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("email", email == null ? "" : email.trim());
        } catch (Exception e) {
            postError(callback, "Не удалось подготовить запрос");
            return;
        }
        execute("POST", "/auth/email/request", body, false, callback);
    }

    public void verifyEmailCode(String email, String code, Callback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("email", email == null ? "" : email.trim());
            body.put("code", code == null ? "" : code.trim());
        } catch (Exception e) {
            postError(callback, "Не удалось подготовить запрос");
            return;
        }
        execute("POST", "/auth/email/verify", body, false, new Callback() {
            @Override
            public void onSuccess(JSONObject result) {
                setSessionToken(result.optString("token", ""));
                if (callback != null) callback.onSuccess(result);
            }

            @Override
            public void onError(String message) {
                if (callback != null) callback.onError(message);
            }
        });
    }

    public void getMe(Callback callback) {
        execute("GET", "/auth/me", null, true, callback);
    }

    public void getChats(Callback callback) {
        execute("GET", "/api/chats", null, true, callback);
    }

    public void createDirectChat(String virtualNumber, Callback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("number", virtualNumber == null ? "" : virtualNumber.trim());
        } catch (Exception e) {
            postError(callback, "Не удалось подготовить запрос");
            return;
        }
        execute("POST", "/api/chats/direct", body, true, callback);
    }

    public void getMessages(String chatId, Callback callback) {
        execute("GET", "/api/chats/" + encodePathPart(chatId) + "/messages?limit=80",
                null, true, callback);
    }

    public void sendMessage(String chatId, String content, Callback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("content", content == null ? "" : content);
        } catch (Exception e) {
            postError(callback, "Не удалось подготовить сообщение");
            return;
        }
        execute("POST", "/api/chats/" + encodePathPart(chatId) + "/messages",
                body, true, callback);
    }

    public void logout(Callback callback) {
        execute("POST", "/auth/logout", new JSONObject(), true, new Callback() {
            @Override
            public void onSuccess(JSONObject result) {
                setSessionToken("");
                if (callback != null) callback.onSuccess(result);
            }

            @Override
            public void onError(String message) {
                if (callback != null) callback.onError(message);
            }
        });
    }

    private void execute(String method, String path, JSONObject body,
                         boolean authorized, Callback callback) {
        if (authorized && sessionToken.isEmpty()) {
            postError(callback, "Сначала войди в АИОГРАМ");
            return;
        }

        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(BASE_URL + path);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod(method);
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setUseCaches(false);
                connection.setRequestProperty("Accept", "application/json");

                if (body != null) {
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                }
                if (authorized) {
                    connection.setRequestProperty("Authorization", "Bearer " + sessionToken);
                }

                if (body != null) {
                    byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                    try (OutputStream output = connection.getOutputStream()) {
                        output.write(payload);
                    }
                }

                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 400
                        ? connection.getInputStream() : connection.getErrorStream();
                String responseText = readFully(stream);
                JSONObject response = responseText.isEmpty()
                        ? new JSONObject() : new JSONObject(responseText);

                if (status < 200 || status >= 300) {
                    throw new IOException(response.optString("error", "Ошибка сервера (" + status + ")"));
                }

                if (callback != null) {
                    MAIN.post(() -> callback.onSuccess(response));
                }
            } catch (Exception error) {
                String message = error.getMessage();
                if (message == null || message.trim().isEmpty()) message = "Не удалось подключиться к АИОГРАМ";
                postError(callback, message);
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private static String readFully(InputStream stream) throws IOException {
        if (stream == null) return "";
        try (InputStream input = stream;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String encodePathPart(String value) {
        if (value == null) return "";
        return value.replace("%", "%25").replace("/", "%2F").replace("?", "%3F")
                .replace("#", "%23").replace(" ", "%20");
    }

    private static void postError(Callback callback, String message) {
        if (callback != null) {
            MAIN.post(() -> callback.onError(message));
        }
    }
}
