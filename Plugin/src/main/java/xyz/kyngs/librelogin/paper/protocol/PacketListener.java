/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper.protocol;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import xyz.kyngs.librelogin.common.networking.LibreLoginMessenger;
import xyz.kyngs.librelogin.paper.PaperListeners;

public class PacketListener extends PacketListenerAbstract {
    private final PaperListeners delegate;

    public PacketListener(PaperListeners delegate) {
        super(PacketListenerPriority.HIGHEST);
        this.delegate = delegate;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.isCancelled()) return;

        if (event.getPacketType() == PacketType.Play.Client.PLUGIN_MESSAGE) {
            var wrapper = new WrapperPlayClientPluginMessage(event);
            if (LibreLoginMessenger.isAuthChannel(wrapper.getChannelName())) {
                delegate.onAuthMessage(event, wrapper.getData());
            }
            return;
        }

        if (event.getPacketType() != PacketType.Login.Client.LOGIN_START && event.getPacketType() != PacketType.Login.Client.ENCRYPTION_RESPONSE)
            return;

        delegate.onPacketReceive(event);
    }
}
