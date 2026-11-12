package com.vaydelux.bousygram.data.repository

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cloud-only duplicate identity regression; no Telegram credentials or local database. */
class CloudFavoriteIdentityTest {
    private fun video(chat: Long, message: Long, uniqueId: String): TdApi.Message {
        val remote = TdApi.RemoteFile().apply { this.uniqueId = uniqueId }
        val file = TdApi.File().apply { id = message.toInt(); this.remote = remote }
        val media = TdApi.Video().apply { video = file }
        return TdApi.Message().apply {
            chatId = chat
            id = message
            content = TdApi.MessageVideo().apply { video = media }
        }
    }

    @Test fun sameRemoteMediaIsRecognizedAfterRestartEvenWhenMessageIdsDiffer() {
        val source = video(-100L, 70L, "stable-remote-video-123")
        val savedCopy = video(-200L, 900L, "stable-remote-video-123")
        assertTrue(cloudFavoritesMatch(source, savedCopy))
        assertTrue("remote:stable-remote-video-123" in cloudFavoriteIdentityKeys(savedCopy))
    }

    @Test fun forwardedChannelOriginIdentifiesOriginalEvenWithoutFileIdentity() {
        val source = TdApi.Message().apply {
            chatId = -100L
            id = 71L
        }
        val savedForward = TdApi.Message().apply {
            chatId = -200L
            id = 901L
            forwardInfo = TdApi.MessageForwardInfo().apply {
                origin = TdApi.MessageOriginChannel().apply {
                    chatId = -100L
                    messageId = 71L
                }
            }
        }
        assertTrue(cloudFavoritesMatch(source, savedForward))
    }

    @Test fun lastForwardSourceCanIdentifyNonChannelOrigins() {
        val source = TdApi.Message().apply { chatId = 333L; id = 444L }
        val savedForward = TdApi.Message().apply {
            chatId = -200L
            id = 902L
            forwardInfo = TdApi.MessageForwardInfo().apply {
                this.source = TdApi.ForwardSource().apply {
                    chatId = 333L
                    messageId = 444L
                }
            }
        }
        assertTrue(cloudFavoritesMatch(source, savedForward))
    }

    @Test fun unrelatedMediaMustNeverBeDeclaredDuplicateByTitleOrSize() {
        val source = video(100L, 70L, "first-video")
        val other = video(200L, 70L, "another-video")
        assertFalse(cloudFavoritesMatch(source, other))
    }

    @Test fun missingStableFileIdentityDoesNotInventMatches() {
        val first = video(100L, 70L, "")
        val second = video(200L, 71L, "")
        assertFalse(cloudFavoritesMatch(first, second))
    }
}
