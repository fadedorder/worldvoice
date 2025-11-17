package org.faded.worldvoice.ai;

import java.util.Optional;

public final class YggdrasilHooks {
    private static volatile YggdrasilService service;

    private YggdrasilHooks() {}

    public static void bind(YggdrasilService svc) {
        service = svc;
    }

    public static Optional<YggdrasilService> instance() {
        return Optional.ofNullable(service);
    }
}
