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

/// A player-shaped test entity for the server GameTests.
///
/// <p>`GameTestHelper#makeMockServerPlayerInLevel` is `@Deprecated(forRemoval = true)` in Minecraft
/// 26.3, and the conventions' strict quality turns that warning into a compile error, so this is
/// the same body the vanilla helper still runs: a `ServerPlayer` over an embedded connection whose
/// game mode is creative.
public final class MockPlayers {
    private MockPlayers() {}

    public static ServerPlayer spawn(GameTestHelper context) {
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(
                        new GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        ServerPlayer player =
                new ServerPlayer(
                        context.getLevel().getServer(),
                        context.getLevel(),
                        cookie.gameProfile(),
                        cookie.clientInformation()) {
                    @Override
                    public GameType gameMode() {
                        return GameType.CREATIVE;
                    }
                };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        context.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }
}
