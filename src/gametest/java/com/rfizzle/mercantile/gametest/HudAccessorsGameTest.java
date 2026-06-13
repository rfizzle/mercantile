package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.api.MercantileAPI;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The HUD coordination accessors (Concord HUD-STANDARD §6) must be safe to
 * call unconditionally from common code on either side. Gametests run on a
 * dedicated server, so this exercises the server-side sentinel path:
 * false/0, no classloading of client-only code, no crash.
 */
public class HudAccessorsGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void hudAccessorsReturnSentinelsServerSide(GameTestHelper helper) {
        helper.assertFalse(MercantileAPI.isHudVisible(),
                "isHudVisible() must return the false sentinel on a dedicated server");
        helper.assertTrue(MercantileAPI.getHudHeight() == 0,
                "getHudHeight() must return the 0 sentinel on a dedicated server, got "
                        + MercantileAPI.getHudHeight());
        helper.succeed();
    }
}
