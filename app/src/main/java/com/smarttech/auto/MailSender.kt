package com.smarttech.auto

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

object MailSender {

    private const val PREFS_KEY_EMAIL = "gmail_email"
    private const val PREFS_KEY_PASSWORD = "gmail_password"

    fun isConfigured(context: Context): Boolean {
        val p = context.getSharedPreferences("smarttech_auto", Context.MODE_PRIVATE)
        val email = p.getString(PREFS_KEY_EMAIL, null)
        val pass = p.getString(PREFS_KEY_PASSWORD, null)
        return !email.isNullOrBlank() && !pass.isNullOrBlank()
    }

    fun getEmail(context: Context): String {
        val p = context.getSharedPreferences("smarttech_auto", Context.MODE_PRIVATE)
        return p.getString(PREFS_KEY_EMAIL, "") ?: ""
    }

    fun saveCredentials(context: Context, email: String, password: String) {
        context.getSharedPreferences("smarttech_auto", Context.MODE_PRIVATE)
            .edit()
            .putString(PREFS_KEY_EMAIL, email)
            .putString(PREFS_KEY_PASSWORD, password)
            .apply()
    }

    fun sendLogAsync(
        context: Context,
        to: String,
        subject: String,
        body: String,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val p = context.getSharedPreferences("smarttech_auto", Context.MODE_PRIVATE)
        val email = p.getString(PREFS_KEY_EMAIL, "") ?: ""
        val password = p.getString(PREFS_KEY_PASSWORD, "") ?: ""

        if (email.isBlank() || password.isBlank()) {
            onResult(false, "Gmail 계정이 설정되지 않았습니다")
            return
        }

        Thread {
            try {
                val props = Properties().apply {
                    put("mail.smtp.host", "smtp.gmail.com")
                    put("mail.smtp.port", "465")
                    put("mail.smtp.auth", "true")
                    put("mail.smtp.ssl.enable", "true")
                    put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                    put("mail.smtp.socketFactory.fallback", "false")
                    put("mail.smtp.socketFactory.port", "465")
                }

                val session = Session.getInstance(props, object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(email, password)
                    }
                })

                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(email))
                    setRecipient(Message.RecipientType.TO, InternetAddress(to))
                    setSubject(subject)
                    setText(body)
                }

                Transport.send(message)

                Handler(Looper.getMainLooper()).post {
                    onResult(true, "메일 전송 완료")
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    onResult(false, "전송 실패: ${e.message}")
                }
            }
        }.start()
    }
}
