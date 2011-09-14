/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.yahoo_import;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.Iterator;

/**
 * Class for reading records from a Yahoo! conversation file.
 */
public class YahooConversationReader implements Iterator<RawReply>
{
	protected static final int YM_PACKET_START_CONV = 0;
	protected static final int YM_PACKET_CONV_MESSAGE = 6;
	protected static final int YM_PACKET_CONF_JOIN = 25;
	protected static final int YM_PACKET_CONF_DECLINE = 26;
	protected static final int YM_PACKET_CONF_LEAVE = 27;
	protected static final int YM_PACKET_CONF_MESSAGE = 29;
	
	protected String _localAccount;
	protected String _remoteAccount;
	
	protected ByteBuffer _buffer;
	protected byte[] _stringBuffer = new byte[256];
	
	/**
	 * Constructor.
	 * 
	 * @param conversationFile The conversation file
	 * @param localAccount The name of the local account (used for decrypting
	 *                     replies)
	 * @param remoteAccount The name of the remote account
	 * @param startOffset An offset in the file from which to read records
	 * @param sizeInFile The size of the area to read records from. If this is
	 *                   negative, the entire file is read.
	 */
	public YahooConversationReader(File conversationFile, String localAccount, String remoteAccount,
			int startOffset, int sizeInFile)
	{
		this._localAccount = localAccount;
		this._remoteAccount = remoteAccount;
		
		try
		{
			// Read the entire conversation file in a buffer
			FileChannel inpChan = new FileInputStream(conversationFile).getChannel();
			this._buffer = ByteBuffer.allocate((sizeInFile >= 0) ? sizeInFile : (int)inpChan.size());
			this._buffer.order(ByteOrder.LITTLE_ENDIAN);
			
			inpChan.position(startOffset);
			inpChan.read(this._buffer);
			inpChan.close();
			
			this._buffer.rewind();
		}
		catch (Exception e)
		{
			throw new RuntimeException("Cannot scan conversation '"+conversationFile.toString()+"' for replies:\n"+e.toString());
		}
	}
	
	/**
	 * Checks whether there are any more reply records available.
	 * 
	 * @return True if there are more replies in the file, false
	 *         otherwise.
	 */
	public boolean hasNext()
	{
		return this._buffer.hasRemaining();
	}
	
	/**
	 * Reads the next reply record from the conversation.
	 * 
	 * @return An object representing the read reply, or null if there are
	 *         no more replies
	 */
	public RawReply next()
	{
		try
		{
			if (!this._buffer.hasRemaining()) return null;
			
			// Read binary data
			Date replyDate = new Date(((long)this._buffer.getInt())*1000);
			int type = this._buffer.getInt();
			int direction = this._buffer.getInt();
			String replyText = this._readString(true);
			String extra = this._readString(false);
			
			// Interpret the reply according to its type
			String senderAccount = (direction == 0) ? this._localAccount : this._remoteAccount;
			switch (type)
			{
			case YM_PACKET_START_CONV:
				return new RawReply(RawReply.Type.START_CONV, replyDate, null, replyText, null);
			case YM_PACKET_CONV_MESSAGE:
				return new RawReply(RawReply.Type.REGULAR, replyDate, senderAccount, replyText, null);
			case YM_PACKET_CONF_MESSAGE:
				if (direction != 0) senderAccount = extra;
				return new RawReply(RawReply.Type.REGULAR, replyDate, senderAccount, replyText, null);
			case YM_PACKET_CONF_JOIN:
				return new RawReply(RawReply.Type.CONFERENCE_JOIN, replyDate, null, replyText, extra);
			case YM_PACKET_CONF_LEAVE:
				return new RawReply(RawReply.Type.CONFERENCE_LEAVE, replyDate, null, replyText, extra);
			case YM_PACKET_CONF_DECLINE:
				return new RawReply(RawReply.Type.CONFERENCE_DECLINE, replyDate, null, replyText, null);
			default:
				throw new RuntimeException(String.format("Unsupported message type: 0x%02h", type));
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new RuntimeException("Could not read reply: "+e.getMessage());
		}
	}
	
	/**
	 * This method is just here to fulfill the contract for
	 * Iterator. It throws an UnsupportedOperationException.
	 */
	public void remove()
	{
		throw new UnsupportedOperationException();
	}
	
	/**
	 * Rewinds the reader to the first reply.
	 */
	public void reset()
	{
		this._buffer.rewind();
	}
	
	/**
	 * Returns the current position in the reply buffer.
	 * 
	 * @return The current position, in bytes
	 */
	public int getBufferPosition()
	{
		return (int)this._buffer.position();
	}
	
	/**
	 * Reads a string from the buffer. The string is assumed to
	 * be preceded by a 4-byte integer indicating the number of
	 * bytes in the string.
	 * 
	 * @param decyrypt If this parameter is true, the string will
	 *                 be decrypted by XOR'ing with the local
	 *                 account name
	 * @return The read string
	 */
	protected String _readString(boolean decrypt)
	{
		int strLen = this._buffer.getInt();
		
		while (this._stringBuffer.length < strLen)
			this._stringBuffer = new byte[this._stringBuffer.length*2];
		this._buffer.get(this._stringBuffer, 0, strLen);
		
		if (decrypt) for (int i=0; i<strLen; i++) this._stringBuffer[i] ^= this._localAccount.codePointAt(i % this._localAccount.length());
		
		return new String(this._stringBuffer, 0, strLen, Charset.forName("UTF-8"));
	}
}
