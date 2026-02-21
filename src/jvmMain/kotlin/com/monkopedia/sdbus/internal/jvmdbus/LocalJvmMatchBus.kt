package com.monkopedia.sdbus.internal.jvmdbus

import com.monkopedia.sdbus.ActionResource
import com.monkopedia.sdbus.Message
import com.monkopedia.sdbus.MessageHandler
import com.monkopedia.sdbus.Resource
import com.monkopedia.sdbus.signalFromMetadata
import java.util.concurrent.atomic.AtomicLong

internal data class LocalMatchFilter(
    val sender: String? = null,
    val path: String? = null,
    val interfaceName: String? = null,
    val member: String? = null
)

internal object LocalJvmMatchBus {
    private val nextId = AtomicLong(1)
    private val handlers = mutableMapOf<Long, Pair<LocalMatchFilter, MessageHandler>>()

    fun parseMatchFilter(match: String): LocalMatchFilter {
        val values = mutableMapOf<String, String>()
        match.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { token ->
                val parts = token.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim().trim('\'').trim('"')
                    values[key] = value
                }
            }
        return LocalMatchFilter(
            sender = values["sender"],
            path = values["path"],
            interfaceName = values["interface"],
            member = values["member"]
        )
    }

    fun register(match: String, callback: MessageHandler): Resource {
        val id = nextId.getAndIncrement()
        val filter = parseMatchFilter(match)
        synchronized(this) {
            handlers[id] = filter to callback
        }
        return ActionResource {
            synchronized(this) {
                handlers.remove(id)
            }
        }
    }

    fun emit(
        sender: String?,
        path: String,
        interfaceName: String,
        member: String,
        payload: List<Any?>
    ) {
        val message = signalFromMetadata(
            Message.Metadata(
                interfaceName = interfaceName,
                memberName = member,
                sender = sender,
                path = path,
                valid = true,
                empty = payload.isEmpty()
            )
        ).also {
            it.payload.addAll(payload)
        }
        val callbacks = synchronized(this) {
            handlers.values.filter { (filter, _) ->
                (filter.sender == null || filter.sender == sender) &&
                    (filter.path == null || filter.path == path) &&
                    (filter.interfaceName == null || filter.interfaceName == interfaceName) &&
                    (filter.member == null || filter.member == member)
            }.map { it.second }
        }
        callbacks.forEach { callback ->
            JvmCurrentMessageContext.withMessage(message) {
                callback(message)
            }
        }
    }
}
