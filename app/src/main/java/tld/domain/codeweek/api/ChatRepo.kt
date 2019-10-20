package tld.domain.codeweek.api

import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.ApolloSubscriptionCall
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.internal.util.Cancelable
import com.apollographql.apollo.response.CustomTypeAdapter
import com.apollographql.apollo.response.CustomTypeValue
import com.apollographql.apollo.subscription.WebSocketSubscriptionTransport
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import timber.log.Timber
import tld.domain.codeweek.api.type.CustomType
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


class ChatRepo {
    companion object {
        private const val LOGTAG = "ChatRepo"
        private const val BASE_URL = "https://codingweek.herokuapp.com/v1/graphql"
        private const val BASE_URL_SUBS = "wss://codingweek.herokuapp.com/v1/graphql"
        private const val CHANNEL_MAIN = "main"
    }

    private val apolloClient: ApolloClient by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .addNetworkInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .build()

                chain.proceed(request)
            }
            .build()

        val dateAdapter = object : CustomTypeAdapter<Date> {
            val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT)
            override fun decode(value: CustomTypeValue<*>): Date {
                try {
                    return formatter.parse(value.value.toString())!!
                } catch (e: Exception) {
                    throw RuntimeException(e)
                }
            }

            override fun encode(value: Date): CustomTypeValue<*> {
                return CustomTypeValue.GraphQLString(formatter.format(value))
            }
        }

        val uuidAdapter = object : CustomTypeAdapter<UUID> {
            override fun decode(value: CustomTypeValue<*>): UUID {
                try {
                    return UUID.fromString(value.value.toString())
                } catch (e: Exception) {
                    throw RuntimeException(e)
                }
            }

            override fun encode(value: UUID): CustomTypeValue<*> {
                return CustomTypeValue.GraphQLString(value.toString())
            }
        }


        ApolloClient.builder()
            .serverUrl(BASE_URL)
            .okHttpClient(okHttpClient).subscriptionTransportFactory(
                WebSocketSubscriptionTransport.Factory(
                    BASE_URL_SUBS,
                    okHttpClient
                )
            )
            .addCustomTypeAdapter(CustomType.TIMESTAMPTZ, dateAdapter)
            .addCustomTypeAdapter(CustomType.UUID, uuidAdapter)
            .build()
    }

    fun getMessagesBlocking(): List<Message> {
        return runBlocking {
            suspendCoroutine<List<Message>> { cont ->
                val query = GetMessagesQuery.builder().build()

                val callback = object : ApolloCall.Callback<GetMessagesQuery.Data>() {
                    override fun onFailure(e: ApolloException) {
                        Timber.tag(LOGTAG).e(e, "getMessagesBlocking()")
                        cont.resumeWithException(e)
                    }

                    override fun onResponse(response: Response<GetMessagesQuery.Data>) {
                        val messages = response.data()?.messages
                            ?.map {
                                Message(
                                    it.id,
                                    created = it.created,
                                    channel = it.channel,
                                    author = it.user_name,
                                    content = it.content
                                )
                            }
                            ?.filter { it.channel == CHANNEL_MAIN }
                        Timber.tag(LOGTAG).d("getMessagesBlocking(): %s", messages)
                        cont.resume(messages!!)
                    }
                }

                apolloClient
                    .query(query)
                    .enqueue(callback)
            }
        }
    }

    fun sendMessageBlocking(author: String?, content: String): UUID {
        return runBlocking {
            suspendCoroutine<UUID> { cont ->
                val mutation = CreateMessageMutation.builder()
                    .apply {
                        channel(CHANNEL_MAIN)
                        if (author != null) user_name(author)
                        content(content)
                    }
                    .build()

                val callback = object : ApolloCall.Callback<CreateMessageMutation.Data>() {
                    override fun onFailure(e: ApolloException) {
                        Timber.tag(LOGTAG).e(e, "sendMessageBlocking()")
                        cont.resumeWithException(e)
                    }

                    override fun onResponse(response: Response<CreateMessageMutation.Data>) {
                        val messageId = response.data()!!.insert_messages()!!.returning().first().id
                        Timber.tag(LOGTAG).d("getMessagesBlocking(): %s", messageId)
                        cont.resume(messageId)
                    }
                }

                apolloClient
                    .mutate(mutation)
                    .enqueue(callback)
            }
        }
    }

    fun deleteMessageBlocking(id: UUID): Boolean {
        return runBlocking {
            suspendCoroutine<Boolean> { cont ->
                val mutation = DeleteMessageMutation.builder()
                    .id(id)
                    .build()

                val callback = object : ApolloCall.Callback<DeleteMessageMutation.Data>() {
                    override fun onFailure(e: ApolloException) {
                        Timber.tag(LOGTAG).e(e, "sendMessageBlocking()")
                        cont.resumeWithException(e)
                    }

                    override fun onResponse(response: Response<DeleteMessageMutation.Data>) {
                        val success = response.data()!!.delete_messages()!!.affected_rows != 0
                        Timber.tag(LOGTAG).d("deleteMessageBlocking(): %s", success)
                        cont.resume(success)
                    }
                }

                apolloClient
                    .mutate(mutation)
                    .enqueue(callback)
            }
        }
    }

    fun observeMessagesAsync(callback: (List<Message>) -> Unit): Cancelable {
        val subscription = apolloClient.subscribe(ObserveMessagesSubscription())

        val subscriptionCallback =
            object : ApolloSubscriptionCall.Callback<ObserveMessagesSubscription.Data> {
                override fun onFailure(e: ApolloException) {
                    Timber.tag(LOGTAG).e(e, "observeMessagesAsync()")
                    throw e
                }

                override fun onResponse(response: Response<ObserveMessagesSubscription.Data>) {
                    val messages = response.data()?.messages
                        ?.map {
                            Message(
                                it.id,
                                created = it.created,
                                channel = it.channel,
                                author = it.user_name,
                                content = it.content
                            )
                        }
                        ?.filter { it.channel == CHANNEL_MAIN }
                    Timber.tag(LOGTAG).d("observeMessagesAsync(): %s", messages)
                    if (messages != null) callback(messages)
                }

                override fun onConnected() {
                    Timber.tag(LOGTAG).d("onConnected()")
                }

                override fun onTerminated() {
                    Timber.tag(LOGTAG).d("onTerminated()")
                }

                override fun onCompleted() {
                    Timber.tag(LOGTAG).d("onCompleted()")
                }

            }

        subscription.execute(subscriptionCallback)

        return object : Cancelable {
            override fun isCanceled(): Boolean = subscription.isCanceled

            override fun cancel() = subscription.cancel()
        }
    }

    data class Message(
        val id: UUID,
        val created: Date,
        val channel: String,
        val author: String,
        val content: String
    )
}