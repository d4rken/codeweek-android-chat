package tld.domain.codeweek.api

import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.JUnitTree
import timber.log.Timber
import java.util.*

class ChatRepoTest {
    private val logTree = JUnitTree()

    @BeforeEach
    fun onSetup() {
        Timber.plant(logTree)
    }

    @AfterEach
    fun onTearDown() {
        Timber.uproot(logTree)
    }

    @Test
    fun `test creating, retrieving and deleting messages`() {
        val chatRepo = ChatRepo()

        val receivedMessages = mutableListOf<ChatRepo.Message>()
        val cancelable = chatRepo.observeMessagesAsync {
            receivedMessages.addAll(it)
        }

        val author = "Android UnitTest"
        val content = "Android UnitTest Message: ${UUID.randomUUID()}"

        val newMessageId = chatRepo.sendMessageBlocking(author, content)
        newMessageId shouldNotBe null

        val allMessages = chatRepo.getMessagesBlocking()

        val ourMessage = allMessages.single { it.id == newMessageId }
        ourMessage.author shouldBe author
        ourMessage.content shouldBe content

        // Wait for subscription
        Thread.sleep(1000)
        chatRepo.deleteMessageBlocking(newMessageId) shouldBe true

        receivedMessages.size shouldNotBe 0
        cancelable.cancel()
        cancelable.isCanceled shouldBe true
    }
}