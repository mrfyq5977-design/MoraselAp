
package com.morasel.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import android.text.InputType;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends Activity {

    private static final String SUPABASE_URL =
            "https://qzinzkfnmurmzuecmljp.supabase.co";

    private static final String API_KEY =
            "sb_publishable_K-sCDrOQhGgQy4tXvKKsbw_QF_22Jsc";

    private static final MediaType JSON =
            MediaType.parse("application/json; charset=utf-8");

    private final int BG = Color.rgb(15, 29, 43);
    private final int PANEL = Color.rgb(25, 45, 62);
    private final int BLUE = Color.rgb(24, 104, 150);
    private final int GOLD = Color.rgb(239, 193, 77);
    private final int WHITE = Color.WHITE;

    private final OkHttpClient client = new OkHttpClient();

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private android.content.SharedPreferences prefs;

    private String token = "";
    private String refreshToken = "";
    private String userId = "";
    private String email = "";
    private String currentChatId = "";

    private LinearLayout root;
    private EditText emailInput;
    private EditText passwordInput;

    interface Result {
        void done(String response, String error);
    }

    // ------------------------------
    // الرسائل والتنبيهات
    // ------------------------------

    private void message(String value) {
        runOnUiThread(() ->
                Toast.makeText(
                        MainActivity.this,
                        value,
                        Toast.LENGTH_LONG
                ).show()
        );
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    private JSONObject object(String... values) {
        JSONObject json = new JSONObject();

        try {
            for (int i = 0; i < values.length; i += 2) {
                json.put(values[i], values[i + 1]);
            }
        } catch (Exception ignored) {
        }

        return json;
    }

    // ------------------------------
    // الاتصال بـ Supabase
    // ------------------------------

    private void request(
            String method,
            String path,
            JSONObject data,
            boolean authenticated,
            Result callback) {

        executor.execute(() -> {

            String result = "";
            String error = "";

            try {

                Request.Builder builder =
                        new Request.Builder()
                                .url(SUPABASE_URL + path)
                                .addHeader("apikey", API_KEY)
                                .addHeader(
                                        "Content-Type",
                                        "application/json"
                                )
                                .addHeader(
                                        "Accept",
                                        "application/json"
                                );

                // رمز المستخدم فقط، وليس المفتاح العام
                if (authenticated) {

                    if (token.isEmpty()) {
                        throw new Exception(
                                "انتهت جلسة تسجيل الدخول"
                        );
                    }

                    builder.addHeader(
                            "Authorization",
                            "Bearer " + token
                    );
                }

                if ("POST".equals(method)) {

                    builder.post(
                            RequestBody.create(
                                    data == null
                                            ? "{}"
                                            : data.toString(),
                                    JSON
                            )
                    );

                } else if ("PATCH".equals(method)) {

                    builder.patch(
                            RequestBody.create(
                                    data == null
                                            ? "{}"
                                            : data.toString(),
                                    JSON
                            )
                    );

                } else if ("GET".equals(method)) {

                    builder.get();

                } else {

                    throw new Exception(
                            "طريقة الطلب غير مدعومة"
                    );
                }

                try (Response response =
                             client.newCall(builder.build()).execute()) {

                    result = response.body() == null
                            ? ""
                            : response.body().string();

                    if (!response.isSuccessful()) {
                        error = result;

                        if (error.isEmpty()) {
                            error = "HTTP " + response.code();
                        }
                    }
                }

            } catch (Exception e) {

                error = e.getMessage();

                if (error == null) {
                    error = "خطأ في الاتصال";
                }
            }

            String finalResult = result;
            String finalError = error;

            runOnUiThread(() -> {

                if (!isFinishing()) {
                    callback.done(
                            finalResult,
                            finalError
                    );
                }
            });
        });
    }

    // ------------------------------
    // تصميم الواجهة
    // ------------------------------

    private GradientDrawable shape(int color, int radius) {

        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(color);
        drawable.setCornerRadius(radius);

        return drawable;
    }

    private void startScreen(String title) {

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        root.setPadding(20, 15, 20, 20);
        root.setBackgroundColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        scroll.addView(root);
        setContentView(scroll);

        TextView heading = new TextView(this);

        heading.setText(title);
        heading.setTextColor(GOLD);
        heading.setTextSize(27);
        heading.setGravity(Gravity.CENTER);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(5, 15, 5, 25);

        root.addView(heading);
    }

    private TextView text(String value, int size) {

        TextView view = new TextView(this);

        view.setText(value);
        view.setTextColor(WHITE);
        view.setTextSize(size);
        view.setPadding(8, 12, 8, 12);
        view.setGravity(Gravity.CENTER);

        return view;
    }

    private EditText edit(String hint, int type) {

        EditText input = new EditText(this);

        input.setHint(hint);
        input.setTextColor(WHITE);
        input.setHintTextColor(Color.LTGRAY);
        input.setTextSize(16);
        input.setInputType(type);
        input.setSingleLine(true);
        input.setPadding(15, 12, 15, 12);
        input.setBackground(shape(PANEL, 15));

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);

        params.setMargins(0, 7, 0, 7);

        root.addView(input, params);

        return input;
    }

    private void button(String value, Runnable action) {

        Button btn = new Button(this);

        btn.setText(value);
        btn.setTextColor(WHITE);
        btn.setTextSize(16);
        btn.setAllCaps(false);
        btn.setBackground(shape(BLUE, 15));

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);

        params.setMargins(0, 7, 0, 7);

        root.addView(btn, params);

        btn.setOnClickListener(v -> action.run());
    }

    // ------------------------------
    // بداية التطبيق
    // ------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(
                "morasel_session",
                MODE_PRIVATE
        );

        token = prefs.getString("token", "");
        refreshToken = prefs.getString("refresh_token", "");
        userId = prefs.getString("user_id", "");
        email = prefs.getString("email", "");

        if (!token.isEmpty() && !userId.isEmpty()) {

            loadProfile();

        } else {

            showLogin();
        }
    }

    // ------------------------------
    // شاشة البداية
    // ------------------------------

    private void showLogin() {

        startScreen("مراسل");

        root.addView(
                text("مرحباً بك في مراسل", 20)
        );

        root.addView(
                text("تطبيق المراسلة الخاص بك", 15)
        );

        button(
                "تسجيل الدخول",
                this::showEmailLogin
        );

        button(
                "إنشاء حساب جديد",
                this::showRegister
        );
    }

    // ------------------------------
    // تسجيل الدخول
    // ------------------------------

    private void showEmailLogin() {

        startScreen("تسجيل الدخول");

        emailInput = edit(
                "البريد الإلكتروني",
                InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        );

        passwordInput = edit(
                "كلمة المرور",
                InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_PASSWORD
        );

        button("دخول", this::login);

        button(
                "نسيت كلمة المرور؟",
                this::resetPassword
        );

        button("رجوع", this::showLogin);
    }

    private void showRegister() {

        startScreen("إنشاء حساب جديد");

        emailInput = edit(
                "البريد الإلكتروني",
                InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        );

        passwordInput = edit(
                "كلمة المرور - 6 أحرف على الأقل",
                InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_PASSWORD
        );

        button("إنشاء الحساب", this::register);

        button("رجوع", this::showLogin);
    }

    private void login() {

        String mail =
                emailInput.getText().toString().trim();

        String pass =
                passwordInput.getText().toString();

        if (mail.isEmpty() || pass.isEmpty()) {

            message("أدخل البريد وكلمة المرور");
            return;
        }

        request(
                "POST",
                "/auth/v1/token?grant_type=password",
                object(
                        "email", mail,
                        "password", pass
                ),
                false,
                (response, error) -> {

                    if (!error.isEmpty()) {

                        message("فشل الدخول: " + error);
                        return;
                    }

                    saveSession(response);
                }
        );
    }

    // ------------------------------
    // إنشاء حساب
    // ------------------------------

    private void register() {

        String mail =
                emailInput.getText().toString().trim();

        String pass =
                passwordInput.getText().toString();

        if (mail.isEmpty() || !mail.contains("@")) {

            message("أدخل بريداً إلكترونياً صحيحاً");
            return;
        }

        if (pass.length() < 6) {

            message("كلمة المرور يجب أن تكون 6 أحرف على الأقل");
            return;
        }

        request(
                "POST",
                "/auth/v1/signup",
                object(
                        "email", mail,
                        "password", pass
                ),
                false,
                (response, error) -> {

                    if (!error.isEmpty()) {

                        message("تعذر إنشاء الحساب: " + error);
                        return;
                    }

                    try {

                        JSONObject data =
                                new JSONObject(response);

                        if (!data.has("access_token")) {

                            message(
                                    "تم إنشاء الحساب. تحقق من بريدك الإلكتروني ثم سجل الدخول."
                            );

                            showEmailLogin();
                            return;
                        }

                        saveSession(response);

                    } catch (Exception e) {

                        message("تعذر قراءة نتيجة التسجيل");
                    }
                }
        );
    }

    // ------------------------------
    // حفظ جلسة المستخدم
    // ------------------------------

    private void saveSession(String response) {

        try {

            JSONObject data =
                    new JSONObject(response);

            token = data.getString("access_token");

            refreshToken =
                    data.optString("refresh_token", "");

            JSONObject user =
                    data.getJSONObject("user");

            userId = user.getString("id");

            email = user.optString("email", "");

            prefs.edit()
                    .putString("token", token)
                    .putString("refresh_token", refreshToken)
                    .putString("user_id", userId)
                    .putString("email", email)
                    .apply();

            message("تم تسجيل الدخول بنجاح");

            loadProfile();

        } catch (Exception e) {

            message("تعذر حفظ بيانات الحساب");
        }
    }

    // ------------------------------
    // استعادة كلمة المرور
    // ------------------------------

    private void resetPassword() {

        String mail =
                emailInput.getText().toString().trim();

        if (mail.isEmpty()) {

            message("أدخل بريدك الإلكتروني");
            return;
        }

        request(
                "POST",
                "/auth/v1/recover",
                object("email", mail),
                false,
                (response, error) -> {

                    if (error.isEmpty()) {

                        message(
                                "تم طلب رابط استعادة كلمة المرور"
                        );

                    } else {

                        message(
                                "تعذر إرسال الرابط: " + error
                        );
                    }
                }
        );
    }

    // ------------------------------
    // الملف الشخصي
    // ------------------------------

    private void loadProfile() {

        if (token.isEmpty() || userId.isEmpty()) {

            showLogin();
            return;
        }

        request(
                "GET",
                "/rest/v1/profiles?id=eq."
                        + encode(userId)
                        + "&select=id,username",
                null,
                true,
                (response, error) -> {

                    if (!error.isEmpty()) {

                        message(
                                "تعذر تحميل الملف الشخصي: " + error
                        );

                        return;
                    }

                    try {

                        JSONArray array =
                                new JSONArray(response);

                        if (array.length() == 0) {

                            showCreateProfile();

                        } else {

                            showHome();
                        }

                    } catch (Exception e) {

                        message("تعذر قراءة الملف الشخصي");
                    }
                }
        );
    }

    private void showCreateProfile() {

        startScreen("إنشاء ملفك الشخصي");

        root.addView(
                text("اختر اسم المستخدم الخاص بك", 17)
        );

        EditText username = edit(
                "اسم المستخدم",
                InputType.TYPE_CLASS_TEXT
        );

        button("حفظ الاسم", () -> {

            String name =
                    username.getText().toString().trim();

            if (name.length() < 3) {

                message("الاسم يجب أن يكون 3 أحرف على الأقل");
                return;
            }

            saveUsername(name);
        });

        button("تسجيل الخروج", this::logout);
    }

    private void saveUsername(String name) {

        request(
                "POST",
                "/rest/v1/profiles",
                object(
                        "id", userId,
                        "username", name
                ),
                true,
                (response, error) -> {

                    if (!error.isEmpty()) {

                        message(
                                "تعذر حفظ الاسم. ربما الاسم مستخدم: "
                                        + error
                        );

                        return;
                    }

                    message("تم حفظ اسم المستخدم");

                    showHome();
                }
        );
    }

    // ------------------------------
    // الصفحة الرئيسية
    // ------------------------------

    private void showHome() {

        startScreen("مراسل");

        root.addView(
                text("أهلاً بك في مراسل", 20)
        );

        root.addView(
                text(email, 13)
        );

        EditText search = edit(
                "ابحث باسم المستخدم",
                InputType.TYPE_CLASS_TEXT
        );

        button("بحث عن مستخدم", () -> {

            searchUsers(
                    search.getText().toString().trim()
            );
        });

        root.addView(
                text("محادثاتك", 19)
        );

        LinearLayout chatsArea =
                new LinearLayout(this);

        chatsArea.setOrientation(
                LinearLayout.VERTICAL
        );

        root.addView(chatsArea);

        button(
                "تحديث المحادثات",
                () -> loadChats(chatsArea)
        );

        button("تسجيل الخروج", this::logout);

        loadChats(chatsArea);
    }

    // ------------------------------
    // تحميل المحادثات
    // ------------------------------

    private void loadChats(LinearLayout area) {

        request(
                "GET",
                "/rest/v1/conversation_members?user_id=eq."
                        + encode(userId)
                        + "&select=conversation_id",
                null,
                true,
                (response, error) -> {

                    if (!error.isEmpty()) {

                        message(
                                "تعذر تحميل المحادثات: " + error
                        );

                        return;
                    }

                    try {

                        JSONArray array =
                                new JSONArray(response);

                        area.removeAllViews();

                        if (array.length() == 0) {

                            area.addView(
                                    text("لا توجد محادثات بعد", 15)
                            );

                            return;
                        }

                        for (int i = 0; i < array.length(); i++) {

                            String id =
                                    array.getJSONObject(i)
                                            .getString("conversation_id");

                            Button chatButton =
                                    new Button(this);

                            chatButton.setText(
                                    "المحادثة رقم " + id
                            );

                            chatButton.setAllCaps(false);
                            chatButton.setTextColor(WHITE);
                            chatButton.setBackground(
                                    shape(PANEL, 15)
                            );

                            area.addView(chatButton);

                            chatButton.setOnClickListener(
                                    v -> openChat(id)
                            );
                        }

                    } catch (Exception e) {

                        message("تعذر قراءة المحادثات");
                    }
                }
        );
    }

    // ------------------------------
    // البحث عن المستخدمين
    // ------------------------------

    private void searchUsers(String name) {

        if (name.isEmpty()) {

            message("اكتب اسم المستخدم");
            return;
        }

        request(
                "GET",
                "/rest/v1/profiles?username=ilike.*"
                        + encode(name)
                        + "*&select=id,username",
                null,
                true,
                (response, error) -> {

                    if (!error.isEmpty()) {

                        message("فشل البحث: " + error);
                        return;
                    }

                    try {

                        JSONArray array =
                                new JSONArray(response);

                        if (array.length() == 0) {

                            message(
                                    "لم يتم العثور على المستخدم"
                            );

                            return;
                        }

                        for (int i = 0; i < array.length(); i++) {

                            JSONObject person =
                                    array.getJSONObject(i);

                            String otherId =
                                    person.getString("id");

                            String username =
                                    person.getString("username");

                            if (otherId.equals(userId)) {
                                continue;
                            }

                            new android.app.AlertDialog.Builder(this)
                                    .setTitle(username)
                                    .setMessage(
                                            "هل تريد مراسلة هذا المستخدم؟"
                                    )
                                    .setPositiveButton(
                                            "مراسلة",
                                            (dialog, which) ->
                                                    createChat(otherId)
                                    )
                                    .setNegativeButton(
                                            "إلغاء",
                                            null
                                    )
                                    .show();
                        }

                    } catch (Exception e) {

                        message("تعذر قراءة نتائج البحث");
                    }
                }
        );
    }

    // ------------------------------
    // إنشاء محادثة مباشرة
    // ------------------------------

    private void createChat(String otherUser) {

        request(
                "POST",
                "/rest/v1/rpc/create_direct_conversation",
                object("other_user", otherUser),
                true,
                (response, error) -> {

                    if (!error.isEmpty()) {

                        message(
                                "تعذر إنشاء المحادثة: " + error
                        );

                        return;
                    }

                    String id = response.trim();

                    if (id.startsWith("\"")
                            && id.endsWith("\"")) {

                        id = id.substring(
                                1,
                                id.length() - 1
                        );
                    }

                    if (id.isEmpty()) {

                        message("لم يصل رقم المحادثة");
                        return;
                    }

                    openChat(id);
                }
        );
    }

    // ------------------------------
    // فتح المحادثة
    // ------------------------------

    private void openChat(String chatId) {

        currentChatId = chatId;

        startScreen("المحادثة");

        LinearLayout messagesArea =
                new LinearLayout(this);

        messagesArea.setOrientation(
                LinearLayout.VERTICAL
        );

        root.addView(messagesArea);

        EditText input = edit(
                "اكتب رسالتك هنا",
                InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
        );

        input.setSingleLine(false);
        input.setMinLines(2);

        button("إرسال الرسالة", () -> {

            String value =
                    input.getText().toString().trim();

            if (value.isEmpty()) {

                message("اكتب رسالة أولاً");
                return;
            }

            sendMessage(value);
            input.setText("");
        });

        button(
                "تحديث الرسائل",
                () -> loadMessages(messagesArea)
        );

        button(
                "العودة للرئيسية",
                this::showHome
        );

        loadMessages(messagesArea);
    }

    // ------------------------------
    // تحميل الرسائل
    // ------------------------------

    private void loadMessages(LinearLayout area) {

        request(
                "GET",
                "/rest/v1/messages?conversation_id=eq."
                        + encode(currentChatId)
                        + "&select=id,sender_id,content,created_at"
                        + "&order=created_at.asc",
                null,
                true,
                (response, error) -> {

                    if (!error.isEmpty()) {

                        message(
                                "تعذر تحميل الرسائل: " + error
                        );

                        return;
                    }

                    try {

                        JSONArray array =
                                new JSONArray(response);

                        area.removeAllViews();

                        if (array.length() == 0) {

                            area.addView(
                                    text("ابدأ المحادثة بإرسال رسالة", 15)
                            );

                            return;
                        }

                        for (int i = 0; i < array.length(); i++) {

                            JSONObject item =
                                    array.getJSONObject(i);

                            String sender =
                                    item.getString("sender_id");

                            String content =
                                    item.getString("content");

                            TextView bubble =
                                    new TextView(this);

                            bubble.setText(
                                    (sender.equals(userId)
                                            ? "أنت\n"
                                            : "المستخدم\n")
                                            + content
                            );

                            bubble.setTextColor(WHITE);
                            bubble.setTextSize(16);
                            bubble.setPadding(15, 10, 15, 10);

                            bubble.setBackground(
                                    shape(
                                            sender.equals(userId)
                                                    ? BLUE
                                                    : PANEL,
                                            15
                                    )
                            );

                            LinearLayout.LayoutParams params =
                                    new LinearLayout.LayoutParams(
                                            -1,
                                            -2
                                    );

                            params.setMargins(0, 5, 0, 5);

                            area.addView(
                                    bubble,
                                    params
                            );
                        }

                    } catch (Exception e) {

                        message("تعذر قراءة الرسائل");
                    }
                }
        );
    }

    // ------------------------------
    // إرسال رسالة
    // ------------------------------

    private void sendMessage(String value) {

        if (currentChatId.isEmpty()) {
            return;
        }

        JSONObject data = new JSONObject();

        try {

            data.put(
                    "conversation_id",
                    Long.parseLong(currentChatId)
            );

            data.put("sender_id", userId);
            data.put("content", value);

        } catch (Exception e) {

            message("بيانات الرسالة غير صحيحة");
            return;
        }

        request(
                "POST",
                "/rest/v1/messages",
                data,
                true,
                (response, error) -> {

                    if (!error.isEmpty()) {

                        message(
                                "تعذر إرسال الرسالة: " + error
                        );

                        return;
                    }

                    message("تم إرسال الرسالة");

                    // إعادة عرض المحادثة لتحديث الرسائل
                    openChat(currentChatId);
                }
        );
    }

    // ------------------------------
    // تسجيل الخروج
    // ------------------------------

    private void logout() {

        token = "";
        refreshToken = "";
        userId = "";
        email = "";
        currentChatId = "";

        prefs.edit().clear().apply();

        showLogin();
    }

    // ------------------------------
    // إغلاق التطبيق
    // ------------------------------

    @Override
    protected void onDestroy() {

        executor.shutdown();

        super.onDestroy();
    }
}
