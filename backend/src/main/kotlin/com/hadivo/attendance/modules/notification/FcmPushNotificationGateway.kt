package com.hadivo.attendance.modules.notification

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification as FcmNotification
import com.hadivo.attendance.config.AppProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@Component
@ConditionalOnExpression("'\${hadivo.notification.push.provider:mock}' == 'fcm' && '\${hadivo.notification.push.fcm.enabled:false}' == 'true' && '\${hadivo.notification.push.fcm.project-id:}' != '' && '\${hadivo.notification.push.fcm.service-account-path:}' != ''")
class FcmPushNotificationGateway(
    private val props: AppProperties,
) : NotificationGateway {

    private val firebaseApp = AtomicReference<FirebaseApp?>()

    override val channel: NotificationChannel = NotificationChannel.PUSH

    override fun send(
        request: NotificationRequest,
        recipient: NotificationRecipient,
        template: NotificationTemplate,
        destination: String?,
    ): NotificationGatewayResult {
        val tokens = recipient.pushTokens.filter { it.isNotBlank() }
        if (recipient.userId == null || tokens.isEmpty() || destination.isNullOrBlank()) {
            return NotificationGatewayResult(
                status = NotificationDeliveryStatus.SKIPPED,
                provider = PROVIDER,
                errorMessage = "Recipient push destination is not available",
            )
        }

        return try {
            val message = MulticastMessage.builder()
                .setNotification(
                    FcmNotification.builder()
                        .setTitle(template.title)
                        .setBody(template.body)
                        .build()
                )
                .putData("eventType", request.eventType.name)
                .putData("tenantId", request.tenantId?.toString().orEmpty())
                .addAllTokens(tokens)
                .build()

            val response = FirebaseMessaging.getInstance(app()).sendEachForMulticast(message)
            val status = if (response.successCount > 0) {
                NotificationDeliveryStatus.SENT
            } else {
                NotificationDeliveryStatus.FAILED
            }
            NotificationGatewayResult(
                status = status,
                provider = PROVIDER,
                providerMessageId = "success=${response.successCount};failure=${response.failureCount}",
                errorMessage = response.responses.firstOrNull { !it.isSuccessful }?.exception?.let(::sanitizeError),
            )
        } catch (ex: Exception) {
            NotificationGatewayResult(
                status = NotificationDeliveryStatus.FAILED,
                provider = PROVIDER,
                errorMessage = sanitizeError(ex),
            )
        }
    }

    private fun app(): FirebaseApp {
        firebaseApp.get()?.let { return it }
        synchronized(this) {
            firebaseApp.get()?.let { return it }
            val projectId = props.notification.push.fcm.projectId
            val serviceAccount = File(props.notification.push.fcm.serviceAccountPath)
            if (!serviceAccount.isFile) {
                throw IllegalStateException("FCM service account file is not available")
            }

            val options = serviceAccount.inputStream().use { stream ->
                FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(stream))
                    .setProjectId(projectId)
                    .build()
            }
            val appName = "hadivo-notification-$projectId"
            val app = FirebaseApp.getApps().firstOrNull { it.name == appName }
                ?: FirebaseApp.initializeApp(options, appName)
            firebaseApp.set(app)
            return app
        }
    }

    private fun sanitizeError(ex: Exception): String =
        (ex.message ?: "FCM push delivery failed")
            .let { message ->
                recipientTokenPatterns.fold(message) { current, tokenPattern ->
                    current.replace(tokenPattern, "[redacted]")
                }
            }
            .take(MAX_ERROR_LENGTH)

    private companion object {
        const val PROVIDER = "fcm"
        const val MAX_ERROR_LENGTH = 1000
        val recipientTokenPatterns = listOf(Regex("[A-Za-z0-9_-]{80,}"))
    }
}
