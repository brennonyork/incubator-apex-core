/*
 *  Copyright (c) 2012 Malhar, Inc.
 *  All Rights Reserved.
 */
package com.malhartech.bufferserver;

import com.google.protobuf.InvalidProtocolBufferException;
import com.malhartech.bufferserver.Buffer.Message;
import com.malhartech.bufferserver.Buffer.Message.MessageType;
import com.malhartech.bufferserver.util.SerializedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Chetan Narsude <chetan@malhar-inc.com>
 */
public class ProtobufDataInspector implements DataIntrospector
{
  private static final Logger logger = LoggerFactory.getLogger(ProtobufDataInspector.class);
  int previousOffset = -1;
  Message previousMessage;

  /**
   *
   * @param data
   */
  private void readyMessage(SerializedData data)
  {
    if (data.offset != previousOffset) {
      try {
        int size = data.size - data.dataOffset + data.offset;
        if (size < BasicDataMinLength) {
          logger.debug("since the data is smaller than BasicDataMinLength, assuming it's missing");
          previousMessage = null;
        }
        else {
          previousMessage = Message.newBuilder().mergeFrom(data.bytes, data.dataOffset, size).build();
        }
      }
      catch (InvalidProtocolBufferException ipbe) {
        logger.debug(ipbe.getLocalizedMessage());
        previousMessage = null;
      }

      previousOffset = data.offset;
    }
  }

  /**
   *
   * @param data
   * @return MessageType
   */
  @Override
  public final MessageType getType(SerializedData data)
  {
    readyMessage(data);
    return previousMessage == null ? Message.MessageType.NO_MESSAGE : previousMessage.getType();
  }

  /**
   *
   * @param data
   * @return long
   */
  @Override
  public final int getWindowId(SerializedData data)
  {
    readyMessage(data);
    return previousMessage instanceof Message ? previousMessage.getWindowId() : 0;
  }

  /**
   *
   * @param data
   * @return Message
   */
  @Override
  public final Message getData(SerializedData data)
  {
    readyMessage(data);
    return previousMessage;
  }

  /**
   *
   * @param data
   */
  @Override
  public void wipeData(SerializedData data)
  {
    if (data.size + data.offset - data.dataOffset < BasicDataMinLength) {
      logger.debug("we do not need to wipe the SerializedData since it's smaller than min length");
      /* Arrays.fill(data.bytes, data.dataOffset, data.size + data.offset, (byte) 0); */
    }
    else {
      System.arraycopy(BasicData, 0, data.bytes, data.dataOffset, BasicDataMinLength);
    }
  }
  /**
   * Here is a hope that Protobuf implementation is not very different than what small common sense would dictate.
   */
  private static final int BasicDataMinLength;
  private static final int BasicDataMaxLength;
  private static final byte[] BasicData;

  static {
    Message.Builder db = Message.newBuilder();
    db.setType(MessageType.NO_MESSAGE);
    db.setWindowId(0);
    Message basic = db.build();
    BasicData = basic.toByteArray();
    BasicDataMinLength = basic.getSerializedSize();
    if (BasicData.length != BasicDataMinLength) {
      logger.debug("BasicDataMinLength({}) != BasicData.length({})", BasicDataMinLength, BasicData.length);
    }

    db = Message.newBuilder();
    db.setType(MessageType.NO_MESSAGE); // i may need to change this if protobuf is compacting to smaller than 1 byte
    db.setWindowId(Integer.MAX_VALUE);

    BasicDataMaxLength = db.build().getSerializedSize();
  }
}
