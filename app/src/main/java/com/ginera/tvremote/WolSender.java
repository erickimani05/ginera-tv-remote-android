package com.ginera.tvremote;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public final class WolSender {
    private WolSender() {}

    public static void wake(String macAddress) throws Exception {
        String clean = macAddress.replace(":", "").replace("-", "");
        if (clean.length() != 12) throw new IllegalArgumentException("Invalid MAC address");
        byte[] mac = new byte[6];
        for (int i = 0; i < 6; i++) {
            mac[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
        }
        byte[] packet = new byte[102];
        for (int i = 0; i < 6; i++) packet[i] = (byte) 0xff;
        for (int i = 6; i < packet.length; i++) packet[i] = mac[(i - 6) % 6];

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            DatagramPacket datagram = new DatagramPacket(
                    packet, packet.length, InetAddress.getByName("255.255.255.255"), 9);
            socket.send(datagram);
        }
    }
}
