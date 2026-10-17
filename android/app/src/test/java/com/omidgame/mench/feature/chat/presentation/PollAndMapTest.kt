package com.omidgame.mench.feature.chat.presentation

import com.google.common.truth.Truth.assertThat
import com.omidgame.mench.feature.chat.domain.Poll
import com.omidgame.mench.feature.chat.domain.PollOption
import org.junit.Test

class PollAndMapTest {
    private val uuid = "3f2b8c1e-5d4a-4c1b-9e77-0a1b2c3d4e5f"

    private fun poll(multiple: Boolean) = Poll(
        id = "p", conversationId = "c", creatorId = "creator", question = "Q", multiple = multiple, closed = false,
        options = listOf(
            PollOption("o1", "A", listOf("me", "x")),
            PollOption("o2", "B", listOf("x")),
            PollOption("o3", "C", emptyList()),
        ),
    )

    @Test
    fun `poll and location markers round trip and reject tampering`() {
        assertThat(RichBody.encodePoll(uuid)).isEqualTo("poll:v1:$uuid")
        assertThat(RichBody.parse("poll:v1:$uuid")).isEqualTo(RichBody.Poll(uuid))
        assertThat(RichBody.parse("poll:v1:nope")).isEqualTo(RichBody.Plain)
        val loc = RichBody.encodeLocation(35.6892, 51.389)
        assertThat(loc).isEqualTo("loc:v1:35.689200:51.389000")
        assertThat(RichBody.parse(loc)).isEqualTo(RichBody.Location(35.6892, 51.389))
        assertThat(RichBody.encodeLocation(91.0, 0.0)).isNull()
        assertThat(RichBody.encodeLocation(0.0, Double.NaN)).isNull()
        assertThat(RichBody.parse("loc:v1:95:10")).isEqualTo(RichBody.Plain)
        assertThat(RichBody.parse("loc:v1:a:b")).isEqualTo(RichBody.Plain)
    }

    @Test
    fun `single choice taps replace or retract`() {
        val p = poll(false)
        assertThat(p.totalVoters).isEqualTo(2)
        assertThat(p.myChoices("me")).containsExactly("o1")
        assertThat(p.choicesAfterTap("o2", "me")).containsExactly("o2")
        assertThat(p.choicesAfterTap("o1", "me")).isEmpty()
    }

    @Test
    fun `multiple choice taps toggle`() {
        val p = poll(true)
        assertThat(p.choicesAfterTap("o2", "me")).containsExactly("o1", "o2")
        assertThat(p.choicesAfterTap("o1", "me")).isEmpty()
    }

    @Test
    fun `optimistic votes replace only my own`() {
        val updated = poll(false).withMyVotes("me", listOf("o2"))
        assertThat(updated.options.first { it.id == "o1" }.voterIds).containsExactly("x")
        assertThat(updated.options.first { it.id == "o2" }.voterIds).containsExactly("x", "me")
        assertThat(updated.withMyVotes("me", emptyList()).myChoices("me")).isEmpty()
    }

    @Test
    fun `tile math puts Tehran in the right tile and covers the viewport`() {
        val (x, y) = MapTiles.tileCoordinates(35.6892, 51.389, 15)
        assertThat(x.toInt()).isEqualTo(21061)
        assertThat(y.toInt()).isEqualTo(12902)
        val tiles = MapTiles.visibleTiles(35.6892, 51.389, 15, 240f, 150f)
        assertThat(tiles.size).isIn(1..4)
        assertThat(tiles.any { it.offsetXDp <= 120f && it.offsetXDp + 256f >= 120f && it.offsetYDp <= 75f && it.offsetYDp + 256f >= 75f }).isTrue()
    }

    @Test
    fun `tiles wrap at the antimeridian and drop rows beyond the poles`() {
        assertThat(MapTiles.visibleTiles(0.0, 179.9999, 3, 600f, 150f).all { it.x in 0..7 }).isTrue()
        assertThat(MapTiles.visibleTiles(89.0, 0.0, 3, 300f, 600f).all { it.y in 0..7 }).isTrue()
    }

    @Test
    fun `coordinate formatting and links use a dot and fixed decimals`() {
        assertThat(MapTiles.formatCoordinates(35.6892, 51.389)).isEqualTo("35.68920, 51.38900")
        assertThat(MapTiles.geoUri(1.5, -2.25)).isEqualTo("geo:1.500000,-2.250000?q=1.500000,-2.250000")
        assertThat(MapTiles.webLink(1.5, -2.25)).contains("mlat=1.500000&mlon=-2.250000")
    }
}
