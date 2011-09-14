/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.msn_import;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.XMLEvent;

/**
 * Class for reading replies in a MSN conversation file.
 */
public class MsnConversationReader implements Iterator<RawReply>
{
	protected File _convFile;
	protected int _sessionId;
	
	protected XMLEventReader _xmlReader;
	protected RawReply _bufferedReply;
	
	/**
	 * Constructor.
	 * 
	 * @param conversationFile The conversation file
	 * @param sessionId The session id of the conversation to read.
	 *                  If negative, replies belonging to all conversations
	 *                  will be read.
	 */
	public MsnConversationReader(File conversationFile, int sessionId)
	{	
		this._convFile = conversationFile;
		this._sessionId = sessionId;
		
		this.reset();
	}
	
	@Override
	public boolean hasNext()
	{
		if (this._bufferedReply == null) this._bufferedReply = this._readReply();
		
		return (this._bufferedReply != null);
	}

	@Override
	public RawReply next()
	{
		if (this._bufferedReply == null) this._bufferedReply = this._readReply();
		
		RawReply reply = this._bufferedReply;
		this._bufferedReply = null;
		
		return reply;
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
		try
		{
			this._xmlReader = XMLInputFactory.newInstance().createXMLEventReader(
					new BufferedInputStream(new FileInputStream(this._convFile)));
			this._bufferedReply = null;
		}
		catch (Exception e)
		{
			throw new RuntimeException("Cannot scan conversation for replies:\n"+e.getMessage());
		}
	}
	
	/**
	 * Reads a reply from the underlying XML event reader.
	 * 
	 * @return An object containing the raw reply, or null if there
	 *         are no more replies
	 */
	protected RawReply _readReply()
	{
		final SimpleDateFormat xslDateFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
		
		try
		{
			boolean inMessage = false;
			boolean inFrom = false;
			boolean inTo = false;
			boolean inText = false;
			
			String msgType = null;
			int msgSessionId = 0;
			Date msgDate = null;
			String msgFrom = null;
			String msgTo = null;
			StringBuilder msgText = null;
			
			while (this._xmlReader.hasNext())
			{
				XMLEvent evt = this._xmlReader.nextEvent();
				
				if (evt.isStartElement())
				{
					String tagName = evt.asStartElement().getName().getLocalPart();
					
					if (tagName.equals("Message") || tagName.equals("Invitation") ||
							tagName.equals("InvitationResponse") || tagName.equals("Join") ||
							tagName.equals("Leave"))
					{
						// A reply is starting, fetch type, session ID and date
						msgSessionId = Integer.parseInt(evt.asStartElement().getAttributeByName(new QName("SessionID")).getValue());
						if ((this._sessionId < 0) || (msgSessionId == this._sessionId))
						{
							inMessage = true;
							msgType = tagName;
							msgFrom = null;
							msgTo = null;
							msgText = new StringBuilder();
							
							String dateStr = evt.asStartElement().getAttributeByName(new QName("DateTime")).getValue();
							try
							{
								msgDate = xslDateFmt.parse(dateStr);
							}
							catch (Exception e)
							{
								throw new RuntimeException("Invalid date format: "+dateStr);
							}
						}
					}
					else if (inMessage && tagName.equals("From"))
					{
						inFrom = true;
					}
					else if (inMessage && tagName.equals("To"))
					{
						inTo = true;
					}
					else if (inMessage && tagName.equals("Text"))
					{
						inText = true;
					}
					else if ((inFrom || inTo) && tagName.equals("User"))
					{
						String alias = evt.asStartElement().getAttributeByName(new QName("FriendlyName")).getValue();
						
						if (inFrom) msgFrom = alias; else msgTo = alias;
					}
				}
				
				if (evt.isEndElement() && inMessage)
				{
					String tagName = evt.asEndElement().getName().getLocalPart();
					
					if (tagName.equals(msgType))
					{
						// Message ends
						if (msgFrom == null) throw new RuntimeException("Sender is unspecified");
						
						RawReply.Type type = RawReply.Type.REGULAR;
						if (msgType.equals("Invitation") || msgType.equals("InvitationResponse")) type = RawReply.Type.SYSTEM;
						if (msgType.equals("Join")) type = RawReply.Type.CONFERENCE_JOIN;
						if (msgType.equals("Leave")) type = RawReply.Type.CONFERENCE_LEAVE;
						
						return new RawReply(type, msgDate, msgFrom, msgTo, msgText.toString(), msgSessionId);
					}
					else if (tagName.equals("From"))
					{
						inFrom = false;
					}
					else if (tagName.equals("To"))
					{
						inTo = false;
					}
					else if (tagName.equals("Text"))
					{
						inText = false;
					}
				}
				
				if (evt.isCharacters() && inText)
				{
					msgText.append(evt.asCharacters().getData());
				}
			}
		}
		catch (Exception e)
		{
			throw new RuntimeException("Error reading replies:\n"+e.getMessage());
		}
		
		return null;
	}
}