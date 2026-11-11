package org.telegram.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AIOGRAMApi;

public final class AIOGRAMActivity extends Activity {
    private static final int BG = Color.rgb(11, 18, 30);
    private static final int PANEL = Color.rgb(17, 28, 45);
    private static final int PANEL_LIGHT = Color.rgb(29, 45, 68);
    private static final int TEXT = Color.rgb(238, 244, 255);
    private static final int MUTED = Color.rgb(143, 161, 188);
    private static final int BLUE = Color.rgb(66, 139, 238);

    private final AIOGRAMApi api = new AIOGRAMApi();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (activeChatId != null) {
                loadMessages(activeChatId);
                handler.postDelayed(this, 5000);
            }
        }
    };

    private String myNumber = "";
    private String activeChatId;
    private String activeChatName = "";
    private boolean codeSent;
    private LinearLayout root;
    private ScrollView messageScroll;
    private LinearLayout messageColumn;
    private EditText messageInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        String savedToken = getSharedPreferences("aiogram_session", MODE_PRIVATE)
                .getString("token", "");
        api.setSessionToken(savedToken);
        if (savedToken.isEmpty()) {
            showLogin();
        } else {
            api.getMe(new AIOGRAMApi.Callback() {
                @Override
                public void onSuccess(JSONObject result) {
                    JSONObject account = result.optJSONObject("account");
                    myNumber = account == null ? "" : account.optString("number", "");
                    showChats();
                }

                @Override
                public void onError(String message) {
                    clearSession();
                    showLogin();
                }
            });
        }
    }

    private void showLogin() {
        codeSent = false;
        activeChatId = null;
        handler.removeCallbacks(refreshRunnable);
        root = baseColumn();
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(26), dp(24), dp(26), dp(24));

        TextView logo = label("АИОГРАМ", 29, TEXT, true);
        logo.setGravity(Gravity.CENTER);
        root.addView(logo, matchWrap());
        TextView subtitle = label("ТВОЁ ОБЩЕНИЕ. ТВОИ ПРАВИЛА.", 11, MUTED, true);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subParams = matchWrap();
        subParams.topMargin = dp(7);
        root.addView(subtitle, subParams);

        TextView title = label("Вход в АИОГРАМ", 24, TEXT, true);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(38);
        root.addView(title, titleParams);
        TextView description = label("Введи электронную почту. Мы отправим одноразовый код подтверждения.", 14, MUTED, false);
        LinearLayout.LayoutParams descParams = matchWrap();
        descParams.topMargin = dp(9);
        root.addView(description, descParams);

        EditText email = input("Электронная почта", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        LinearLayout.LayoutParams emailParams = matchWrap();
        emailParams.topMargin = dp(24);
        root.addView(email, emailParams);

        EditText code = input("Код из письма", InputType.TYPE_CLASS_NUMBER);
        LinearLayout.LayoutParams codeParams = matchWrap();
        codeParams.topMargin = dp(12);
        code.setVisibility(View.GONE);
        root.addView(code, codeParams);

        TextView action = button("Отправить код");
        LinearLayout.LayoutParams actionParams = matchWrap();
        actionParams.topMargin = dp(16);
        root.addView(action, actionParams);

        TextView status = label("", 13, MUTED, false);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = dp(14);
        root.addView(status, statusParams);

        action.setOnClickListener(v -> {
            String address = email.getText().toString().trim();
            if (!codeSent) {
                status.setText("Отправляем код…");
                api.requestEmailCode(address, new AIOGRAMApi.Callback() {
                    @Override
                    public void onSuccess(JSONObject result) {
                        codeSent = true;
                        code.setVisibility(View.VISIBLE);
                        action.setText("Подтвердить вход");
                        status.setText("Код отправлен. Проверь почту и папку «Спам».");
                        status.setTextColor(Color.rgb(123, 220, 171));
                    }

                    @Override
                    public void onError(String message) {
                        status.setText(message);
                        status.setTextColor(Color.rgb(255, 164, 174));
                    }
                });
            } else {
                status.setText("Проверяем код…");
                api.verifyEmailCode(address, code.getText().toString().trim(), new AIOGRAMApi.Callback() {
                    @Override
                    public void onSuccess(JSONObject result) {
                        JSONObject account = result.optJSONObject("account");
                        myNumber = account == null ? "" : account.optString("number", "");
                        getSharedPreferences("aiogram_session", MODE_PRIVATE).edit()
                                .putString("token", api.getSessionToken()).apply();
                        showChats();
                    }

                    @Override
                    public void onError(String message) {
                        status.setText(message);
                        status.setTextColor(Color.rgb(255, 164, 174));
                    }
                });
            }
        });
        setContentView(root);
    }

    private void showChats() {
        activeChatId = null;
        handler.removeCallbacks(refreshRunnable);
        root = baseColumn();

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(12), dp(14), dp(12));
        header.setBackgroundColor(PANEL);
        TextView brand = label("АИОГРАМ", 21, TEXT, true);
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(44), 1));
        TextView number = label(myNumber, 12, MUTED, false);
        number.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(number, wrapWrap());
        TextView add = button("+");
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        addParams.leftMargin = dp(10);
        header.addView(add, addParams);
        add.setOnClickListener(v -> showNewChatDialog());
        root.addView(header, matchWrap());

        TextView accountLine = label("Личные сообщения", 13, MUTED, false);
        accountLine.setPadding(dp(18), dp(13), dp(18), dp(8));
        root.addView(accountLine, matchWrap());

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView logout = label("Выйти из аккаунта", 13, MUTED, false);
        logout.setGravity(Gravity.CENTER);
        logout.setPadding(dp(12), dp(15), dp(12), dp(15));
        root.addView(logout, matchWrap());
        logout.setOnClickListener(v -> logout());

        setContentView(root);
        loadChats(list);
    }

    private void loadChats(LinearLayout list) {
        api.getChats(new AIOGRAMApi.Callback() {
            @Override
            public void onSuccess(JSONObject result) {
                list.removeAllViews();
                JSONArray chats = result.optJSONArray("chats");
                if (chats == null || chats.length() == 0) {
                    TextView empty = label("Пока нет чатов.\nНажми +, чтобы начать общение", 14, MUTED, false);
                    empty.setGravity(Gravity.CENTER);
                    empty.setPadding(dp(25), dp(45), dp(25), dp(45));
                    list.addView(empty, matchWrap());
                    return;
                }
                for (int i = 0; i < chats.length(); i++) {
                    JSONObject chat = chats.optJSONObject(i);
                    if (chat == null) continue;
                    String id = chat.optString("id", "");
                    String name = getChatName(chat);
                    JSONObject last = chat.optJSONObject("last_message");
                    String preview = last == null ? "Нет сообщений" : last.optString("content", "");
                    addChatRow(list, id, name, preview);
                }
            }

            @Override
            public void onError(String message) {
                Toast.makeText(AIOGRAMActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addChatRow(LinearLayout list, String id, String name, String preview) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(11), dp(16), dp(11));
        TextView avatar = label(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(), 18, TEXT, true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(circle(PANEL_LIGHT));
        row.addView(avatar, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(13), 0, 0, 0);
        TextView title = label(name, 16, TEXT, true);
        TextView last = label(preview, 13, MUTED, false);
        last.setMaxLines(1);
        info.addView(title, matchWrap());
        LinearLayout.LayoutParams previewParams = matchWrap();
        previewParams.topMargin = dp(4);
        info.addView(last, previewParams);
        row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(34, 48, 69));

        list.addView(row, matchWrap());
        list.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        row.setOnClickListener(v -> openChat(id, name));
    }

    private void showNewChatDialog() {
        EditText number = input("Номер АИОГРАМ: +7XXXXXXXX", InputType.TYPE_CLASS_PHONE);
        new AlertDialog.Builder(this)
                .setTitle("Новый личный чат")
                .setView(number)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Открыть", (dialog, which) -> {
                    String value = number.getText().toString().trim();
                    api.createDirectChat(value, new AIOGRAMApi.Callback() {
                        @Override
                        public void onSuccess(JSONObject result) {
                            String chatId = result.optString("chat_id", "");
                            if (!chatId.isEmpty()) openChat(chatId, value);
                        }

                        @Override
                        public void onError(String message) {
                            Toast.makeText(AIOGRAMActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                    });
                }).show();
    }

    private void openChat(String chatId, String name) {
        activeChatId = chatId;
        activeChatName = name;
        root = baseColumn();

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(10), dp(16), dp(10));
        header.setBackgroundColor(PANEL);
        TextView back = button("‹");
        header.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));
        back.setOnClickListener(v -> showChats());
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(10), 0, 0, 0);
        titles.addView(label(name, 17, TEXT, true), matchWrap());
        titles.addView(label("Личный чат", 12, MUTED, false), matchWrap());
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(header, matchWrap());

        messageScroll = new ScrollView(this);
        messageColumn = new LinearLayout(this);
        messageColumn.setOrientation(LinearLayout.VERTICAL);
        messageColumn.setPadding(dp(14), dp(14), dp(14), dp(14));
        messageScroll.addView(messageColumn);
        root.addView(messageScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout composer = new LinearLayout(this);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(dp(10), dp(10), dp(10), dp(10));
        composer.setBackgroundColor(PANEL);
        messageInput = input("Сообщение…", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        composer.addView(messageInput, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView send = button("Отправить");
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(-2, dp(48));
        sendParams.leftMargin = dp(8);
        composer.addView(send, sendParams);
        send.setOnClickListener(v -> sendCurrentMessage());
        root.addView(composer, matchWrap());

        setContentView(root);
        loadMessages(chatId);
        handler.removeCallbacks(refreshRunnable);
        handler.postDelayed(refreshRunnable, 5000);
    }

    private void loadMessages(String chatId) {
        api.getMessages(chatId, new AIOGRAMApi.Callback() {
            @Override
            public void onSuccess(JSONObject result) {
                if (!chatId.equals(activeChatId) || messageColumn == null) return;
                messageColumn.removeAllViews();
                JSONArray messages = result.optJSONArray("messages");
                if (messages == null || messages.length() == 0) {
                    TextView empty = label("Это начало переписки. Напиши первое сообщение", 14, MUTED, false);
                    empty.setGravity(Gravity.CENTER);
                    empty.setPadding(dp(20), dp(40), dp(20), dp(40));
                    messageColumn.addView(empty, matchWrap());
                } else {
                    for (int i = 0; i < messages.length(); i++) {
                        JSONObject message = messages.optJSONObject(i);
                        if (message == null) continue;
                        boolean own = message.optBoolean("is_self");
                        LinearLayout line = new LinearLayout(AIOGRAMActivity.this);
                        line.setGravity(own ? Gravity.RIGHT : Gravity.LEFT);
                        TextView bubble = label(message.optString("content", ""), 15, TEXT, false);
                        bubble.setPadding(dp(13), dp(10), dp(13), dp(10));
                        bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.78f));
                        bubble.setBackground(round(own ? Color.rgb(38, 83, 137) : PANEL_LIGHT, 18));
                        line.addView(bubble, wrapWrap());
                        LinearLayout.LayoutParams lineParams = matchWrap();
                        lineParams.bottomMargin = dp(8);
                        messageColumn.addView(line, lineParams);
                    }
                }
                messageScroll.post(() -> messageScroll.fullScroll(View.FOCUS_DOWN));
            }

            @Override
            public void onError(String message) {
                Toast.makeText(AIOGRAMActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendCurrentMessage() {
        if (activeChatId == null || messageInput == null) return;
        String content = messageInput.getText().toString().trim();
        if (content.isEmpty()) return;
        String chatId = activeChatId;
        api.sendMessage(chatId, content, new AIOGRAMApi.Callback() {
            @Override
            public void onSuccess(JSONObject result) {
                if (messageInput != null) messageInput.setText("");
                loadMessages(chatId);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(AIOGRAMActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String getChatName(JSONObject chat) {
        if (!chat.optString("title", "").isEmpty()) return chat.optString("title");
        JSONArray members = chat.optJSONArray("members");
        if (members != null) {
            for (int i = 0; i < members.length(); i++) {
                JSONObject member = members.optJSONObject(i);
                if (member != null && !member.optBoolean("is_self")) {
                    String username = member.optString("username", "");
                    return username.isEmpty() ? member.optString("number", "Личный чат") : username;
                }
            }
        }
        return "Личный чат";
    }

    private void logout() {
        api.logout(new AIOGRAMApi.Callback() {
            @Override
            public void onSuccess(JSONObject result) {
                clearSession();
                showLogin();
            }

            @Override
            public void onError(String message) {
                clearSession();
                showLogin();
            }
        });
    }

    private void clearSession() {
        api.setSessionToken("");
        getSharedPreferences("aiogram_session", MODE_PRIVATE).edit().remove("token").apply();
    }

    private LinearLayout baseColumn() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(BG);
        return layout;
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView button(String value) {
        TextView view = label(value, 14, Color.WHITE, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(round(BLUE, 14));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private EditText input(String hint, int type) {
        EditText edit = new EditText(this);
        edit.setSingleLine(type != (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE));
        edit.setInputType(type);
        edit.setTextColor(TEXT);
        edit.setHintTextColor(MUTED);
        edit.setHint(hint);
        edit.setTextSize(15);
        edit.setPadding(dp(14), dp(10), dp(14), dp(10));
        edit.setBackground(round(PANEL_LIGHT, 13));
        return edit;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private GradientDrawable circle(int color) {
        GradientDrawable drawable = round(color, 50);
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(-2, -2);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refreshRunnable);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (activeChatId != null) handler.postDelayed(refreshRunnable, 5000);
    }

    @Override
    public void onBackPressed() {
        if (activeChatId != null) {
            showChats();
        } else {
            super.onBackPressed();
        }
    }
}
