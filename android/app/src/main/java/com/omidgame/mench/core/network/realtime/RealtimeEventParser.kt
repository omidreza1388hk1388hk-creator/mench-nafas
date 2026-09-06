@file:OptIn(ExperimentalStdlibApi::class)

package com.omidgame.mench.core.network.realtime

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import javax.inject.Inject
import javax.inject.Singleton

@JsonClass(generateAdapter = true)
internal data class TypeEnvelope(val type: String)

@JsonClass(generateAdapter = true)
internal data class TypingEventWire(val type: String, val conversationId: String)

@JsonClass(generateAdapter = true)
internal data class CallSignalOfferAnswerWire(val type: String, val callId: String, val sdp: String)

@JsonClass(generateAdapter = true)
internal data class CallSignalIceCandidateWire(
    val type: String,
    val callId: String,
    val candidate: String,
    val sdpMid: String?,
    val sdpMLineIndex: Int?,
)

@JsonClass(generateAdapter = true)
internal data class CallSignalHangupWire(val type: String, val callId: String)

/**
 * Parses inbound WS frames in two passes: first read only the `type`
 * discriminator, then parse the full frame with the adapter for the
 * matching concrete class. Deliberately avoids Moshi's
 * PolymorphicJsonAdapterFactory — that requires the JSON to carry an
 * explicit "label" matching a registered subtype string, wired through
 * factory configuration that's easy to get subtly wrong without a
 * compiler to catch it. This two-pass approach uses only Moshi's ordinary,
 * well-understood single-class adapters: each is either right or fails
 * loudly, not silently mismatched.
 */
@Singleton
class RealtimeEventParser @Inject constructor(private val moshi: Moshi) {

    fun parse(json: String): ServerToClientEvent? {
        val envelope = runCatching { moshi.adapter<TypeEnvelope>().fromJson(json) }.getOrNull()
            ?: return null

        return when (envelope.type) {
            "message.created" ->
                runCatching { moshi.adapter<ServerToClientEvent.MessageCreated>().fromJson(json) }.getOrNull()
            "message.updated" ->
                runCatching { moshi.adapter<ServerToClientEvent.MessageUpdated>().fromJson(json) }.getOrNull()
            "message.deleted" ->
                runCatching { moshi.adapter<ServerToClientEvent.MessageDeleted>().fromJson(json) }.getOrNull()
            "reaction.updated" ->
                runCatching { moshi.adapter<ServerToClientEvent.ReactionUpdated>().fromJson(json) }.getOrNull()
            "message.read" ->
                runCatching { moshi.adapter<ServerToClientEvent.MessageRead>().fromJson(json) }.getOrNull()
            "typing.started" ->
                runCatching { moshi.adapter<ServerToClientEvent.TypingStarted>().fromJson(json) }.getOrNull()
            "typing.stopped" ->
                runCatching { moshi.adapter<ServerToClientEvent.TypingStopped>().fromJson(json) }.getOrNull()
            "conversation.added" ->
                runCatching { moshi.adapter<ServerToClientEvent.ConversationAdded>().fromJson(json) }.getOrNull()
            "group.member_added" ->
                runCatching { moshi.adapter<ServerToClientEvent.GroupMemberAdded>().fromJson(json) }.getOrNull()
            "group.member_removed" ->
                runCatching { moshi.adapter<ServerToClientEvent.GroupMemberRemoved>().fromJson(json) }.getOrNull()
            "group.member_left" ->
                runCatching { moshi.adapter<ServerToClientEvent.GroupMemberLeft>().fromJson(json) }.getOrNull()
            "group.renamed" ->
                runCatching { moshi.adapter<ServerToClientEvent.GroupRenamed>().fromJson(json) }.getOrNull()
            "call.incoming" ->
                runCatching { moshi.adapter<ServerToClientEvent.CallIncoming>().fromJson(json) }.getOrNull()
            "call.accepted" ->
                runCatching { moshi.adapter<ServerToClientEvent.CallAccepted>().fromJson(json) }.getOrNull()
            "call.declined" ->
                runCatching { moshi.adapter<ServerToClientEvent.CallDeclined>().fromJson(json) }.getOrNull()
            "call.ended" ->
                runCatching { moshi.adapter<ServerToClientEvent.CallEnded>().fromJson(json) }.getOrNull()
            "call.offer" ->
                runCatching { moshi.adapter<ServerToClientEvent.CallOffer>().fromJson(json) }.getOrNull()
            "call.answer" ->
                runCatching { moshi.adapter<ServerToClientEvent.CallAnswer>().fromJson(json) }.getOrNull()
            "call.ice-candidate" ->
                runCatching { moshi.adapter<ServerToClientEvent.CallIceCandidate>().fromJson(json) }.getOrNull()
            "call.hangup" ->
                runCatching { moshi.adapter<ServerToClientEvent.CallHangup>().fromJson(json) }.getOrNull()
            else -> null
        }
    }

    /**
     * Uses a concrete, non-generic @JsonClass adapter per shape rather than
     * a generic Map<String, Any?> adapter — moshi-kotlin's reified
     * `adapter<T>()` extension is only unambiguous for concrete types;
     * relying on it for a parameterized generic type risks a subtly wrong
     * adapter (e.g. losing type info to erasure) that no compiler here can
     * catch. A dedicated wire class per outgoing shape has no such risk.
     */
    fun serialize(event: ClientToServerEvent): String = when (event) {
        is ClientToServerEvent.TypingStarted ->
            moshi.adapter<TypingEventWire>().toJson(TypingEventWire(event.type, event.conversationId))
        is ClientToServerEvent.TypingStopped ->
            moshi.adapter<TypingEventWire>().toJson(TypingEventWire(event.type, event.conversationId))
        is ClientToServerEvent.CallOffer ->
            moshi.adapter<CallSignalOfferAnswerWire>()
                .toJson(CallSignalOfferAnswerWire(event.type, event.callId, event.sdp))
        is ClientToServerEvent.CallAnswer ->
            moshi.adapter<CallSignalOfferAnswerWire>()
                .toJson(CallSignalOfferAnswerWire(event.type, event.callId, event.sdp))
        is ClientToServerEvent.CallIceCandidate ->
            moshi.adapter<CallSignalIceCandidateWire>().toJson(
                CallSignalIceCandidateWire(event.type, event.callId, event.candidate, event.sdpMid, event.sdpMLineIndex),
            )
        is ClientToServerEvent.CallHangup ->
            moshi.adapter<CallSignalHangupWire>().toJson(CallSignalHangupWire(event.type, event.callId))
    }
}
