package com.rfizzle.mercantile.client;

import com.rfizzle.mercantile.client.network.ClientNetworkHandler;
import net.fabricmc.api.ClientModInitializer;

public class MercantileClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientNetworkHandler.init();
    }
}
