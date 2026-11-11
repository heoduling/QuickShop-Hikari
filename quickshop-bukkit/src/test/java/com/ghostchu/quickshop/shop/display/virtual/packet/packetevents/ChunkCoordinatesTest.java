package com.ghostchu.quickshop.shop.display.virtual.packet.packetevents;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChunkCoordinatesTest {
  @Test void signedCoordinatesAreReadWithoutConsumingTheBuffer() {
    for(int[] pair : new int[][]{{0, 0}, {-17, 32}, {Integer.MIN_VALUE, Integer.MAX_VALUE}}) {
      PacketSendEvent event = mock(PacketSendEvent.class);
      Object buffer = new Object();
      when(event.getByteBuf()).thenReturn(buffer);
      byte[] bytes = ByteBuffer.allocate(8).putInt(pair[0]).putInt(pair[1]).array();
      try(var helper = mockStatic(ByteBufHelper.class)) {
        helper.when(()->ByteBufHelper.readerIndex(buffer)).thenReturn(7);
        for(int index = 0; index < bytes.length; index++) {
          int offset = index + 7;
          short value = (short)(bytes[index] & 0xff);
          helper.when(()->ByteBufHelper.getUnsignedByte(buffer, offset)).thenReturn(value);
        }
        long coordinates = PacketFactoryv1_21.chunkCoordinates(event);
        assertEquals(pair[0], (int)(coordinates >> 32));
        assertEquals(pair[1], (int)coordinates);
        helper.verify(()->ByteBufHelper.readerIndex(buffer, 7), never());
        helper.verify(()->ByteBufHelper.readInt(buffer), never());
      }
    }
  }

  @Test void existingWrapperCoordinatesTakePrecedenceOverOriginalBytes() {
    PacketSendEvent event = mock(PacketSendEvent.class);
    WrapperPlayServerChunkData wrapper = mock(WrapperPlayServerChunkData.class);
    Column column = mock(Column.class);
    doReturn(wrapper).when(event).getLastUsedWrapper();
    when(wrapper.getColumn()).thenReturn(column);
    when(column.getX()).thenReturn(-125);
    when(column.getZ()).thenReturn(976);
    long coordinates = PacketFactoryv1_21.chunkCoordinates(event);
    assertEquals(-125, (int)(coordinates >> 32));
    assertEquals(976, (int)coordinates);
    verify(event, never()).getByteBuf();
  }
}
