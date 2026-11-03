const express = require("express");
const multer = require("multer");
const admin = require("firebase-admin");

const app = express();

app.use(express.json({ limit: "32kb" }));

// إعداد Firebase Admin
const serviceAccountText =
    process.env.FIREBASE_SERVICE_ACCOUNT_JSON;

if (!serviceAccountText) {
    throw new Error("FIREBASE_SERVICE_ACCOUNT_JSON is missing");
}

const serviceAccount = JSON.parse(serviceAccountText);

if (serviceAccount.private_key) {
    serviceAccount.private_key =
        serviceAccount.private_key.replace(/\\n/g, "\n");
}

admin.initializeApp({
    credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

// إعداد Dosya
const DOSYA_API = "https://api.dosya.dev/api";
const DOSYA_API_KEY = process.env.DOSYA_API_KEY;
const DOSYA_WORKSPACE = process.env.DOSYA_WORKSPACE_ID;

if (!DOSYA_API_KEY || !DOSYA_WORKSPACE) {
    throw new Error("Dosya environment variables are missing");
}

// استقبال ملفات صوتية فقط، بحد أقصى 10 ميجابايت
const storage = multer.memoryStorage();

const upload = multer({
    storage,
    limits: {
        fileSize: 10 * 1024 * 1024,
        files: 1
    },
    fileFilter: (req, file, callback) => {
        const allowed = [
            "audio/mp4",
            "audio/aac",
            "audio/3gpp",
            "audio/webm",
            "audio/ogg",
            "audio/mpeg"
        ];

        if (!allowed.includes(file.mimetype)) {
            return callback(
                new Error("Unsupported audio format")
            );
        }

        callback(null, true);
    }
});

// التحقق من تسجيل دخول Firebase
async function requireAuth(req, res, next) {
    try {
        const header = req.headers.authorization || "";

        if (!header.startsWith("Bearer ")) {
            return res.status(401).json({
                error: "Authentication required"
            });
        }

        const token = header.substring(7);
        const decoded = await admin.auth().verifyIdToken(token);

        req.uid = decoded.uid;
        next();

    } catch (error) {
        return res.status(401).json({
            error: "Invalid authentication"
        });
    }
}

// التحقق من عضوية المستخدم في المحادثة
async function checkChatMember(chatId, uid) {
    if (
        typeof chatId !== "string" ||
        !/^[a-zA-Z0-9_-]{1,150}$/.test(chatId)
    ) {
        return false;
    }

    const chat = await db.collection("chats")
        .doc(chatId)
        .get();

    if (!chat.exists) return false;

    const participants = chat.get("participants") || [];

    return participants.includes(uid);
}

// فحص الخادم
app.get("/", (req, res) => {
    res.json({
        status: "ok",
        app: "Morasel Server"
    });
});

// رفع رسالة صوتية إلى Dosya
app.post(
    "/audio/upload",
    requireAuth,
    upload.single("audio"),
    async (req, res) => {
        try {
            const chatId = req.body.chatId;

            if (!req.file) {
                return res.status(400).json({
                    error: "Audio file is required"
                });
            }

            const allowed = await checkChatMember(
                chatId,
                req.uid
            );

            if (!allowed) {
                return res.status(403).json({
                    error: "Chat access denied"
                });
            }

            const extension =
                req.file.mimetype === "audio/webm" ? "webm" :
                req.file.mimetype === "audio/ogg" ? "ogg" :
                req.file.mimetype === "audio/aac" ? "aac" :
                req.file.mimetype === "audio/3gpp" ? "3gp" :
                req.file.mimetype === "audio/mpeg" ? "mp3" : "m4a";

            const fileName =
                "morasel-voice-" + Date.now() + "." + extension;

            // إنشاء جلسة رفع
            const initResponse = await fetch(
                DOSYA_API + "/upload/init",
                {
                    method: "POST",
                    headers: {
                        "Authorization": "Bearer " + DOSYA_API_KEY,
                        "Content-Type": "application/json"
                    },
                    body: JSON.stringify({
                        file_name: fileName,
                        file_size: req.file.size,
                        workspace_id: DOSYA_WORKSPACE,
                        mime_type: req.file.mimetype
                    })
                }
            );

            const initData = await initResponse.json();

            if (
                !initResponse.ok ||
                !initData.ok ||
                !initData.upload_url ||
                !initData.session_id
            ) {
                console.error(
                    "Dosya init failed:",
                    initData
                );

                return res.status(502).json({
                    error: "Could not start Dosya upload"
                });
            }

            // الملفات الصغيرة تستخدم رفع PUT واحد
            if (initData.resumable !== null) {
                return res.status(502).json({
                    error: "Unexpected multipart upload session"
                });
            }

            const uploadUrl = new URL(
                initData.upload_url,
                DOSYA_API
            );

            if (
                uploadUrl.origin !==
                new URL(DOSYA_API).origin
            ) {
                return res.status(502).json({
                    error: "Invalid upload destination"
                });
            }

            // إرسال الملف إلى Dosya
            const uploadResponse = await fetch(uploadUrl, {
                method: "PUT",
                headers: {
                    "Content-Type": "application/octet-stream"
                },
                body: req.file.buffer
            });

            if (!uploadResponse.ok) {
                console.error(
                    "Dosya upload failed:",
                    uploadResponse.status
                );

                return res.status(502).json({
                    error: "Audio upload failed"
                });
            }

            // قراءة نتيجة الرفع
            const uploadData = await uploadResponse.json();

            if (
                !uploadData.ok ||
                !uploadData.file ||
                !uploadData.file.id
            ) {
                console.error(
                    "Dosya upload response:",
                    uploadData
                );

                return res.status(502).json({
                    error: "Invalid Dosya upload response"
                });
            }

            return res.status(201).json({
                ok: true,
                fileId: uploadData.file.id,
                size: uploadData.file.size_bytes,
                mimeType: uploadData.file.mime_type
            });

        } catch (error) {
            console.error(
                "Audio upload error:",
                error.message
            );

            return res.status(500).json({
                error: "Audio upload failed"
            });
        }
    }
);

// تشغيل الصوت بعد التحقق من صلاحية المستخدم
app.get(
    "/audio/:fileId",
    requireAuth,
    async (req, res) => {
        try {
            const fileId = req.params.fileId;
            const chatId = req.query.chatId;

            if (
                typeof fileId !== "string" ||
                fileId.length < 1 ||
                fileId.length > 255
            ) {
                return res.status(400).json({
                    error: "Invalid file ID"
                });
            }

            const allowed = await checkChatMember(
                chatId,
                req.uid
            );

            if (!allowed) {
                return res.status(403).json({
                    error: "Chat access denied"
                });
            }

            // التأكد أن الملف مرتبط برسالة صوتية
            const messages = await db.collection("chats")
                .doc(chatId)
                .collection("messages")
                .where("audioFileId", "==", fileId)
                .limit(1)
                .get();

            if (messages.empty) {
                return res.status(404).json({
                    error: "Audio message not found"
                });
            }

            // الحصول على رابط تنزيل موقّع من Dosya
            const grantResponse = await fetch(
                DOSYA_API + "/files/" +
                encodeURIComponent(fileId) +
                "/download-url",
                {
                    method: "GET",
                    headers: {
                        "Authorization": "Bearer " + DOSYA_API_KEY
                    }
                }
            );

            if (!grantResponse.ok) {
                console.error(
                    "Dosya grant failed:",
                    grantResponse.status
                );

                return res.status(502).json({
                    error: "Could not obtain download permission"
                });
            }

            const grant = await grantResponse.json();

            if (
                typeof grant.url !== "string" ||
                !Number.isSafeInteger(grant.size) ||
                grant.size < 0
            ) {
                return res.status(502).json({
                    error: "Invalid Dosya download response"
                });
            }

            // التحقق من أن الرابط يستخدم HTTPS
            const signedUrl = new URL(grant.url);

            if (signedUrl.protocol !== "https:") {
                return res.status(502).json({
                    error: "Invalid download URL"
                });
            }

            // تنزيل الصوت دون إرسال مفتاح Dosya
            const audioResponse = await fetch(signedUrl);

            if (!audioResponse.ok) {
                return res.status(502).json({
                    error: "Could not fetch audio file"
                });
            }

            const audioBuffer = Buffer.from(
                await audioResponse.arrayBuffer()
            );

            // التحقق من حجم الملف
            if (audioBuffer.length !== grant.size) {
                return res.status(502).json({
                    error: "Audio size verification failed"
                });
            }

            res.setHeader(
                "Content-Type",
                audioResponse.headers.get("content-type") ||
                "application/octet-stream"
            );

            res.setHeader(
                "Content-Length",
                audioBuffer.length
            );

            res.setHeader(
                "Cache-Control",
                "private, no-store"
            );

            res.setHeader(
                "X-Content-Type-Options",
                "nosniff"
            );

            return res.status(200).send(audioBuffer);

        } catch (error) {
            console.error(
                "Audio playback error:",
                error.message
            );

            if (!res.headersSent) {
                return res.status(500).json({
                    error: "Audio playback failed"
                });
            }
        }
    }
);

// معالجة الأخطاء
app.use((error, req, res, next) => {
    console.error("Server error:", error.message);

    if (error instanceof multer.MulterError) {
        return res.status(400).json({
            error: "Audio file is too large or invalid"
        });
    }

    return res.status(400).json({
        error: "Invalid request"
    });
});

// تشغيل الخادم
const PORT = process.env.PORT || 3000;

app.listen(PORT, () => {
    console.log(
        "Morasel Server is running on port " + PORT
    );
});
