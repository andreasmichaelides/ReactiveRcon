package com.westplay.reactivercon;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

/**
 * Created by westplay on 26/02/17.
 */

public class RconPacket {

    //Rcon packet types
    public static final int SERVERDATA_AUTH = 3;
    public static final int SERVERDATA_EXECCOMMAND = 2;
    public static final int SERVERDATA_AUTH_RESPONSE = 2;
    public static final int SERVERDATA_RESPONSE_VALUE = 0;

    private static final int PACKET_LENGTH_BYTE_SIZE = 4;
    private static final int REQUEST_ID_BYTE_SIZE = 4;
    private static final int TYPE_BYTE_SIZE = 4;
    private static final int nullByteSize = 1;
    private static final int RCON_PACKET_NULL_BYTE_SIZE = 2 * nullByteSize;

    private int requestId;
    private int type;
    private byte[] body;
    private Charset charset = Charset.forName("UTF-8");

    public RconPacket(int requestId, int type, String body) {
        this.requestId = requestId;
        this.type = type;
        this.body = body.getBytes();
    }

    public RconPacket(int requestId, int type, byte[] body) {
        this.requestId = requestId;
        this.type = type;
        this.body = body;
    }

    public static RconPacket createFromStream(InputStream inputStream) throws IOException {
        int packetHeaderSize = PACKET_LENGTH_BYTE_SIZE + REQUEST_ID_BYTE_SIZE + TYPE_BYTE_SIZE;
        byte[] header = new byte[packetHeaderSize];

        //Read the packet header
        inputStream.read(header);
        // Use a bytebuffer in little endian to read the first 3 ints
        ByteBuffer buffer = ByteBuffer.wrap(header);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        int length = buffer.getInt();
        int requestId = buffer.getInt();
        int type = buffer.getInt();

        int packetBodyLength = length - REQUEST_ID_BYTE_SIZE - TYPE_BYTE_SIZE - RCON_PACKET_NULL_BYTE_SIZE;
        packetBodyLength = packetBodyLength < 0 ? 0 : packetBodyLength;
        byte[] packetBody = new byte[packetBodyLength];

        DataInputStream dataInputStream = new DataInputStream(inputStream);

        //Read the full packet body
        dataInputStream.readFully(packetBody);

        //Read the null bytes
        dataInputStream.read(new byte[RCON_PACKET_NULL_BYTE_SIZE]);

        return new RconPacket(requestId, type, packetBody);
    }

    public byte[] getAsByteArray() {
        int packetBodyLength = getPacketBodyLength(body);
        int packetLength = getPacketLength(packetBodyLength);
        ByteBuffer byteBuffer = ByteBuffer.allocate(packetLength);

        //Header data
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.putInt(packetBodyLength);
        byteBuffer.putInt(requestId);
        byteBuffer.putInt(type);

        //Body data
        byteBuffer.put(body);

        //Put the two null bytes
        byteBuffer.put((byte) 0);
        byteBuffer.put((byte) 0);

        return byteBuffer.array();
    }

    private int getPacketLength(int packetBodyLength) {
        return PACKET_LENGTH_BYTE_SIZE + packetBodyLength;
    }

    private int getPacketBodyLength(byte[] payload) {
        return REQUEST_ID_BYTE_SIZE + TYPE_BYTE_SIZE + RCON_PACKET_NULL_BYTE_SIZE + payload.length;
    }

    public int getRequestId() {
        return requestId;
    }

    public int getType() {
        return type;
    }

    public byte[] getBody() {
        return body;
    }

    public String getBodyAsString() {
        return new String(body, charset);
    }

    @Override
    public String toString() {
        return String.format("ID:\t%1$d, Type\t%2$d, Body\t%3$s", requestId, type, getBodyAsString());
    }
}
