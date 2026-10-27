package com.dwinovo.numen.client.consent;

import com.dwinovo.numen.network.payload.ConsentRequestPayload;
import com.dwinovo.numen.pathing.body.BodyView;
import com.dwinovo.numen.permission.ConsentDesk;
import com.dwinovo.numen.permission.ConsentRequest;
import com.dwinovo.numen.permission.Rule;

import net.minecraft.client.Minecraft;

import java.util.List;

/** 客户端假人的征询线：请求直接进本地对话流，答复也回到本地登记处，不走网络。 */
public final class ClientConsentLine implements ConsentDesk.Line {

    private final BodyView body;

    public ClientConsentLine(BodyView body) {
        this.body = body;
    }

    @Override
    public long gameTime() {
        var level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime();
    }

    @Override
    public boolean ownerPresent() {
        return Minecraft.getInstance().player != null;
    }

    @Override
    public void show(ConsentRequest request) {
        ConsentCards.accept(ConsentRequestPayload.of(request));
    }

    @Override
    public void clear(ConsentDesk.Withdrawal why) {
        ConsentCards.accept(ConsentRequestPayload.none(body.getUUID(), why));
    }

    @Override
    public void remember(List<Rule> allow) {
        // 客户端假人没有主人的 allow 表，记住的规则不落盘
    }
}
