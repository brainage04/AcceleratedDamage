package io.github.brainage04.accelerateddamage.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;

/// A player joined to the test server through `PlayerList.placeNewPlayer`, so it is in the level
/// and the player list. `GameTestHelper#makeMockServerPlayerInLevel` is `@Deprecated(forRemoval =
/// true)` in 26.3 (`GameTestHelper.java:395`), which the conventions' deprecated-API check fails
/// the build on; this is the same join it performs.
final class TestPlayers {
    private TestPlayers() {}

    static ServerPlayer join(GameTestHelper helper) {
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(
                        new GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        ServerPlayer player =
                new ServerPlayer(
                        helper.getLevel().getServer(),
                        helper.getLevel(),
                        cookie.gameProfile(),
                        cookie.clientInformation()) {
                    @Override
                    public GameType gameMode() {
                        return GameType.CREATIVE;
                    }
                };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }
}
